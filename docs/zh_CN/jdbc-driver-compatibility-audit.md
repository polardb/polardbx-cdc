# JDBC 驱动切换兼容性审查

本次审查针对 `polardbx-connector-java:2.2.9`，覆盖仓库生产代码和 QATEST 的 JDBC 执行入口、PreparedStatement 调用、元数据名称、结果读取及直接驱动 API。结论是源码审查和针对性验证，不代表所有 SQL、数据类型、连接参数组合已经穷举验证。

## 为什么反复出现问题

此前验证偏重连接建立与基础 binlog 链路，没有完整覆盖客户端调用约定。驱动能连接成功，不等于以下行为都兼容：

- `executeQuery` 只能用于产生结果集的语句；`USE`、`SET` 不能借它执行。
- `executeUpdate` 不能用于 `ANALYZE TABLE` 等产生结果集的管理语句。驱动会在发送 SQL 前拒绝此类调用。
- DDL 不一定只返回更新计数；`Statement.execute()` 的布尔值表示是否返回结果集，不表示执行成功与否。
- JDBC 元数据的 catalog/schema、模式参数、驱动版本字符串、`getObject()` 返回类型也不能沿用未经验证的旧驱动假设。
- Mock 若无条件允许任意执行方法，会掩盖实际驱动的校验；PreparedStatement 的错误重载甚至曾被测试夹具固化。

## 本次修改

| 类别 | 修改 | 回归保护 |
| --- | --- | --- |
| Replica DDL 回放 | 新增 `SqlContextExecutor.execDdl`，同步、异步入口均使用 `execute()`；保留 SQL_MODE、FP_OVERRIDE_NOW 设置/恢复与异常传播 | 真实 2.2.9 StatementImpl + 模拟服务端传输；真实 CN 执行；既有 DDL 单测 |
| 暂停 DDL 恢复 | `CONTINUE DDL` 使用 `execute()` | `continuePausedDdlUsesGenericExecution` |
| 特殊库表名元数据 | 两种数据源入口统一启用 `useInformationSchema=true`，保留 `pedantic=true`，传递原始 JDBC 名称；两处 information_schema 条件改为参数绑定 | 真实 2.2.9 元数据 SQL 构造、H2 特殊字符库表名、真实 CN 完整 DbMetaManager 只读探针 |
| QATEST 管理 SQL | `JdbcUtil.analyzeTable()` 使用 `execute()` | 本地单测，不加入环境回归用例集 |
| PreparedStatement | 14 处带 SQL 参数的重载改为无参 `executeQuery()`：DbMetaManager 7、DdlApplyHelper 1、MysqlFullProcessor 1、BaseTestCase 2、FlashBackTest 2、DataConsistencyTest 1 | 更新元数据单测；增加全量抽取和 QATEST helper 调用约定测试 |
| 心跳库探测 | 先读取并关闭库名结果集，再复用 Statement 探测各库；关闭探测结果集 | 第一个库无心跳表、第二个库有表的测试，约束旧结果集关闭顺序 |
| DEBUG 日志 | DDL 的 params 为 null 时不再触发日志代码 NPE | 真实 CN 验证开启 DEBUG，DDL 正常执行 |
| 测试工具类型假设 | 驱动主版本改读 `getDriverMajorVersion()`；数值使用 `getLong`/`wasNull`，不强转 `getObject` 为 Long | 版本字符串占位符、非 Long 数值和 SQL NULL 单测 |

PreparedStatement 重载、游标生命周期以及未调用的工具方法是审查发现的兼容性/可移植性隐患，不全部归为本次实验室报错的根因。

## 核查后保留的行为

- DML 的 `execUpdate`、参数化 INSERT/UPDATE/DELETE、批量写入保留影响行数语义；没有把所有执行方法改成 `execute()`。
- `JdbcUtil.executeUpdate(Connection, String)` / `executeUpdateSuccess` 虽然名称带 Update，内部已经调用 `Statement.execute()`，不需要机械替换其调用者。
- `USE`、`SET`、事务控制与 JDBC 查询型 hint 的此前修复保留；源码搜索未发现同样的有效 `executeQuery("USE ...")` 调用。
- DbMetaManager 的 catalog/schema 分流、模式转义、空列集合拒绝及 UPDATE/DELETE 非空定位保护保留，并重跑对应单测。
- Spring JdbcTemplate 的 update/batchUpdate 入口核查为 MetaDB DML；LabTestJob 的管理语句已经使用 `execute`。
- 全量抽取使用按 SQL 类型选择的 `getString`/`getBytes`，而不是假设新驱动 `getObject` 返回某个固定 Java 类；其所有类型组合仍需完整数据类型回归。
- JDBC URL 适配、驱动注册与 DirectLogFetcher 使用的内部类/方法有现有针对性测试；注册或 Druid init 测试本身不等于真实建连测试。

## 验证

JDK 11，针对性测试命令：

```bash
mvn -pl polardbx-cdc-rpl,polardbx-cdc-test -am test \
  -Dtest=SqlContextExecutorTest,DdlApplyHelperTest,DdlApplyHelperReplicaTest,DbMetaManagerTest,DbMetaCacheTest,DruidDataSourceWrapperTest,DmlApplyHelperReplicaTest,DmlApplyHelperTest,DmlApplyHelperGeneratedColumnTest,TransactionParallelApplierV3Test,MysqlDetectingTimeTaskTest,MysqlFullProcessorJdbcTest,JdbcUtilTest,DirectLogFetcherCompatibilityTest,PolarDbxCompatDriverTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=false
```

229 个测试通过，Failures=0、Errors=0、Skipped=0（2026-09-16 14:16，JDK 11）。特殊名称覆盖字面反引号、单引号、反斜杠、通配符及中文；真实驱动测试验证 SQL 构造与参数，H2 测试验证完整元数据路径。PreparedStatement 的额外语法树扫描覆盖 24 个候选源码文件，声明类型检查未再发现带 SQL 参数的执行调用；该检查不是完整跨方法类型/别名分析，不能替代运行时验证。

真实 CN + 2.2.9 驱动验证使用独立临时库，执行的是本次编译的 SqlContextExecutor：

- 复现旧 `executeQuery(USE)` 和 `executeUpdate(ANALYZE)` 的驱动拒绝。
- 修改后的带审计注释 ANALYZE、限定库名/当前库 ANALYZE、CREATE/ALTER 正常执行。
- SQL_MODE 按集合恢复一致；FP_OVERRIDE_NOW 清空。
- 参数化 DML 影响行数、事务回滚、二进制值与 Decimal 值核对通过。
- 验证结束删除本次新建临时库，不覆盖服务安装文件。

SQL_MODE 的展示顺序可能不同，比较其模式集合，不能将顺序不同判作恢复失败。

## 仍需验证的边界

- 已给故障实验室受影响的正向 Replica 换包，原 ANALYZE 卡点及后续字面反引号表名卡点均已越过；正在回放历史积压，尚未证明追平。没有重置位点或手动跳过事务。
- 真实 JDBC 探针不是完整 Replica/QATEST E2E；异步 DDL、暂停后 CONTINUE 的真实服务器完整流程仍需专项或实验室回归。
- 未穷举 MySQL/CN 版本、鉴权/TLS、全部数据类型、流式读取与网络异常组合。
- 另一个实验室的压缩触发计数为 0：已验证创建时容器环境关闭自动压缩测试，与后置检查要求触发不匹配。没有修改该环境；历史 MetaDB 当前不可达，未审计所有动态覆盖值。此问题与 JDBC 修复独立。

后续驱动升级应复用上述检查矩阵：先验证实际驱动的语句分类和 JDBC 调用约定，再验证真实连接及数据面结果，最后检查实验室实际运行包与恢复位点。
