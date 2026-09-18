# Replica 执行时 Compaction 与批量 DML 设计

## 1. 背景与目标

复制链路在热点行场景下会形成较长的事务依赖链。逐事务、逐行提交会放大数据库往返、SQL 解析和连接开销。本变更在 `TransactionParallelApplierV3` 中引入执行时 compaction，并将同一事务中的同表同类 DML 合并执行，以提升热点更新和小事务场景的吞吐。

设计目标：

- 保留 PK/UK 冲突事务的依赖顺序；无冲突事务继续并行执行。
- 在有界窗口内消除被后续事件完全覆盖的行事件。
- 合并 INSERT、DELETE、UPDATE，降低 SQL round-trip。
- DDL 作为全局屏障串行执行，不与前后 DML 交叉。
- 支持运行时关闭 compaction，异常时尽量回退到原始执行路径。

非目标：

- 不跨一个 `tranApply` 输入批次做 compaction。
- 不合并发生 identity key（PK + 分区键）变化的 UPDATE 链。
- 不证明或维护跨表外键依赖；当前批量器假设复制目标不存在需要保序的外键关系。

## 2. 总体流程

```text
tranApply
  ├─ 按 DDL 切分 DML 段；DDL 串行执行
  └─ 对每个 DML 段
       ├─ 提取 PK/UK，构建事务 DAG
       ├─ 将入度为 0 的节点送入 worker pool
       ├─ 从 root 构造 upstream-closed 的有界 window
       ├─ 识别重复访问的 identity key
       ├─ supersede 状态机消除旧事件
       ├─ 合成事务并做批量 DML
       └─ consumed 节点只释放下游依赖，不重复访问数据库
```

## 3. 依赖图

每个输入事务对应一个 `TxNode`。`extractAffectedKeys` 提取 before/after PK 与非 NULL UK；同一个 key 的前一次访问节点指向本次访问节点。这样同一唯一资源上的操作串行，而不同资源可并行。

无 PK 且无 UK 的表没有可用于 DAG 冲突检测的稳定资源键。只要一个 DML 批次涉及此类表，整个批次直接回退到 `TransactionApplier` 串行路径，同时绕开单事务 batch，保持事件原始顺序。

资源 key 使用结构化值与深值判等；`BINARY/VARBINARY` 在 extractor 中表示为 `byte[]`，必须按数组内容而不是 Java 对象引用建立依赖。CASE WHEN 的 last-writer-wins 去重复用同一结构化 key，避免字符串拼接和数组 `toString()` 造成 identity 误判。

### 3.1 Key 语义兼容性与串行回退

DAG 冲突检测要求 Java key 尽量覆盖数据库 key 的等价关系：数据库认为相同的 key，Java 侧如果认为不同，就可能漏掉依赖边并重排原本冲突的事务。`DbMetaManager` 在加载表元数据时检查 PK、UK、拆分键，以及无 PK 表实际用于行定位的 identity columns；只要 key 类型既没有精确表示，也没有受支持的 DAG 归一化策略，`TableInfo.parallelApplyKeyUnsupported` 就会标记为 true。一个 DML 批次涉及任意此类表时，整个批次回退到 `TransactionApplier` 串行路径，不进入 DAG、compaction 或 CASE WHEN batch。

当前采用精确类型白名单，并对普通字符 key 做有限的工程化归一化：

| 分类 | 当前处理 | 原因 |
|---|---|---|
| `TINYINT/SMALLINT/INT/BIGINT`、`DECIMAL/NUMERIC` | 允许并行 | extractor 表示与数据库定点值语义稳定对应 |
| `BIT`、`BINARY/VARBINARY` | 允许并行 | 使用数组深值判等和深值 hash，按二进制内容建立依赖 |
| `DATE/TIME/DATETIME/TIMESTAMP`、`YEAR` | 允许并行 | extractor 对同一存储值生成稳定标量 |
| `ENUM/SET` | 允许并行 | extractor 分别使用枚举序号和 bitmask |
| 完整列 `CHAR/VARCHAR` | 允许并行 | 仅为 DAG 派生 key 去除尾部 ASCII 空格并使用 `Locale.ROOT` 转小写，覆盖常见大小写不敏感和 PAD SPACE 场景 |
| `FLOAT/DOUBLE` | 串行 | 字符串化后的值不能完整覆盖数据库对 signed zero/特殊值的比较语义 |
| `BLOB/TEXT`、`LONGVARBINARY`、`JSON`、`VECTOR/GEOMETRY` 等 | 串行 | LOB/复杂类型缺少可证明的精确 key 表示，且通常依赖索引前缀或生成列 |
| generated key column、表达式唯一索引 | 串行 | binlog before/after image 或表达式结果不能按普通列稳定提取 |

类型检查同时使用 JDBC type code 和数据库 `typeName`。MySQL JDBC 元数据读取路径会把 `POINT/GEOMETRY` 等空间类型从 `Types.OTHER` 映射成 `Types.BINARY`，兼容性判定必须先按 `typeName` 排除空间/复杂类型，不能因此误过二进制精确类型白名单。

任何前缀唯一索引都串行处理，包括 `UNIQUE KEY uk_name(name(16))`。本地索引通过 `SHOW INDEXES.Sub_part` 识别；全局索引元数据若返回 `name(16)` 也会解析并标记。不能只比较完整列值，因为数据库只比较索引前缀：两个完整值不同但索引前缀相同的事件仍属于同一唯一资源。

字符归一化只用于 `extractAffectedKeys` 构造 DAG resource key，不会回写 before/after image，也不会改变 UPDATE/INSERT 的 SET 参数、WHERE 参数或最终落库值。compaction 的 `cachedPkKeys`、`eventsByPk` 和 `isIdentityOrUkChanged` 继续使用原始精确值；否则在大小写敏感 collation 下把 `A`、`a` 当作同一行 supersede，反而可能造成数据丢失。DAG 中归一化过度只会多建依赖边，而不会删除或改写事件。

该有限归一化不试图完整模拟 MySQL collation，重音、Unicode 宽度和语言特有等价规则仍属于残余风险。当前取舍面向 UK 主要由中文、数字和 ASCII 业务标识组成的常见场景；不在 Java 侧持续扩展完整 collation 实现。

如果兼容配置关闭了 UK 元数据发现，V3 同样按不安全表处理。即使当前已知 PK 类型安全，在不知道表上实际 UK 的情况下也不能证明 DAG 依赖完整；因此不能把“忽略 UK”解释成“UK 不参与冲突”。

该判定按表生效，但回退按输入 DML 批次生效。这样可以保证一个事务同时涉及安全表和不安全表时仍保持完整的原始事务顺序。

### 3.2 性能影响

字符归一化位于 DAG key 提取的内存路径，不增加数据库交互、锁或网络调用。数值、时间和二进制 key 通过 `instanceof String` 快速返回，不创建归一化字符串；只有 `CHAR/VARCHAR` key 扫描尾部空格并执行小写转换，成本与 key 字符串长度线性相关。

每次 `buildDependencyGraph` 使用按 `TableInfo` identity 隔离的字符列集合缓存：每张表只扫描一次列元数据，后续每个 PK/UK 分量以 `HashSet` O(1) 判断是否需要归一化。设本批次涉及表的总列数为 C、PK/UK value 数为 K、需要归一化的字符总长度为 L，新增工作量为 O(C + K + L)，缓存生命周期只覆盖本次 DAG build，不会跨 DDL 持有旧元数据。

已有 `TableKey`、key map 和 DAG 节点分配不变；新增常驻对象仅为一次 build 内每张表一个字符列集合，派生字符串只存在于 DAG key。正常复制的 JDBC 执行、网络和事务提交通常远重于这部分 CPU，因此普通数字主键以及短字符 UK 的预期影响很小。可开启 `rpl_compaction_log_enabled` 观察 `[Graph] Build DAG completed, cost=...`，并在相同流量下比较 DAG build cost 与总 apply cost。

需要区分“归一化开销”和“串行回退开销”：前者通常很小；后者对命中前缀 UK、表达式/generated key 或不支持类型的 DML 批次会失去 V3 并行度，吞吐影响可能明显。该影响只发生在实际涉及不安全表的批次，普通完整列 `CHAR/VARCHAR` 已通过 DAG-only 归一化保留并行能力。

串行回退的 WARN 按 `table + reason` 做 10 分钟限频，缓存最多 4096 项，避免长期涉及同一不安全表时形成逐 batch 日志压力，同时保留周期性可观测性。

节点同时缓存：

- `cachedPkKeys`：实际使用 `TableInfo.getKeyList()`，即 PK + dbShardKey + tbShardKey，避免跨分片同 PK 被错误压缩。
- `eventsByPk`：identity key 到事件列表的索引。
- `cachedEvents`：原始事件对象，保证事务持久化开启时 `IdentityHashMap` 仍能正确识别被淘汰事件。

## 4. Compaction window

window 从一个 ready root 做 BFS 扩展。只有当候选节点的全部 upstream 都已进入 window 时，候选节点才可加入，因此 window 对上游封闭，不会越过外部依赖。当前边界为最多 64 个事务、200 个事件。

`rpl_compaction_enabled` 在 `TransactionParallelApplierV3` 构造时读取并固化，运行中配置变更需要重启 applier 进程后生效。关闭 compaction 后不做 window 压缩，但仍可由 `rpl_batch_enabled` 独立控制单事务内批量 DML。开启 compaction 时，即使 `tranApply` 只收到单个事务，也会进入 DAG/window 路径，以识别同一事务内同一 identity key 的连续事件。

## 5. Supersede 状态机

状态按 identity key 独立维护。identity key 使用 `TableInfo.getKeyList()`，即 PK + 分区键；DAG 依赖仍按 PK + UK 提取，保证唯一资源访问顺序。

| 事件链 | 处理结果 |
|---|---|
| INSERT → UPDATE... | 保留 INSERT 和最后一个 UPDATE |
| UPDATE → UPDATE... | 保留最后一个 UPDATE，并强制使用完整 after-image |
| INSERT → ... → DELETE | 删除 INSERT/中间 UPDATE，保留 DELETE |
| UPDATE → ... → DELETE | 删除旧 UPDATE，保留 DELETE |
| DELETE → INSERT | DELETE 不建链，后续 INSERT 新建链，因此保留 DELETE + INSERT，不压缩为单 INSERT/REPLACE |
| identity key 或 UK 变化 | 中断当前链，不做跨 key 合并 |

发生 supersede 后，幸存 UPDATE 设置 `forceAllColumns`，以避免增量列集合丢失被淘汰事件已经写入的值；生成 SET 时排除未变化的 identity columns，避免无意义的 relocate 路径。UK 变化同样会打断 compaction 链，因为它可能承担唯一键让位语义，不能被 per-PK 状态机删除，也不能进入无序的 CASE WHEN 批量 UPDATE。

## 6. 批量 DML

合并事务在一个数据库事务内执行。每张表维护一个 pending batch；同表动作变化或达到 `rpl_inc_dml_batch_size` 时 flush。

批量执行由 `rpl_batch_enabled` 独立控制，开关同样在 applier 构造时读取并固化。关闭后，无论事务是否由 compaction 合成，都会回退到原有的 `TransactionApplier` 执行路径；因此可以独立组合 compaction 与 batch 两项能力。

- INSERT：多 values INSERT。`TransactionParallelApplierV3` 构造期已禁止 `DIRECT_OVERWRITE`，因此不再按 conflict strategy 切换为 REPLACE，`insertMode` 恒为 `INSERT_MODE_SIMPLE_INSERT_OR_DELETE`。
- DELETE：按 identity 条件合成批量 DELETE。
- UPDATE：identity/UK 均不变的连续段生成 `CASE WHEN` UPDATE；identity 或 UK 变化的行单独 UPDATE，保留唯一键迁移和分片迁移顺序。
- key/identity columns 含 VECTOR/GEOMETRY 等不可精确比较类型时，DML 批次在建图前回退到 `TransactionApplier` 串行执行。
- 批量 SQL 遇到 duplicate key 时逐条重试；死锁、超时、网络异常直接抛出，避免在事务状态不确定时重复执行。

`CASE WHEN` UPDATE 对同事务、同 before-image 的重复 UPDATE 做 last-writer-wins 去重。该去重是批量层最后防线：即使 intra-TX compaction 已能识别单事务热点，仍保留此校验以避免 future corner case 让重复 identity 进入同一 CASE WHEN。NULL identity value 使用 `IS NULL`，不绑定参数。

批量 UPDATE 已恢复 update-miss 与 `ConflictStrategy` 语义：`flushCaseWhenUpdate()` 执行后若 `affectedRows < rows.size()`，说明存在未命中行；由于 CASE WHEN 只返回聚合 affected rows，无法定位具体 miss 行，因此整批降级调用 `DmlApplyHelper.executeDML()` 逐条执行，由单行路径在 `affectedRows == 0` 时调用 `handleDupException(..., UPDATE_MISSED, ...)`。`flushSingleRowUpdate()` 也会在单条 UPDATE miss 时直接进入同一 conflict strategy 处理。

`handleDupException()` 是单行签名，只接收一个 `DefaultRowChange`；批量层没有可传入的单个 miss `rc`，所以不能直接在批量层调用它。整批降级会让批内已成功的行重复执行一次绝对值 UPDATE，但 after-image SET 是幂等的，仅增加一次 I/O，不影响正确性。

当前暂不对 batch INSERT 做同主键去重。若同一批 INSERT 中出现相同 PK，数据库会返回 duplicate key，`flushPendingBatch()` 捕获后降级逐条执行并按 conflict strategy 处理；该问题属于性能损耗，不是静默丢数据风险。

## 7. DDL 与 schema 演进

DDL 是 DML DAG 的串行屏障。迁移任务可配置仅允许 ADD COLUMN；来自 2.0/RDS/PolarDB-M 的 DDL 转换为实际 DDL 或 schema-refresh-only 事件。后者只刷新 `DbMetaCache`，不在目标端重复执行。

FULL_COPY、INC_COPY 可开启 `skipMismatchedColumns`，用于源端已经出现新列、目标端元数据尚未追平时跳过目标不存在的列。该能力只允许落到 FULL_COPY applier 或 INC_COPY 使用的 MERGE applier：前者只有 INSERT，后者会先把 UPDATE 转为 DELETE+INSERT，因此不存在“跳过 UPDATE 中的目标缺失列”语义。

`skipMismatchedColumns` 当前保存在 `DmlApplyHelper` 静态字段中。该实现依赖 RPL 的部署模型：每个 `RplTaskEngine` 进程只接收一个 `taskId`，`RplTaskRunner` 只创建一个 applier，因此单进程内不会出现不同任务类型相互覆盖该配置。若未来改成一个 JVM 承载多个复制任务，需要先将这些静态配置改成实例状态。

## 8. 配置与灰度

| 配置 | 当前默认值 | 说明 |
|---|---:|---|
| `rpl_compaction_enabled` | `true` | Applier 初始化时读取，控制执行时压缩；变更后需重启生效 |
| `rpl_batch_enabled` | `true` | Applier 初始化时读取，控制批量 DML；关闭时回退旧 apply 路径 |
| `rpl_compaction_log_enabled` | `false` | 是否逐批次/逐 window 输出 Graph、Exec、Compact 诊断日志；动态生效 |
| `rpl_inc_dml_batch_size` | `200` | 单次同表同动作批量行数 |
| `rpl_ddl_strip_leading_comments` | `false` | DDL 前导注释剥离 |

建议上线时先将 compaction/batch 按任务灰度开启，观察正确性校验、延迟、批量失败率、duplicate-key fallback、UPDATE miss fallback 和 compact window 指标后再扩大范围。

## 9. 一致性边界与失败处理

- compaction 只在一个目标事务内提交；执行失败时整体 rollback。
- DAG 提取、热点识别或 supersede 失败时回退到单事务执行。
- 批量执行仅对 duplicate key 做逐条 fallback。
- 批量 UPDATE miss 会整批降级逐条执行，以恢复 `UPDATE_MISSED` conflict strategy 语义。
- `DIRECT_OVERWRITE` 与 V3 的绝对值 UPDATE flush 路径语义不一致，构造期直接拒绝启动；需要该语义的任务不能使用 `TransactionParallelApplierV3`。
- DDL 前先 flush DML，DDL 后重新开始新 DML 段。
- 外键表必须关闭 `rpl_batch_enabled`，使用原始事务顺序执行。
- 表的 PK、UK、拆分键或实际 identity columns 既不在精确类型白名单内、也没有支持的归一化策略，或 UK 使用前缀/表达式/生成列时，整个 DML 批次串行执行。

## 10. 测试建议

除现有单元测试外，合入前应覆盖：

1. INSERT/UPDATE/DELETE 全状态组合，包含同事务多次 UPDATE 和 DELETE → INSERT 保留。
2. composite PK、分区键、UK、NULL UK、PK/分区键变更、UK 变更。
3. key 类型白名单、字符 DAG key 归一化且原始值不变，以及 FLOAT/DOUBLE、generated/表达式 UK、`name(16)` 前缀 UK 的串行回退。
4. persist on/off 下事件引用一致性。
5. VECTOR/GEOMETRY、generated column、目标端缺列。
6. DDL 位于批次头、中、尾以及重启后 token 复用。
7. compaction/batch 开关固化语义、批量大小边界和重启后配置生效。
8. 保持“一进程一任务”约束，或在多任务 JVM 改造时补充不同 applier 配置的隔离性测试。
9. 外键表必须关闭 `rpl_batch_enabled`，使用原始事务顺序执行。
10. 批量 UPDATE miss 降级、单条 UPDATE miss、`INTERRUPT/IGNORE/OVERWRITE` conflict strategy 行为。
11. `DIRECT_OVERWRITE` 构造期拒绝启动。

当前分支已补充的核心单测覆盖包括：`DIRECT_OVERWRITE` 拒绝、单事务进入 intra-TX compaction、compaction 关闭时回退、无 PK/UK 表串行回退、binary identity 深值判等、persist on/off、PK/分区键/UK barrier、batch on/off 最终数据，以及 compacted/batch UPDATE miss 在 `INTERRUPT/IGNORE/OVERWRITE` 下的行为。

## 11. 当前分支 review 结论（不含 datasource）

本次 review 范围排除 `DruidDataSourceWrapper` 及其测试，聚焦 compaction/batch 主路径。当前增量主要落在 `TransactionParallelApplierV3`、`DmlApplyHelper` 和 `TransactionParallelApplierV3Test`。

已确认的正确性修复：

- `rpl_compaction_enabled` 与 `rpl_batch_enabled` 在构造期读取并固化，避免运行时线程间读取动态配置导致行为漂移。
- intra-TX compaction 与跨事务 compaction 合并到同一套热点识别和 supersede 状态机，不新增独立 `compactIntraTx` 路径。
- 删除 `Edge/DagResult/edgePkMap` 元数据后，热点识别直接基于 window 内节点缓存的 PK 事件索引，简化 DAG 构建并支持单节点内热点。
- `DIRECT_OVERWRITE` 在构造期禁止，相关 `insertMode` 三元判断已删除，INSERT 批量固定使用普通 INSERT。
- 批量 UPDATE 和单条 UPDATE 都补齐 update-miss 处理；批量层通过整批降级逐条执行恢复 conflict strategy。
- identity 或 UK 变化都会斩断 compaction 链，并在批量 UPDATE 中走单条 UPDATE，避免唯一键让位/行迁移语义被重排或删除。
- key 变化 barrier 会先 finalize 已发生 supersede 的前置 segment，再清空状态；幸存 UPDATE 因而始终携带联合 after-image。
- 无 PK/UK 表直接回退串行路径，不进入 DAG、compaction 或 batch。
- DAG、compaction 与 CASE WHEN 去重对数组 identity 使用深值语义，并对传入 key map 做快照。
- 表元数据对所有 DAG/batch key 做类型检查；完整列 CHAR/VARCHAR 使用不影响实际值的 DAG-only 归一化，浮点、LOB/复杂类型、generated/表达式 key 以及前缀唯一索引回退串行。
- `DmlApplyHelper.handleDupException()` 在 `INTERRUPT` 且异常参数为 null 时抛出明确 `PolardbxException`，避免空异常导致信息丢失。

遗留边界：

- batch INSERT 同主键暂不去重；依赖 duplicate-key fallback 保证正确性，影响仅为性能。
- batch 不支持目标端外键保序；外键任务必须关闭 `rpl_batch_enabled`。
- 仍需真实链路 checksum/压测验证 compaction+batch 组合在目标 CN/DN 上的行为，尤其是 UPDATE miss fallback、UK 迁移和 duplicate-key fallback 指标。
