# feature/jiyue_replica_support_compaction 变更说明与代码审查

## 1. 审查范围

基线：`origin/cdc_release`；审查方式：`git diff origin/cdc_release...feature/jiyue_replica_support_compaction`。

该范围包含 27 个文件，约 3175 行新增、154 行删除。除 compaction 主功能外，还包含 DDL/schema 演进、恢复 SQL 转义、心跳、数据源选点、VECTOR 兼容和缺列跳过等改动。

## 2. 主要代码变更

### RPL apply 主路径

- `TransactionParallelApplierV3`：新增 DDL 屏障、PK/UK DAG、执行时 window compaction、supersede 状态机和 INSERT/DELETE/UPDATE 批量 SQL。
- `DefaultRowChange`：新增 `forceAllColumns`，支持压缩后幸存 UPDATE 写完整 after-image。
- `TableInfo`：补充 identity key 与列类型辅助能力。
- `DmlApplyHelper`：支持 force-all、不可比较类型、缺列跳过及批量 SQL 复用入口。
- `ApplierConfig`：新增 DML batch size、ADD COLUMN-only、skip mismatched columns。

### DDL 与导入兼容

- `LogEventConvert`、`ImportLogEventConvert`、`DefaultQueryLog`：增加 DDL 转换与 schema-refresh-only 标记。
- `MysqlApplier`、`DdlApplyHelper`：支持 ADD COLUMN 白名单、只刷新 schema、前导注释处理及重启 token 复用。
- `MemoryTableMeta`：增强 DDL 后元数据刷新。
- `FSMMetaManager`：按服务类型和源类型下发 DDL/XA/缺列兼容配置。

### 稳定性与基础设施

- `HeartbeatManager`：心跳失败最多重试 3 次；任务不存在或状态非 RUNNING 时 halt。
- `RecoveryApplier`：补齐 MySQL 文本转义。
- `DruidDataSourceWrapper`：从“每个 CN 一个池”改为“每任务固定一个 CN，失败再切换”。
- `MysqlEventParser`：时间戳定位跳过 FDE。
- `MonitorType`：调整复制告警类型。

## 3. Review 结论

结论：主流程思路完整且编译通过，但仍建议处理以下合入风险并完成真实链路验证。

### 已复核：`skipMismatchedColumns` 静态字段在当前进程模型下不会串扰

`RplTaskEngine` 只从启动参数接收一个 `taskId`，随后只创建一个 `RplTaskRunner`；`RplTaskRunner.createApplier()` 只为该任务构造一个 applier。因此一个 JVM 中只有一个任务类型和一个 applier，`DmlApplyHelper.skipMismatchedColumns` 不会被其他任务覆盖。此前将其判定为 P1 不成立，现撤销该问题。

该实现仍依赖“一进程一任务”的架构约束；如果未来支持单 JVM 多任务，再将静态配置实例化即可，不作为当前分支阻断项。

### 已处理：移除 `compareAll`

该特性已不再使用，现已删除 `COMPARE_ALL` change-master 参数、`ReplicaMeta/ApplierConfig` 字段、动态配置、随机 compare-all 实验开关、全列 WHERE 构造和冲突忽略分支。旧任务 JSON 中的冗余字段由 FastJSON 忽略。

### [P1] 批量 UPDATE 绕过原有冲突策略和 update-miss 处理

位置：`TransactionParallelApplierV3.flushUpdateBatch()`、`flushCaseWhenUpdate()`、`flushSingleRowUpdate()`。

原路径 `DmlApplyHelper.executeDML()` 会按 `ConflictStrategy` 处理 affectedRows=0、duplicate key 和 `insertOnUpdateMiss`。新批量 UPDATE 直接调用 `execSqlContext()`，不检查 affectedRows，也没有调用 `handleDupException()`：

- `DIRECT_OVERWRITE` 原本是 DELETE + REPLACE，新路径变成普通 UPDATE；目标行不存在时不会补行。
- `OVERWRITE` 原本可在 update-miss 时写入 after-image，新路径静默跳过。

建议：在没有完整实现 strategy-aware 批量语义前，UPDATE 一律回退 `DmlApplyHelper.executeDML()`；先只保留 INSERT/DELETE batching。

### 已复核：批量 INSERT/DELETE 保持现有冲突语义

- INSERT：`DIRECT_OVERWRITE` 直接生成批量 REPLACE；其他策略先执行普通批量 INSERT。duplicate-key 时批量语句整体失败，再逐条调用 `DmlApplyHelper.executeDML()`，所以 IGNORE、INTERRUPT、OVERWRITE 仍走原冲突分支。
- DELETE：原逐条路径不把 affectedRows=0 当成冲突，也没有 update-miss 类补偿；批量 DELETE 的 OR 条件只是合并相同语义的 DELETE，未绕过额外冲突处理。

因此冲突策略问题仅存在于新批量 UPDATE builder。

### 已明确：`skipMismatchedColumns` 仅支持 MERGE/FULL_COPY

该配置仅允许 `MERGE` 和 `FULL_COPY` applier 开启。MERGE 会先把 UPDATE 转换成 DELETE+INSERT，FULL_COPY 只有 INSERT，不会进入 TransactionParallelApplierV3 的批量 UPDATE builder。`MysqlApplier.init()` 已增加 fail-fast 校验，其他 applier 错配时直接拒绝启动。

### 已复核：同事务重复 UPDATE 已有 last-writer-wins 去重

位置：`TransactionParallelApplierV3.flushCaseWhenUpdate()`。

`flushCaseWhenUpdate()` 已按 before-image identity 使用 `LinkedHashMap` 去重，后写覆盖前写，解决相同 identity 的 CASE 首分支覆盖问题。当前实现适用于常规标量 PK/分区键。若未来支持 BINARY/VARBINARY identity，字符串 key 对 `byte[]` 不是内容比较，需要再改为结构化 deep-equality key；该场景暂不作为当前阻断项。

### 已明确：batch 不支持外键表

位置：`TransactionParallelApplierV3.executeBatchedDml()`。

实现按表累积 pending batch，末尾遍历 `HashMap` flush，跨表执行顺序不确定。代码注释直接假设“不同表之间无外键依赖”，但配置和入口没有证明或限制这一前提。原事务若为 `INSERT parent` 后 `INSERT child`，重排后可能先写 child；DELETE 场景相反，同样可能触发外键错误并使原本合法的事务失败。

产品约束为 batch 不支持外键。涉及外键的任务必须手工设置 `rpl_batch_enabled=false`，回退原始 `TransactionApplier` 路径。该限制已写入设计文档和上线检查单。

### [P1] 新的核心优化默认开启，建议调整发布默认值

位置：`config.properties` 中 `rpl_compaction_enabled=true`。

compaction 与 batch 当前都默认开启，首次发布的影响面较大。现已增加独立动态开关 `rpl_batch_enabled`：关闭后，多事件事务会回退到原有 `TransactionApplier` 路径；两个开关可独立组合。发生目标端 SQL 兼容问题时可关闭 batch，发生压缩正确性问题时可关闭 compaction。

建议：首次发布将两个开关默认关闭，经 shadow compare/灰度后再开启。

### [P1] CN failover 先清旧节点再选新节点，存在空地址窗口

位置：`DruidDataSourceWrapper.scan()`。

新流程先调用 `clearActiveNode()`，从 `nestedAddresses` 删除当前节点并关闭连接池，之后才调用 `activateNode()` 逐个创建和验证候选池。旧实现的顺序是先 add 新节点，再 remove 旧节点。新流程在候选池初始化、连接或 `isValid` 较慢时会让并发 `getConnection()` 进入最长 `maxWaitTimeMills` 的等待；若所有候选探测失败，wrapper 会长期保持空列表。

建议改为先创建并验证 replacement，成功后在写锁内直接将旧地址替换为新地址，最后 invalidate 旧池；没有健康 replacement 时保留当前池并持续告警。该方案尚待单独评审，不纳入本次推送。

### [P1] 连接池饱和与单次探测失败可能触发误切换

位置：`DruidDataSourceWrapper.isHealthy()`。

健康检查通过当前业务池 `getConnection()`。当池达到 `maxActive`、借用等待超时或连接初始化短暂抖动时，会被解释为节点不健康；结合“先清旧池”的流程，负载高峰可能造成连接池反复销毁和重建。该风险在旧多 CN 池模型中已有一部分，但单 active CN 后影响被放大。

建议区分“池饱和”和“节点不健康”，并设置连续失败阈值后再进入 replacement 流程；同时避免 `getConnection()` 持有 wrapper 读锁等待业务池。该方案尚待单独评审，不纳入本次推送。

### [P2] `DruidDataSourceWrapper` 选点/failover 测试不足

建议补充连续失败阈值与恢复、排除当前节点后的候选顺序，以及 active address 原子替换测试。相关实现和测试应在完成独立评审后单独提交。

### [P2] 变更范围耦合过大，难以灰度和回滚

一个分支同时修改 compaction、DDL 白名单、XA、心跳进程退出、恢复 SQL、CN 池模型及缺列策略。任一子功能回滚都必须整体回滚，且现有测试主要集中在 compaction、heartbeat、recovery，DDL/数据源/多任务配置隔离缺少对应覆盖。

建议：至少拆分为 compaction+batch、DDL/schema evolution、runtime stability、datasource selection 四组提交或独立 MR；分别提供开关和验证结果。

## 4. 验证结果

- `mvn -pl polardbx-cdc-rpl -am -DskipTests compile`：成功。
- 构建期间 Maven 尝试写 `~/.m2` 元数据被当前沙箱拒绝，但本地依赖完整，最终 reactor 11 个模块均为 SUCCESS。
- 定向执行 `TransactionParallelApplierV3Test,HeartbeatManagerTest,RecoveryApplierTest` 未通过：`TransactionParallelApplierV3Test` 的 28 个 case 在构造阶段均因 classpath 缺少 `testing-conf/spring-test.xml` 报错，尚未进入被测逻辑；同时 logback 尝试写 `~/logs` 被沙箱拒绝。该结果属于测试环境/测试装配失败，不能视为功能测试通过。
- 尚未完成真实 CN/DN 集成验证；现有环境信息不足以验证事务重排、DDL 灰度和连接池 failover。

## 5. 合入前检查单

- 保持并记录“一进程一任务”的部署约束；未来单 JVM 多任务改造时再处理静态配置隔离。
- 在批量 UPDATE 中恢复 ConflictStrategy 和 update-miss 语义；完成前可关闭 batch。
- 外键任务必须关闭 batch；上线检查需识别目标表外键。
- 已提供可回退旧 apply 路径的 batch 开关；发布前确认默认值和灰度策略。
- 完成 CN replacement 原子切换、连续失败阈值和基础 failover 单测，并进行真实 CN 下线演练。
- 拆分或至少独立开关搭车变更。
- 跑 `TransactionParallelApplierV3Test`、`HeartbeatManagerTest`、`RecoveryApplierTest`。
- 在真实目标端做 compaction on/off 数据 checksum 对比。
- 验证 DDL 重启、ADD COLUMN 前后 schema mismatch 窗口。
- 验证 CN 主动下线、健康检查失败及恢复后的连接池切换。
