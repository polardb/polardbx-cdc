# Compaction+Batch 正确性验证 Case List

**文档版本**：v1.2（回填 Case 32-43 执行结果）  
**最后更新**：2026-07-29  
**目标**：为 compaction+batch 模块的换包后重新验证提供覆盖全面的测试 case 集

---

## 目录

- [一、Intra-TX Duplicate（事务内重复）](#一intra-tx-duplicate事务内重复)
- [二、Inter-TX Supersede（跨事务热点链）](#二inter-tx-supersede跨事务热点链)
- [三、Window 边界](#三window-边界)
- [四、Batch 边界](#四batch-边界)
- [五、并发与 DAG 顺序](#五并发与-dag-顺序)
- [六、特殊数据](#六特殊数据)
- [七、组合场景](#七组合场景)
- [八、连续变更链（Sequential Identity Changes）](#八连续变更链sequential-identity-changes)

---

## 一、Intra-TX Duplicate（事务内重复）

本组 case 验证同一事务内对同一行的多次操作是否被正确合并。这是本次 bug 的**直接触发场景**。

### Case 1: 同事务内对同行 2 次 UPDATE（最小复现）

**Case ID**：`INTRA_DUP_001`  
**名称**：Single Transaction, Same Row, 2 UPDATEs  
**覆盖的缺陷/路径**：
- 本次 bug 的直接复现场景
- `createMergedTransaction` 中的去重逻辑
- `flushCaseWhenUpdate` 中 last-writer-wins 去重（LinkedHashMap 后者覆盖前者）
- 验证修复是否有效

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_001 (
  id INT PRIMARY KEY,
  val INT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO test_intra_dup_001 VALUES (1, 10, '2026-07-08 10:00:00');
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_intra_dup_001 SET val = 20 WHERE id = 1;  -- 第一次UPDATE
  UPDATE test_intra_dup_001 SET val = 30 WHERE id = 1;  -- 第二次UPDATE（同行）
COMMIT;
```

**预期目标端结果**：
```
id=1, val=30  （最后一次UPDATE的after-image）
```

**验证 SQL**：
```sql
SELECT id, val FROM test_intra_dup_001 WHERE id = 1;
-- 预期：1, 30
```

**是否覆盖本次 bug 变体**：Yes（本次 bug 的最小化复现）

---

### Case 2: 同事务内对同行 3+ 次 UPDATE

**Case ID**：`INTRA_DUP_002`  
**名称**：Single Transaction, Same Row, 5 UPDATEs  
**覆盖的缺陷/路径**：
- 验证多次重复不仅仅是 2 次的场景
- CASE WHEN 批量 UPDATE 中多个 WHEN 分支的正确性
- MySQL CASE WHEN 首匹配行为

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_002 (
  id INT PRIMARY KEY,
  val1 INT DEFAULT 0,
  val2 INT DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO test_intra_dup_002 VALUES (1, 0, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_intra_dup_002 SET val1 = 10 WHERE id = 1;     -- 1st
  UPDATE test_intra_dup_002 SET val2 = 20 WHERE id = 1;     -- 2nd
  UPDATE test_intra_dup_002 SET val1 = 100 WHERE id = 1;    -- 3rd (val1 again)
  UPDATE test_intra_dup_002 SET val2 = 200 WHERE id = 1;    -- 4th (val2 again)
  UPDATE test_intra_dup_002 SET val1 = 1000 WHERE id = 1;   -- 5th (final)
COMMIT;
```

**预期目标端结果**：
```
id=1, val1=1000, val2=200  （所有UPDATE的最后状态）
```

**验证 SQL**：
```sql
SELECT id, val1, val2 FROM test_intra_dup_002 WHERE id = 1;
-- 预期：1, 1000, 200
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 3: 同事务内 INSERT 后 UPDATE 同行

**Case ID**：`INTRA_DUP_003`  
**名称**：Single Transaction, INSERT + UPDATE Same Row  
**覆盖的缺陷/路径**：
- INSERT→UPDATE 链的状态机转换
- 验证 supersede 状态机中 INSERT→UPDATE 的分支

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_003 (
  id INT PRIMARY KEY,
  name VARCHAR(50),
  age INT DEFAULT 0
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_intra_dup_003 (id, name, age) VALUES (1, 'Alice', 20);
  UPDATE test_intra_dup_003 SET age = 25 WHERE id = 1;
  UPDATE test_intra_dup_003 SET name = 'Alicia' WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, name='Alicia', age=25
```

**验证 SQL**：
```sql
SELECT id, name, age FROM test_intra_dup_003 WHERE id = 1;
-- 预期：1, 'Alicia', 25
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 4: 同事务内 UPDATE 后 DELETE 同行

**Case ID**：`INTRA_DUP_004`  
**名称**：Single Transaction, UPDATE + DELETE Same Row  
**覆盖的缺陷/路径**：
- UPDATE→DELETE 链的状态机转换
- 验证 supersede 后行的最终状态应为不存在

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_004 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_intra_dup_004 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_intra_dup_004 SET val = 20 WHERE id = 1;
  DELETE FROM test_intra_dup_004 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
行不存在（DELETE 覆盖了 UPDATE）
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_intra_dup_004 WHERE id = 1;
-- 预期：0
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 5: 同事务内 INSERT + UPDATE + DELETE 同行（完整生命周期）

**Case ID**：`INTRA_DUP_005`  
**名称**：Single Transaction, Full Lifecycle: INSERT + UPDATE + DELETE  
**覆盖的缺陷/路径**：
- INSERT→UPDATE→DELETE 完整链的状态机
- 最终结果应该是行不存在（所有中间态都被消除）

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_005 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_intra_dup_005 VALUES (1, 10);
  UPDATE test_intra_dup_005 SET val = 20 WHERE id = 1;
  UPDATE test_intra_dup_005 SET val = 30 WHERE id = 1;
  DELETE FROM test_intra_dup_005 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
行不存在
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_intra_dup_005;
-- 预期：0
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 6: 同事务内对多个不同行的 UPDATE + 其中一行重复（混合场景）

**Case ID**：`INTRA_DUP_006`  
**名称**：Single Transaction, Mixed Rows with One Duplicate  
**覆盖的缺陷/路径**：
- batch 合并中的选择性去重（只去重重复行）
- 确认非重复行不受影响

**前置建表 DDL**：
```sql
CREATE TABLE test_intra_dup_006 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_intra_dup_006 VALUES (1, 10), (2, 20), (3, 30);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_intra_dup_006 SET val = 100 WHERE id = 1;  -- row 1
  UPDATE test_intra_dup_006 SET val = 200 WHERE id = 2;  -- row 2
  UPDATE test_intra_dup_006 SET val = 111 WHERE id = 1;  -- row 1 重复
  UPDATE test_intra_dup_006 SET val = 300 WHERE id = 3;  -- row 3
  UPDATE test_intra_dup_006 SET val = 211 WHERE id = 2;  -- row 2 重复
COMMIT;
```

**预期目标端结果**：
```
id=1, val=111
id=2, val=211
id=3, val=300
```

**验证 SQL**：
```sql
SELECT * FROM test_intra_dup_006 ORDER BY id;
-- 预期：(1,111), (2,211), (3,300)
```

**是否覆盖本次 bug 变体**：Yes

---

## 二、Inter-TX Supersede（跨事务热点链）

本组 case 验证多个事务中对同一行的操作是否被正确识别为热点链并进行 supersede 合并。

### Case 7: 2 个事务各 UPDATE 同一行 1 次（最小 supersede）

**Case ID**：`INTER_SUPER_001`  
**名称**：Two Transactions, Same Row UPDATE (Minimal Supersede)  
**覆盖的缺陷/路径**：
- 跨事务热点链识别（chain length=2）
- supersede 状态机中的 UPDATE→UPDATE 转换
- compaction window 构建

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_001 (
  id INT PRIMARY KEY,
  val INT,
  version INT DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO test_inter_super_001 VALUES (1, 10, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_inter_super_001 SET val = 20, version = 1 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_inter_super_001 SET val = 30, version = 2 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, val=30, version=2  （第二次UPDATE）
```

**验证 SQL**：
```sql
SELECT * FROM test_inter_super_001 WHERE id = 1;
-- 预期：1, 30, 2
```

**是否覆盖本次 bug 变体**：No（但验证 supersede 基础机制）

---

### Case 8: 3+ 个事务链式 UPDATE 同一行

**Case ID**：`INTER_SUPER_002`  
**名称**：Multi-Transaction Chain UPDATE (5 TXs)  
**覆盖的缺陷/路径**：
- 长链的 supersede 合并
- window 能否包含整条链

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_002 (
  id INT PRIMARY KEY,
  counter INT DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO test_inter_super_002 VALUES (1, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_inter_super_002 SET counter = counter + 1 WHERE id = 1;  -- TX1: counter = 1
COMMIT;

BEGIN;
  UPDATE test_inter_super_002 SET counter = counter + 1 WHERE id = 1;  -- TX2: counter = 2
COMMIT;

BEGIN;
  UPDATE test_inter_super_002 SET counter = counter + 1 WHERE id = 1;  -- TX3: counter = 3
COMMIT;

BEGIN;
  UPDATE test_inter_super_002 SET counter = counter + 1 WHERE id = 1;  -- TX4: counter = 4
COMMIT;

BEGIN;
  UPDATE test_inter_super_002 SET counter = counter + 1 WHERE id = 1;  -- TX5: counter = 5
COMMIT;
```

**预期目标端结果**：
```
id=1, counter=5
```

**验证 SQL**：
```sql
SELECT * FROM test_inter_super_002 WHERE id = 1;
-- 预期：1, 5
```

**是否覆盖本次 bug 变体**：No

---

### Case 9: TX_A INSERT → TX_B UPDATE 同行

**Case ID**：`INTER_SUPER_003`  
**名称**：Transaction Chain: INSERT then UPDATE  
**覆盖的缺陷/路径**：
- INSERT→UPDATE 链的 DAG 依赖
- 链识别（insert 被 update 覆盖）

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_003 (
  id INT PRIMARY KEY,
  name VARCHAR(50),
  status VARCHAR(20)
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_inter_super_003 VALUES (1, 'Alice', 'created');
COMMIT;

BEGIN;
  UPDATE test_inter_super_003 SET status = 'verified' WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, name='Alice', status='verified'
```

**验证 SQL**：
```sql
SELECT * FROM test_inter_super_003 WHERE id = 1;
-- 预期：1, 'Alice', 'verified'
```

**是否覆盖本次 bug 变体**：No

---

### Case 10: TX_A UPDATE → TX_B DELETE 同行

**Case ID**：`INTER_SUPER_004`  
**名称**：Transaction Chain: UPDATE then DELETE  
**覆盖的缺陷/路径**：
- UPDATE→DELETE 链
- 最终行应不存在

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_004 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_inter_super_004 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_inter_super_004 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  DELETE FROM test_inter_super_004 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
行不存在
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_inter_super_004 WHERE id = 1;
-- 预期：0
```

**是否覆盖本次 bug 变体**：No

---

### Case 11: TX_A INSERT → TX_B UPDATE → TX_C DELETE（完整链）

**Case ID**：`INTER_SUPER_005`  
**名称**：Transaction Chain: Full Lifecycle (3 TXs)  
**覆盖的缺陷/路径**：
- 跨三个事务的完整链
- 最终状态：行不存在

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_005 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_inter_super_005 VALUES (1, 10);
COMMIT;

BEGIN;
  UPDATE test_inter_super_005 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  DELETE FROM test_inter_super_005 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
行不存在
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_inter_super_005;
-- 预期：0
```

**是否覆盖本次 bug 变体**：No

---

### Case 12: PK 变更打断链（UPDATE 改变了 identity 列值）

**Case ID**：`INTER_SUPER_006`  
**名称**：Transaction Chain with Identity Column Change  
**覆盖的缺陷/路径**：
- `isPkChanged()` 检测 identity key 变更
- 链在 PK 变更处应被切断
- 变更前后作为不同行处理

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_006 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_inter_super_006 VALUES (1, 1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_inter_super_006 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_inter_super_006 SET shard_key = 2 WHERE id = 1;  -- shard_key 变更
COMMIT;

BEGIN;
  UPDATE test_inter_super_006 SET val = 30 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=2, val=30
```

**验证 SQL**：
```sql
SELECT * FROM test_inter_super_006 WHERE id = 1;
-- 预期：1, 2, 30
```

**是否覆盖本次 bug 变体**：No（但验证链切断机制）

---

### Case 13: 多个不同 PK 的热点链在同一 window 内交叉

**Case ID**：`INTER_SUPER_007`  
**名称**：Multiple Independent Hot Chains in One Window  
**覆盖的缺陷/路径**：
- 单个 window 内多条独立热点链的并行处理
- per-key supersede 状态机的隔离性

**前置建表 DDL**：
```sql
CREATE TABLE test_inter_super_007 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_inter_super_007 VALUES (1, 10), (2, 20), (3, 30);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_inter_super_007 SET val = 11 WHERE id = 1;
  UPDATE test_inter_super_007 SET val = 21 WHERE id = 2;
  UPDATE test_inter_super_007 SET val = 31 WHERE id = 3;
COMMIT;

BEGIN;
  UPDATE test_inter_super_007 SET val = 12 WHERE id = 1;
  UPDATE test_inter_super_007 SET val = 22 WHERE id = 2;
  UPDATE test_inter_super_007 SET val = 32 WHERE id = 3;
COMMIT;

BEGIN;
  UPDATE test_inter_super_007 SET val = 13 WHERE id = 1;
  UPDATE test_inter_super_007 SET val = 23 WHERE id = 2;
  UPDATE test_inter_super_007 SET val = 33 WHERE id = 3;
COMMIT;
```

**预期目标端结果**：
```
id=1, val=13
id=2, val=23
id=3, val=33
```

**验证 SQL**：
```sql
SELECT * FROM test_inter_super_007 ORDER BY id;
-- 预期：(1,13), (2,23), (3,33)
```

**是否覆盖本次 bug 变体**：No

---

## 三、Window 边界

本组 case 验证 compaction window 的边界条件（MAX_COMPACT_TRANSACTIONS=64, MAX_COMPACT_EVENTS=200）是否会导致数据丢失或不一致。

### Case 14: 热点链末尾节点被 MAX_COMPACT_EVENTS=200 截断（验证后续正确 apply）

**Case ID**：`WINDOW_BOUND_001`  
**名称**：Hot Chain Last Node Excluded by MAX_COMPACT_EVENTS  
**覆盖的缺陷/路径**：
- 本次 bug 的**根本触发条件**
- MAX_COMPACT_EVENTS=200 限制导致末尾节点被排除
- 验证被排除节点的后续独立 apply 是否覆盖

**前置建表 DDL**：
```sql
CREATE TABLE test_window_bound_001_dup (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

CREATE TABLE test_window_bound_001_filler (
  filler_id INT PRIMARY KEY,
  filler_data TEXT
) ENGINE=InnoDB;

INSERT INTO test_window_bound_001_dup VALUES (1, 0);
INSERT INTO test_window_bound_001_filler VALUES 
  (1, REPEAT('x', 1000)), (2, REPEAT('y', 1000)), ..., (100, REPEAT('z', 1000));
```

**源端执行 SQL 序列**：
```sql
-- TX1: 150 events (filler table)
BEGIN;
  INSERT INTO test_window_bound_001_filler VALUES (101, REPEAT('a', 1000));
  ... (共 ~150 条 INSERT)
COMMIT;

-- TX2: 60 events + UPDATE on dup table
BEGIN;
  UPDATE test_window_bound_001_dup SET val = 10 WHERE id = 1;  -- ★ hot PK update
  INSERT INTO test_window_bound_001_filler VALUES (251, REPEAT('b', 1000));
  ... (共 60 条 INSERT，总计 61 events)
COMMIT;

-- TX3: 100 events + UPDATE on dup table
--      TX1+TX2+TX3 = 150+61+101 = 312 events > 200
--      所以 TX3 会被 MAX_COMPACT_EVENTS 截断
BEGIN;
  UPDATE test_window_bound_001_dup SET val = 20 WHERE id = 1;  -- ★ hot PK update (WILL BE EXCLUDED)
  INSERT INTO test_window_bound_001_filler VALUES (351, REPEAT('c', 1000));
  ... (共 100 条 INSERT，总计 101 events)
COMMIT;
```

**预期目标端结果**：
```
test_window_bound_001_dup: id=1, val=20  （第二次 UPDATE）
```

**验证 SQL**：
```sql
SELECT * FROM test_window_bound_001_dup WHERE id = 1;
-- 预期：1, 20  （如果被丢失则为 1, 10）
```

**是否覆盖本次 bug 变体**：Yes（本次 bug 的核心场景）

---

### Case 15: 热点链末尾节点被 MAX_COMPACT_TRANSACTIONS=64 截断

**Case ID**：`WINDOW_BOUND_002`  
**名称**：Hot Chain Last Node Excluded by MAX_COMPACT_TRANSACTIONS  
**覆盖的缺陷/路径**：
- 与 Case 14 类似，但通过事务数而非 event 数截断
- MAX_COMPACT_TRANSACTIONS=64

**前置建表 DDL**：
```sql
CREATE TABLE test_window_bound_002 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_window_bound_002 VALUES (1, 0);
```

**源端执行 SQL 序列**：
```sql
-- TX1~TX63: 各 1 event
-- DAG: TX1 → TX2 → ... → TX63 → TX64
BEGIN;
  UPDATE test_window_bound_002 SET val = 1 WHERE id = 1;
COMMIT;

-- 重复 63 次...

-- TX64: 会被 MAX_COMPACT_TRANSACTIONS 截断
BEGIN;
  UPDATE test_window_bound_002 SET val = 64 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, val=64
```

**验证 SQL**：
```sql
SELECT * FROM test_window_bound_002 WHERE id = 1;
-- 预期：1, 64
```

**是否覆盖本次 bug 变体**：Yes（但需要构造 64 个链式事务）

---

### Case 16: 单事务 event 数极大（接近 200），验证 window size=1 的降级

**Case ID**：`WINDOW_BOUND_003`  
**名称**：Single Transaction with 180+ Events  
**覆盖的缺陷/路径**：
- 单事务内 180+ events 时，window 仅含自身（size=1）
- 应降级到 `applySingle()` 而非 compaction

**前置建表 DDL**：
```sql
CREATE TABLE test_window_bound_003 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_window_bound_003 (id, val) VALUES (1, 1);
  INSERT INTO test_window_bound_003 (id, val) VALUES (2, 2);
  ... (共 180+ 条 INSERT)
COMMIT;
```

**预期目标端结果**：
```
180+ 条行数据正确插入
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_window_bound_003;
-- 预期：180+
```

**是否覆盖本次 bug 变体**：No（但验证降级路径）

---

### Case 17: 所有事务都是独立 PK（无热点链），验证 applySingle 路径

**Case ID**：`WINDOW_BOUND_004`  
**名称**：All Independent PKs, No Hot Chains  
**覆盖的缺陷/路径**：
- `identifyHotPkChains()` 返回空
- `applyWithCompaction()` 应降级到 `applySingle()`
- 验证非热点场景不被 compaction 拖累

**前置建表 DDL**：
```sql
CREATE TABLE test_window_bound_004 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  INSERT INTO test_window_bound_004 VALUES (1, 10);
COMMIT;

BEGIN;
  INSERT INTO test_window_bound_004 VALUES (2, 20);
COMMIT;

BEGIN;
  INSERT INTO test_window_bound_004 VALUES (3, 30);
COMMIT;

BEGIN;
  INSERT INTO test_window_bound_004 VALUES (4, 40);
COMMIT;
```

**预期目标端结果**：
```
(1,10), (2,20), (3,30), (4,40)
```

**验证 SQL**：
```sql
SELECT * FROM test_window_bound_004 ORDER BY id;
-- 预期：按 id 递增顺序
```

**是否覆盖本次 bug 变体**：No

---

## 四、Batch 边界

本组 case 验证 batch DML 的 batch_size 上限（默认 200）和 identity 变更的拆分逻辑。

### Case 18: 刚好 batch_size=50 条同 PK UPDATE → 不触发跨 batch

**Case ID**：`BATCH_BOUND_001`  
**名称**：Exactly 50 UPDATEs for Same Row, batch_size=50  
**覆盖的缺陷/路径**：
- batch 边界条件
- CASE WHEN 合并上限

**前置建表 DDL**：
```sql
CREATE TABLE test_batch_bound_001 (
  id INT PRIMARY KEY,
  counter INT DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO test_batch_bound_001 VALUES (1, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  -- 生成 50 条 UPDATE 语句
  UPDATE test_batch_bound_001 SET counter = counter + 1 WHERE id = 1;  -- 50x
COMMIT;
```

**预期目标端结果**：
```
id=1, counter=50
```

**验证 SQL**：
```sql
SELECT * FROM test_batch_bound_001 WHERE id = 1;
-- 预期：1, 50
```

**是否覆盖本次 bug 变体**：No（但验证 batch 边界）

---

### Case 19: 51 条 UPDATE for same table → 跨 batch，验证不丢

**Case ID**：`BATCH_BOUND_002`  
**名称**：51 UPDATEs for Same Table, Crossing batch_size=50  
**覆盖的缺陷/路径**：
- batch 在第 50 条 flush，第 51 条开启新 batch
- 验证两个 batch 的结果都应用

**前置建表 DDL**：
```sql
CREATE TABLE test_batch_bound_002 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_batch_bound_002 VALUES 
  (1, 10), (2, 20), ..., (51, 510);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_batch_bound_002 SET val = val + 1000 WHERE id BETWEEN 1 AND 51;  -- 51 条 UPDATE
COMMIT;
```

**预期目标端结果**：
```
所有 51 行的 val 都增加了 1000
```

**验证 SQL**：
```sql
SELECT COUNT(*) FROM test_batch_bound_002 WHERE val >= 1010;
-- 预期：51
```

**是否覆盖本次 bug 变体**：No

---

### Case 20: identity 变更行切断 batch 的前后段正确性

**Case ID**：`BATCH_BOUND_003`  
**名称**：Batch Segmentation by Identity Column Change  
**覆盖的缺陷/路径**：
- `flushCaseWhenUpdate()` 对 identity 未变更的行
- `flushSingleRowUpdate()` 对 identity 变更的行
- 验证分段执行后前后段都正确

**前置建表 DDL**：
```sql
CREATE TABLE test_batch_bound_003 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_batch_bound_003 VALUES 
  (1, 1, 10), (2, 1, 20), (3, 1, 30),
  (4, 2, 40), (5, 2, 50), (6, 2, 60);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_batch_bound_003 SET val = 100 WHERE id = 1;     -- normal
  UPDATE test_batch_bound_003 SET val = 200 WHERE id = 2;     -- normal
  UPDATE test_batch_bound_003 SET shard_key = 2 WHERE id = 3; -- ★ identity change
  UPDATE test_batch_bound_003 SET val = 300 WHERE id = 4;     -- normal (after split)
  UPDATE test_batch_bound_003 SET val = 400 WHERE id = 5;     -- normal
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=1, val=100
id=2, shard_key=1, val=200
id=3, shard_key=2, val=30    （shard_key 变更，但 val 不变 — UPDATE 仅改了 shard_key）
id=4, shard_key=2, val=300
id=5, shard_key=2, val=400
```

**验证 SQL**：
```sql
SELECT * FROM test_batch_bound_003 WHERE id IN (1,2,3,4,5) ORDER BY id;
```

**是否覆盖本次 bug 变体**：No（但验证分段机制）

---

### Case 21: forceAllColumns 模式下 SET 列正确性

**Case ID**：`BATCH_BOUND_004`  
**名称**：forceAllColumns Mode SET Column Correctness  
**覆盖的缺陷/路径**：
- supersede 后 UPDATE 标记 `forceAllColumns=true`
- `getForceAllColumnsUpdateColumns()` 排除 PK + 分区键列
- 验证 SET 列的正确性

**前置建表 DDL**：
```sql
CREATE TABLE test_batch_bound_004 (
  id INT PRIMARY KEY,
  shard_key INT,
  col_a INT,
  col_b INT,
  col_c INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_batch_bound_004 VALUES 
  (1, 1, 10, 20, 30);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_batch_bound_004 SET col_a = 100 WHERE id = 1;  -- 第一次更新
COMMIT;

BEGIN;
  UPDATE test_batch_bound_004 SET col_b = 200 WHERE id = 1;  -- 第二次更新（会 supersede 第一次）
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=1, col_a=10, col_b=200, col_c=30
（第一次的 col_a=100 会被覆盖，因为第二次 forceAllColumns 会重置不在 SET 中的列）
```

**验证 SQL**：
```sql
SELECT * FROM test_batch_bound_004 WHERE id = 1;
-- 预期：1, 1, 10, 200, 30
```

**是否覆盖本次 bug 变体**：No（但验证 forceAllColumns 机制）

---

## 五、并发与 DAG 顺序

本组 case 验证 DAG 并行调度中的顺序保证和 consumed 节点处理。

### Case 22: 多个无依赖的 TxNode 并行 apply（互不影响的 PK）

**Case ID**：`CONCUR_001`  
**名称**：Multiple Independent Transactions, Parallel Apply  
**覆盖的缺陷/路径**：
- DAG 中无依赖的节点可并行执行
- 验证并行执行不导致数据不一致

**前置建表 DDL**：
```sql
CREATE TABLE test_concur_001 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_concur_001 VALUES 
  (1, 10), (2, 20), (3, 30), (4, 40);
```

**源端执行 SQL 序列**：
```sql
-- 这些事务应该能并行执行（不同 PK）
BEGIN;
  UPDATE test_concur_001 SET val = 100 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_concur_001 SET val = 200 WHERE id = 2;
COMMIT;

BEGIN;
  UPDATE test_concur_001 SET val = 300 WHERE id = 3;
COMMIT;

BEGIN;
  UPDATE test_concur_001 SET val = 400 WHERE id = 4;
COMMIT;
```

**预期目标端结果**：
```
所有行的 val 都正确更新
```

**验证 SQL**：
```sql
SELECT SUM(val) FROM test_concur_001;
-- 预期：100+200+300+400 = 1000
```

**是否覆盖本次 bug 变体**：No

---

### Case 23: 有依赖的 TxNode 串行 apply（DAG edge 保序）

**Case ID**：`CONCUR_002`  
**名称**：Dependent Transactions, Sequential Apply by DAG Edge  
**覆盖的缺陷/路径**：
- 同 PK 的事务应通过 DAG 边建立依赖
- 验证顺序执行而非乱序

**前置建表 DDL**：
```sql
CREATE TABLE test_concur_002 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_concur_002 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_concur_002 SET val = 20 WHERE id = 1;  -- TX1
COMMIT;

BEGIN;
  UPDATE test_concur_002 SET val = 30 WHERE id = 1;  -- TX2 depends on TX1
COMMIT;

BEGIN;
  UPDATE test_concur_002 SET val = 40 WHERE id = 1;  -- TX3 depends on TX2
COMMIT;
```

**预期目标端结果**：
```
id=1, val=40  （最后一次 UPDATE）
```

**验证 SQL**：
```sql
SELECT * FROM test_concur_002 WHERE id = 1;
-- 预期：1, 40
```

**是否覆盖本次 bug 变体**：No

---

### Case 24: Consumed 节点跳过但正确触发下游

**Case ID**：`CONCUR_003`  
**名称**：Consumed Node Skips Execution but Triggers Downstream  
**覆盖的缺陷/路径**：
- consumed 节点加入 `consumedNodes` 后，DB 执行被跳过
- 但下游 indegree 递减应正常进行
- 验证下游不会永远等待

**前置建表 DDL**：
```sql
CREATE TABLE test_concur_003 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_concur_003 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_concur_003 SET val = 20 WHERE id = 1;  -- TX1（会被 window 包含）
COMMIT;

BEGIN;
  UPDATE test_concur_003 SET val = 30 WHERE id = 1;  -- TX2（会被 window 包含，标记 consumed）
COMMIT;

BEGIN;
  UPDATE test_concur_003 SET val = 40 WHERE id = 1;  -- TX3（依赖 TX2，会被 consumed 触发）
COMMIT;
```

**预期目标端结果**：
```
id=1, val=40  （TX3 应该正确执行）
```

**验证 SQL**：
```sql
SELECT * FROM test_concur_003 WHERE id = 1;
-- 预期：1, 40
```

**是否覆盖本次 bug 变体**：No（但验证 consumed 机制）

---

## 六、特殊数据

本组 case 验证特殊数据类型和表结构对 compaction+batch 的影响。

### Case 25: NULL 值在 identity 列中（应降级或正确处理）

**Case ID**：`SPECIAL_DATA_001`  
**名称**：NULL in Identity/Shard Key Column  
**覆盖的缺陷/路径**：
- identity 列可能为 NULL
- TableKey 构建时应正确处理 NULL 值的比较

**前置建表 DDL**：
```sql
CREATE TABLE test_special_data_001 (
  id INT PRIMARY KEY,
  shard_key INT,  -- nullable
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_special_data_001 VALUES (1, NULL, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_special_data_001 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_special_data_001 SET val = 30 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=NULL, val=30
```

**验证 SQL**：
```sql
SELECT * FROM test_special_data_001 WHERE id = 1;
-- 预期：1, NULL, 30
```

**是否覆盖本次 bug 变体**：No

---

### Case 26: 大值列（TEXT/BLOB/LONGTEXT 超 64KB）

**Case ID**：`SPECIAL_DATA_002`  
**名称**：Large Column Values (TEXT/BLOB > 64KB)  
**覆盖的缺陷/路径**：
- 大列数据对 batch 合并的影响
- rowSize guard 是否生效（见设计文档 15.3）

**前置建表 DDL**：
```sql
CREATE TABLE test_special_data_002 (
  id INT PRIMARY KEY,
  large_text LONGTEXT,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_special_data_002 VALUES (1, REPEAT('x', 100000), 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_special_data_002 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_special_data_002 SET large_text = REPEAT('y', 100000) WHERE id = 1;
  UPDATE test_special_data_002 SET val = 30 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, large_text=yyyyyy...(100000), val=30
```

**验证 SQL**：
```sql
SELECT id, LENGTH(large_text), val FROM test_special_data_002 WHERE id = 1;
-- 预期：1, 100000, 30
```

**是否覆盖本次 bug 变体**：No

---

### Case 27: 复合主键（3 列+）

**Case ID**：`SPECIAL_DATA_003`  
**名称**：Composite Primary Key (3+ Columns)  
**覆盖的缺陷/路径**：
- TableKey 构建时对多列 PK 的处理
- identity 列提取的正确性

**前置建表 DDL**：
```sql
CREATE TABLE test_special_data_003 (
  id1 INT,
  id2 INT,
  id3 INT,
  val INT,
  PRIMARY KEY (id1, id2, id3)
) ENGINE=InnoDB;

INSERT INTO test_special_data_003 VALUES (1, 2, 3, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_special_data_003 SET val = 20 WHERE id1 = 1 AND id2 = 2 AND id3 = 3;
COMMIT;

BEGIN;
  UPDATE test_special_data_003 SET val = 30 WHERE id1 = 1 AND id2 = 2 AND id3 = 3;
COMMIT;
```

**预期目标端结果**：
```
id1=1, id2=2, id3=3, val=30
```

**验证 SQL**：
```sql
SELECT * FROM test_special_data_003 WHERE id1 = 1 AND id2 = 2 AND id3 = 3;
-- 预期：1, 2, 3, 30
```

**是否覆盖本次 bug 变体**：No

---

### Case 28: 无主键表（若 compaction 支持的话）

**Case ID**：`SPECIAL_DATA_004`  
**名称**：Table Without Primary Key (If Supported)  
**覆盖的缺陷/路径**：
- 无 PK 表的 identity 列提取（应该用 UK 或所有列）
- compaction 对无 PK 表的降级行为

**前置建表 DDL**：
```sql
CREATE TABLE test_special_data_004 (
  id INT,
  val INT,
  UNIQUE KEY uk_id (id)
) ENGINE=InnoDB;

INSERT INTO test_special_data_004 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_special_data_004 SET val = 20 WHERE id = 1;
COMMIT;

BEGIN;
  UPDATE test_special_data_004 SET val = 30 WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, val=30
```

**验证 SQL**：
```sql
SELECT * FROM test_special_data_004 WHERE id = 1;
-- 预期：1, 30
```

**是否覆盖本次 bug 变体**：No

---

## 七、组合场景

本组 case 验证多个维度的组合。

### Case 29: Intra-TX duplicate + Inter-TX supersede 混合（同一 PK 既有事务内重复又跨事务）

**Case ID**：`COMBO_001`  
**名称**：Combined Intra-TX Dup + Inter-TX Supersede  
**覆盖的缺陷/路径**：
- 同一 PK 在单事务内重复，又跨事务被 UPDATE
- 应正确处理两层去重

**前置建表 DDL**：
```sql
CREATE TABLE test_combo_001 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_combo_001 VALUES (1, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_combo_001 SET val = 10 WHERE id = 1;  -- 事务内第 1 次
  UPDATE test_combo_001 SET val = 20 WHERE id = 1;  -- 事务内第 2 次
COMMIT;

BEGIN;
  UPDATE test_combo_001 SET val = 30 WHERE id = 1;  -- 跨事务
COMMIT;

BEGIN;
  UPDATE test_combo_001 SET val = 40 WHERE id = 1;  -- 事务内第 1 次
  UPDATE test_combo_001 SET val = 50 WHERE id = 1;  -- 事务内第 2 次
COMMIT;
```

**预期目标端结果**：
```
id=1, val=50
```

**验证 SQL**：
```sql
SELECT * FROM test_combo_001 WHERE id = 1;
-- 预期：1, 50
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 30: 多表交叉（TX 内同时更新 table_a 和 table_b，table_a 有 intra-TX dup）

**Case ID**：`COMBO_002`  
**名称**：Multi-Table with Intra-TX Dup on One Table  
**覆盖的缺陷/路径**：
- 同事务内多表操作
- 不同表的事件应独立批处理

**前置建表 DDL**：
```sql
CREATE TABLE test_combo_002_a (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

CREATE TABLE test_combo_002_b (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_combo_002_a VALUES (1, 10);
INSERT INTO test_combo_002_b VALUES (1, 20);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_combo_002_a SET val = 100 WHERE id = 1;
  UPDATE test_combo_002_b SET val = 200 WHERE id = 1;
  UPDATE test_combo_002_a SET val = 110 WHERE id = 1;  -- table_a 重复
  UPDATE test_combo_002_b SET val = 210 WHERE id = 1;  -- table_b 重复
COMMIT;
```

**预期目标端结果**：
```
table_a: id=1, val=110
table_b: id=1, val=210
```

**验证 SQL**：
```sql
SELECT * FROM test_combo_002_a WHERE id = 1;
SELECT * FROM test_combo_002_b WHERE id = 1;
-- 预期：(1, 110) 和 (1, 210)
```

**是否覆盖本次 bug 变体**：Yes

---

### Case 31: DDL 穿插（DML batch 被 DDL 切断后恢复）

**Case ID**：`COMBO_003`  
**名称**：DML Batch Interrupted by DDL  
**覆盖的缺陷/路径**：
- DDL 应作为 barrier，打断 DML batch
- DDL 后 DML batch 应在新 barrier 中继续

**前置建表 DDL**：
```sql
CREATE TABLE test_combo_003 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_combo_003 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_combo_003 SET val = 20 WHERE id = 1;
COMMIT;

-- DDL barrier
ALTER TABLE test_combo_003 ADD COLUMN col2 VARCHAR(50) DEFAULT 'default';

BEGIN;
  UPDATE test_combo_003 SET val = 30 WHERE id = 1;
  UPDATE test_combo_003 SET col2 = 'updated' WHERE id = 1;
COMMIT;
```

**预期目标端结果**：
```
id=1, val=30, col2='updated'
```

**验证 SQL**：
```sql
SELECT * FROM test_combo_003 WHERE id = 1;
-- 预期：1, 30, 'updated'
```

**是否覆盖本次 bug 变体**：No

---

## 八、连续变更链（Sequential Identity Changes）

本组 case（Case 32-43）验证同一逻辑行的 PK/分区键发生**连续变更**（链式、回环、互换、释放-复用、跨 window）时，compaction 斩链、batch 分段与 DAG 建边的正确性。核心被测代码：`polardbx-cdc-rpl/src/main/java/com/aliyun/polardbx/rpl/applier/TransactionParallelApplierV3.java`。

本章涉及的关键代码路径（各 case 的"覆盖的代码路径"栏引用）：
- `applySupersedeMachine`（约 L539-615）：遇 `isIdentityOrUkChanged`（L1246-1269）即 `state=null` 斩链，identity 变更前后作为不同链处理；
- `flushUpdateBatch`（L823-870）分段执行：identity 变更行拆单条 `flushSingleRowUpdate`（L975-1029），前后段走 CASE WHEN 批量 `flushCaseWhenUpdate`（L878-967，LinkedHashMap 去重）；
- DAG 建边：UPDATE 同时提取 before-key/after-key（L1076-1088）；TableKey 含分区键（L1183）；
- DELETE 后 `state=null` 不建链（禁止 DELETE→INSERT 压缩）。

### Case 32: 跨事务 PK 链式变更 A→B→C 后接普通列 UPDATE

**Case ID**：`CONTSEQ_032`  
**名称**：Cross-TX PK Chain A→B→C then Normal-Column UPDATE  
**场景描述**：同一逻辑行的 PK 跨事务链式变更 1→2→3（TX1/TX2），随后 TX3/TX4 在新 PK（id=3）上继续 UPDATE 普通列。验证 PK 变更处斩链、新旧 PK 不被误并入同一条 CASE WHEN 链。

**覆盖的代码路径**：
- `applySupersedeMachine`（L539-615）：`isIdentityOrUkChanged`（L1246-1269）判定 PK 变更，`state=null` 斩链
- `flushUpdateBatch`（L823-870）：PK 变更行拆 `flushSingleRowUpdate`（L975-1029）
- DAG 建边：UPDATE 提取 before-key/after-key（L1076-1088）保证链式顺序

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_032 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_contseq_032 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_032 SET id = 2 WHERE id = 1;   -- TX1: PK A→B
COMMIT;

BEGIN;
  UPDATE test_contseq_032 SET id = 3 WHERE id = 2;   -- TX2: PK B→C
COMMIT;

BEGIN;
  UPDATE test_contseq_032 SET val = 20 WHERE id = 3; -- TX3: 新 PK 上普通列 UPDATE
COMMIT;

BEGIN;
  UPDATE test_contseq_032 SET val = 99 WHERE id = 3; -- TX4: 新 PK 上再次 UPDATE
COMMIT;
```

**预期目标端结果**：
```
id=3, val=99；旧 PK（id=1、id=2）无残留；全表行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, val) FROM test_contseq_032 WHERE id = 3;   -- 预期：3,99
SELECT COUNT(*) FROM test_contseq_032 WHERE id IN (1, 2);            -- 预期：0（旧 PK 无残留）
SELECT COUNT(*) FROM test_contseq_032;                               -- 预期：1
```

**风险点**：
- 若斩链失败，1→2→3 被误并为单条 CASE WHEN，可能残留旧 PK 行或丢失终态；
- supersede 后 forceAllColumns UPDATE 若把 after-key 错绑到旧 PK，会 UPDATE miss。

---

### Case 33: 跨事务 PK 回环 A→B→A 后接 UPDATE

**Case ID**：`CONTSEQ_033`  
**名称**：Cross-TX PK Loop A→B→A then UPDATE  
**场景描述**：PK 跨事务回环 1→2→1，随后在 id=1 上 UPDATE 普通列。回环使链首尾 TableKey 相同，验证不会因 key 相同被错误折叠。

**覆盖的代码路径**：
- `applySupersedeMachine`（L539-615）：两次 `isIdentityOrUkChanged`（L1246-1269）斩链
- DAG 建边：before-key/after-key（L1076-1088）——回环导致 key 集合重叠，依赖边必须保序
- `flushCaseWhenUpdate`（L878-967）：LinkedHashMap 以 identity key 去重，回环 key 冲突不得跨链折叠

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_033 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_contseq_033 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_033 SET id = 2 WHERE id = 1;   -- TX1: PK A→B
COMMIT;

BEGIN;
  UPDATE test_contseq_033 SET id = 1 WHERE id = 2;   -- TX2: PK B→A（回环）
COMMIT;

BEGIN;
  UPDATE test_contseq_033 SET val = 77 WHERE id = 1; -- TX3: 回环后 UPDATE
COMMIT;
```

**预期目标端结果**：
```
id=1, val=77；id=2 无残留；全表行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, val) FROM test_contseq_033 WHERE id = 1;   -- 预期：1,77
SELECT COUNT(*) FROM test_contseq_033 WHERE id = 2;                  -- 预期：0
SELECT COUNT(*) FROM test_contseq_033;                               -- 预期：1
```

**风险点**：
- 回环场景 checksum 可能与"两次 PK 变更全部被吞掉"的错误结果碰巧一致，校验必须含行数 + 逐列精确值；
- 若中间态 id=2 的 DELETE/INSERT（relocate 语义）乱序，可能残留 id=2 孤儿行。

---

### Case 34: 单事务内 PK 连续变更 A→B→C + 普通列 UPDATE 交错

**Case ID**：`CONTSEQ_034`  
**名称**：Intra-TX Sequential PK Changes with Interleaved Normal UPDATEs  
**场景描述**：单事务内 PK 连续变更 1→2→3，且每次 PK 变更前后都交错普通列 UPDATE（共 5 条），验证 batch 分段执行的**严格保序**：CASE WHEN 段 → 单条 identity 变更 → CASE WHEN 段 → 单条 identity 变更 → CASE WHEN 段。

**覆盖的代码路径**：
- `flushUpdateBatch`（L823-870）：两处 identity 变更产生多分段
- `flushSingleRowUpdate`（L975-1029）：identity 变更行单条执行
- `flushCaseWhenUpdate`（L878-967）：前后段批量执行 + LinkedHashMap 段内去重

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_034 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_contseq_034 VALUES (1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_034 SET val = 11 WHERE id = 1;  -- 普通列（段1）
  UPDATE test_contseq_034 SET id = 2 WHERE id = 1;    -- ★ PK A→B（切割点1，单条执行）
  UPDATE test_contseq_034 SET val = 22 WHERE id = 2;  -- 普通列（段2）
  UPDATE test_contseq_034 SET id = 3 WHERE id = 2;    -- ★ PK B→C（切割点2，单条执行）
  UPDATE test_contseq_034 SET val = 33 WHERE id = 3;  -- 普通列（段3）
COMMIT;
```

**预期目标端结果**：
```
id=3, val=33；id=1、id=2 无残留；全表行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, val) FROM test_contseq_034 WHERE id = 3;   -- 预期：3,33
SELECT COUNT(*) FROM test_contseq_034 WHERE id IN (1, 2);            -- 预期：0
SELECT COUNT(*) FROM test_contseq_034;                               -- 预期：1
```

**风险点**：
- 分段乱序（后段先执行）会导致 UPDATE miss（WHERE 命中不了尚未变更的 PK）或残留中间 PK；
- 段内 LinkedHashMap 去重不得跨越切割点合并不同 PK 状态下的行。

---

### Case 35: 单事务内两行 PK 互换（借临时值）

**Case ID**：`CONTSEQ_035`  
**名称**：Intra-TX PK Swap via Temporary Value  
**场景描述**：初始只有 id=1,2 两行，单事务内借临时值互换 PK：UPDATE id=1→3；UPDATE id=2→1；UPDATE id=3→2。三条均为 identity 变更，全部拆单条且必须严格保序，否则目标端出现 PK 冲突或互换失败。

**覆盖的代码路径**：
- `flushUpdateBatch`（L823-870）：连续三条 identity 变更行逐条 `flushSingleRowUpdate`（L975-1029）
- `applySupersedeMachine`（L539-615）：每条均触发 `isIdentityOrUkChanged`（L1246-1269）斩链，不得跨行合并
- DAG 建边：before-key/after-key（L1076-1088）——key 1/2/3 在三条记录间交叉引用，保序依赖边密集

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_035 (
  id INT PRIMARY KEY,
  val VARCHAR(20)
) ENGINE=InnoDB;

INSERT INTO test_contseq_035 VALUES (1, 'one'), (2, 'two');
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_035 SET id = 3 WHERE id = 1;  -- ★ 1→3（临时值）
  UPDATE test_contseq_035 SET id = 1 WHERE id = 2;  -- ★ 2→1
  UPDATE test_contseq_035 SET id = 2 WHERE id = 3;  -- ★ 3→2
COMMIT;
```

**预期目标端结果**：
```
id=1, val='two'；id=2, val='one'（两行 val 互换）；id=3 无残留；全表行数=2
```

**验证 SQL**：
```sql
SELECT GROUP_CONCAT(CONCAT('(', id, ',', val, ')') ORDER BY id) FROM test_contseq_035;
-- 预期：(1,two),(2,one)
SELECT COUNT(*) FROM test_contseq_035 WHERE id = 3;   -- 预期：0（临时值无残留）
SELECT COUNT(*) FROM test_contseq_035;                -- 预期：2
```

**风险点**：
- 互换场景 checksum 可能与"完全未执行"碰巧一致（两行集合相同），必须逐行精确比对 id↔val 对应关系；
- 三条单条 UPDATE 若乱序（2→1 先于 1→3），目标端会报 PK 冲突或 UPDATE miss 降级。

---

### Case 36: 分区键跨分片链式移动 1→2→3 + 后续 UPDATE

**Case ID**：`CONTSEQ_036`  
**名称**：Cross-TX Shard Key Chain Move 1→2→3 then UPDATE  
**场景描述**：分区表（PARTITION BY KEY(shard_key) PARTITIONS 4）中同一行的 shard_key 跨事务链式移动 1→2→3（每次跨分片，源端产生 LogicalRelocate），随后 UPDATE 普通列。验证旧分片无残留行。

**覆盖的代码路径**：
- TableKey 含分区键（L1183）：shard_key 变更产生新 TableKey
- `applySupersedeMachine`（L539-615）：`isIdentityOrUkChanged`（L1246-1269）对分区键变更斩链
- `flushUpdateBatch`（L823-870）：分区键变更行拆 `flushSingleRowUpdate`（L975-1029）

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_036 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_contseq_036 VALUES (1, 1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_036 SET shard_key = 2 WHERE id = 1;  -- TX1: 分片 1→2
COMMIT;

BEGIN;
  UPDATE test_contseq_036 SET shard_key = 3 WHERE id = 1;  -- TX2: 分片 2→3
COMMIT;

BEGIN;
  UPDATE test_contseq_036 SET val = 55 WHERE id = 1;       -- TX3: 后续 UPDATE
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=3, val=55；全表 COUNT(*)=1（检测旧分片残留）
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, shard_key, val) FROM test_contseq_036 WHERE id = 1;  -- 预期：1,3,55
SELECT COUNT(*) FROM test_contseq_036;   -- 预期：1（全表扫描覆盖所有分片，检测旧分片孤儿行）
```

**风险点**：
- 目标端 relocate 同步较慢（LogicalRelocate 拆 DELETE+INSERT），wait_sync 超时需放宽至 120s；
- 若 TableKey 未含分区键，不同分片的同 PK 行会被误判为同一链，旧分片可能残留孤儿行（COUNT(*)>1）。

---

### Case 37: 分区键来回震荡 1→2→1 + 后续 UPDATE

**Case ID**：`CONTSEQ_037`  
**名称**：Cross-TX Shard Key Oscillation 1→2→1 then UPDATE  
**场景描述**：shard_key 跨事务来回震荡 1→2→1，随后 UPDATE 普通列。回环使首尾 TableKey 相同（含分区键），验证不被错误折叠、中间分片无残留。

**覆盖的代码路径**：
- TableKey 含分区键（L1183）：震荡导致首尾 key 相同、中间 key 不同
- `applySupersedeMachine`（L539-615）：两次分区键变更（L1246-1269）均斩链
- DAG 建边：before-key/after-key（L1076-1088）回环依赖保序

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_037 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_contseq_037 VALUES (1, 1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_037 SET shard_key = 2 WHERE id = 1;  -- TX1: 分片 1→2
COMMIT;

BEGIN;
  UPDATE test_contseq_037 SET shard_key = 1 WHERE id = 1;  -- TX2: 分片 2→1（震荡回原分片）
COMMIT;

BEGIN;
  UPDATE test_contseq_037 SET val = 66 WHERE id = 1;       -- TX3: 后续 UPDATE
COMMIT;
```

**预期目标端结果**：
```
id=1, shard_key=1, val=66；全表 COUNT(*)=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, shard_key, val) FROM test_contseq_037 WHERE id = 1;  -- 预期：1,1,66
SELECT COUNT(*) FROM test_contseq_037;   -- 预期：1
```

**风险点**：
- 震荡回原分片后 checksum 与"两次变更被吞"碰巧一致，必须验 val=66 确认 TX3 生效；
- 中间分片（shard_key=2）若残留孤儿行，COUNT(*) 会 >1。

---

### Case 38: PK+分区键同时连续变更 (1,1)→(2,2)→(3,3) + 后续 UPDATE

**Case ID**：`CONTSEQ_038`  
**名称**：Cross-TX Simultaneous PK+Shard Key Chain then UPDATE  
**场景描述**：(id, shard_key) 跨事务同时连续变更 (1,1)→(2,2)→(3,3)，每步 PK 与分区键同时改变，随后 UPDATE 普通列。双重 identity 变更叠加跨分片移动，验证旧组合无残留。

**覆盖的代码路径**：
- `isIdentityOrUkChanged`（L1246-1269）：PK 与分区键同时变更的判定
- TableKey 含分区键（L1183）：before/after TableKey 完全不同
- `flushUpdateBatch`（L823-870）+ `flushSingleRowUpdate`（L975-1029）：逐条单条执行

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_038 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_contseq_038 VALUES (1, 1, 10);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_038 SET id = 2, shard_key = 2 WHERE id = 1;  -- TX1: (1,1)→(2,2)
COMMIT;

BEGIN;
  UPDATE test_contseq_038 SET id = 3, shard_key = 3 WHERE id = 2;  -- TX2: (2,2)→(3,3)
COMMIT;

BEGIN;
  UPDATE test_contseq_038 SET val = 88 WHERE id = 3;               -- TX3: 后续 UPDATE
COMMIT;
```

**预期目标端结果**：
```
id=3, shard_key=3, val=88；旧组合 (1,1)、(2,2) 无残留；全表行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, shard_key, val) FROM test_contseq_038 WHERE id = 3;  -- 预期：3,3,88
SELECT COUNT(*) FROM test_contseq_038 WHERE id IN (1, 2);                      -- 预期：0
SELECT COUNT(*) FROM test_contseq_038;                                         -- 预期：1
```

**风险点**：
- PK 与分区键双重变更时，若只按 PK 建 DAG 边而忽略分区键，跨分片顺序可能乱序，旧分片残留孤儿行；
- 双重变更行必须走单条 UPDATE（统一单条策略），不得进 CASE WHEN 批。

---

### Case 39: DELETE 后同 PK INSERT（39a 同事务 / 39b 跨事务）

**Case ID**：`CONTSEQ_039`  
**名称**：DELETE then Re-INSERT Same PK (Intra-TX & Cross-TX Variants)  
**场景描述**：同一 PK 先 DELETE 再 INSERT 新数据，两个变体：39a 同事务内 DELETE+INSERT；39b 跨事务。验证**禁止 DELETE→INSERT 压缩**约束：两条事件必须都执行且保序，新数据生效。

**覆盖的代码路径**：
- `applySupersedeMachine`（L539-615）：DELETE 后 `state=null` 不建链，后续 INSERT 不与 DELETE 压缩合并
- DAG 建边：DELETE 的 before-key 与 INSERT 的 after-key 相同（L1076-1088），保证 DELETE 先于 INSERT 执行

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_039a (
  id INT PRIMARY KEY,
  val VARCHAR(20)
) ENGINE=InnoDB;

CREATE TABLE test_contseq_039b (
  id INT PRIMARY KEY,
  val VARCHAR(20)
) ENGINE=InnoDB;

INSERT INTO test_contseq_039a VALUES (1, 'old_a');
INSERT INTO test_contseq_039b VALUES (1, 'old_b');
```

**源端执行 SQL 序列**：
```sql
-- 39a: 同事务内 DELETE + INSERT（同 PK 新数据）
BEGIN;
  DELETE FROM test_contseq_039a WHERE id = 1;
  INSERT INTO test_contseq_039a VALUES (1, 'new_a');
COMMIT;

-- 39b: 跨事务 DELETE、INSERT
BEGIN;
  DELETE FROM test_contseq_039b WHERE id = 1;
COMMIT;

BEGIN;
  INSERT INTO test_contseq_039b VALUES (1, 'new_b');
COMMIT;
```

**预期目标端结果**：
```
39a: id=1, val='new_a'；行数=1
39b: id=1, val='new_b'；行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, val) FROM test_contseq_039a WHERE id = 1;  -- 预期：1,new_a
SELECT COUNT(*) FROM test_contseq_039a;                              -- 预期：1
SELECT CONCAT_WS(',', id, val) FROM test_contseq_039b WHERE id = 1;  -- 预期：1,new_b
SELECT COUNT(*) FROM test_contseq_039b;                              -- 预期：1
```

**风险点**：
- 若 DELETE→INSERT 被错误压缩为 UPDATE 或互相抵消，新数据丢失（残留旧值或行不存在）；
- INSERT 先于 DELETE 执行会报 Duplicate Entry，在 OVERWRITE 策略下可能掩盖乱序，需观察日志确认。

---

### Case 40: 多行 DELETE + 批量 INSERT 部分 PK 复用

**Case ID**：`CONTSEQ_040`  
**名称**：Multi-Row DELETE then Batch INSERT with Partial PK Reuse  
**场景描述**：初始 id=1,2,3 三行；DELETE id IN (1,2)，随后批量 INSERT (1,新值),(4,新值)——id=1 复用被删 PK，id=4 全新，id=2 不复用。验证部分 PK 复用时 DELETE/INSERT 不串扰。

**覆盖的代码路径**：
- `applySupersedeMachine`（L539-615）：id=1 的 DELETE 后 `state=null` 不建链，新 INSERT 独立处理
- DAG 建边（L1076-1088）：仅 id=1 存在 DELETE→INSERT 依赖边，id=4 无依赖可并行
- batch INSERT 同主键去重路径：复用 PK 的 INSERT 不得被去重误删

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_040 (
  id INT PRIMARY KEY,
  val VARCHAR(20)
) ENGINE=InnoDB;

INSERT INTO test_contseq_040 VALUES (1, 'orig1'), (2, 'orig2'), (3, 'orig3');
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  DELETE FROM test_contseq_040 WHERE id IN (1, 2);          -- 删 2 行
COMMIT;

BEGIN;
  INSERT INTO test_contseq_040 VALUES (1, 'new1'), (4, 'new4');  -- id=1 复用，id=4 全新
COMMIT;
```

**预期目标端结果**：
```
(1,'new1'), (3,'orig3'), (4,'new4')；id=2 不存在；全表行数=3
```

**验证 SQL**：
```sql
SELECT GROUP_CONCAT(CONCAT('(', id, ',', val, ')') ORDER BY id) FROM test_contseq_040;
-- 预期：(1,new1),(3,orig3),(4,new4)
SELECT COUNT(*) FROM test_contseq_040 WHERE id = 2;   -- 预期：0
SELECT COUNT(*) FROM test_contseq_040;                -- 预期：3
```

**风险点**：
- 若 id=1 的 DELETE 与 INSERT 乱序，新值 'new1' 丢失或报 Duplicate Entry；
- 若 DELETE→INSERT 被压缩，id=2 可能残留或 id=1 旧值未替换。

---

### Case 41: 单事务内多段切割（5 普通 + PK 变更 + 5 普通 + 分区键变更 + 5 普通）

**Case ID**：`CONTSEQ_041`  
**名称**：Intra-TX Multi-Segment Split (2 Cut Points, 17 Statements)  
**场景描述**：单事务内共 17 条 UPDATE：5 条普通 UPDATE（id=10..14）→ 1 条 PK 变更（id=10→20）→ 5 条普通 UPDATE → 1 条分区键变更（id=11 的 shard_key 1→2）→ 5 条普通 UPDATE。现有 Case 20 仅单切割点，本 case 验证**多分段点**：三个 CASE WHEN 段 + 两个单条段严格保序。

**覆盖的代码路径**：
- `flushUpdateBatch`（L823-870）：两个切割点产生 5 个执行段
- `flushSingleRowUpdate`（L975-1029）：PK 变更行与分区键变更行各自单条执行
- `flushCaseWhenUpdate`（L878-967）：三个前/中/后段批量执行，LinkedHashMap 段内去重
- TableKey 含分区键（L1183）

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_041 (
  id INT PRIMARY KEY,
  shard_key INT,
  val INT
) ENGINE=InnoDB PARTITION BY KEY(shard_key) PARTITIONS 4;

INSERT INTO test_contseq_041 VALUES
  (10, 1, 0), (11, 1, 0), (12, 1, 0), (13, 1, 0), (14, 1, 0);
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  -- 段1：5 条普通 UPDATE
  UPDATE test_contseq_041 SET val = 101 WHERE id = 10;
  UPDATE test_contseq_041 SET val = 111 WHERE id = 11;
  UPDATE test_contseq_041 SET val = 121 WHERE id = 12;
  UPDATE test_contseq_041 SET val = 131 WHERE id = 13;
  UPDATE test_contseq_041 SET val = 141 WHERE id = 14;
  -- 切割点1：PK 变更
  UPDATE test_contseq_041 SET id = 20 WHERE id = 10;          -- ★ PK 10→20
  -- 段2：5 条普通 UPDATE
  UPDATE test_contseq_041 SET val = 202 WHERE id = 20;
  UPDATE test_contseq_041 SET val = 112 WHERE id = 11;
  UPDATE test_contseq_041 SET val = 122 WHERE id = 12;
  UPDATE test_contseq_041 SET val = 132 WHERE id = 13;
  UPDATE test_contseq_041 SET val = 142 WHERE id = 14;
  -- 切割点2：分区键变更
  UPDATE test_contseq_041 SET shard_key = 2 WHERE id = 11;    -- ★ shard_key 1→2
  -- 段3：5 条普通 UPDATE
  UPDATE test_contseq_041 SET val = 113 WHERE id = 11;
  UPDATE test_contseq_041 SET val = 123 WHERE id = 12;
  UPDATE test_contseq_041 SET val = 133 WHERE id = 13;
  UPDATE test_contseq_041 SET val = 143 WHERE id = 14;
  UPDATE test_contseq_041 SET val = 203 WHERE id = 20;
COMMIT;
```

**预期目标端结果**：
```
(11,2,113), (12,1,123), (13,1,133), (14,1,143), (20,1,203)；id=10 无残留；全表行数=5
```

**验证 SQL**：
```sql
SELECT GROUP_CONCAT(CONCAT('(', id, ',', shard_key, ',', val, ')') ORDER BY id) FROM test_contseq_041;
-- 预期：(11,2,113),(12,1,123),(13,1,133),(14,1,143),(20,1,203)
SELECT COUNT(*) FROM test_contseq_041 WHERE id = 10;  -- 预期：0
SELECT COUNT(*) FROM test_contseq_041;                -- 预期：5
```

**风险点**：
- 多分段场景下若段间乱序（如段3 先于切割点2 执行），id=11/id=20 的终态会错；
- 段内 LinkedHashMap 去重（如段3 内同 PK 多次 UPDATE）与多段交互不得串段；
- 分区键变更行（id=11）若未拆单条，旧分片可能残留。

---

### Case 42: 100 个事务连续 UPDATE，第 50 个为 PK 变更（跨 window 边界）

**Case ID**：`CONTSEQ_042`  
**名称**：100-TX Hot Chain with PK Change at TX#50, Crossing MAX_COMPACT_TRANSACTIONS=64  
**场景描述**：100 个事务对同一逻辑行连续 UPDATE（每 TX 改 val 为递增值 1..100）：第 1-49 个 TX 在 id=1 上 UPDATE val；第 50 个 TX 为 PK 变更 1→2（同时 val=50）；第 51-100 个 TX 在 id=2 上继续 UPDATE val。链长跨 MAX_COMPACT_TRANSACTIONS=64 window 边界，且 PK 变更点落在第一个 window 内。验证 window 截断 + 斩链叠加时终态正确。

**覆盖的代码路径**：
- `applySupersedeMachine`（L539-615）：长链 supersede + 第 50 个 TX 处 `isIdentityOrUkChanged`（L1246-1269）斩链
- window 边界：MAX_COMPACT_TRANSACTIONS=64 截断，链尾部分落入下一 window
- DAG 建边（L1076-1088）：跨 window 的同 key 事务保序

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_042 (
  id INT PRIMARY KEY,
  val INT
) ENGINE=InnoDB;

INSERT INTO test_contseq_042 VALUES (1, 0);
```

**源端执行 SQL 序列**（脚本用 shell 循环生成，每条独立事务）：
```sql
-- TX1..TX49：在 id=1 上递增 UPDATE
BEGIN; UPDATE test_contseq_042 SET val = 1 WHERE id = 1; COMMIT;
BEGIN; UPDATE test_contseq_042 SET val = 2 WHERE id = 1; COMMIT;
-- ...（省略 3..48，同构）
BEGIN; UPDATE test_contseq_042 SET val = 49 WHERE id = 1; COMMIT;

-- TX50：PK 变更 1→2（同时 val=50）
BEGIN; UPDATE test_contseq_042 SET id = 2, val = 50 WHERE id = 1; COMMIT;

-- TX51..TX100：在 id=2 上递增 UPDATE
BEGIN; UPDATE test_contseq_042 SET val = 51 WHERE id = 2; COMMIT;
-- ...（省略 52..99，同构）
BEGIN; UPDATE test_contseq_042 SET val = 100 WHERE id = 2; COMMIT;
```

**预期目标端结果**：
```
id=2, val=100（第 100 个 TX 的值）；id=1 无残留；全表行数=1
```

**验证 SQL**：
```sql
SELECT CONCAT_WS(',', id, val) FROM test_contseq_042 WHERE id = 2;   -- 预期：2,100
SELECT COUNT(*) FROM test_contseq_042 WHERE id = 1;                  -- 预期：0
SELECT COUNT(*) FROM test_contseq_042;                               -- 预期：1
```

**风险点**：
- 本 case 是 Case 15（TX 截断）与 Case 12（PK 变更斩链）的叠加：window 截断点与斩链点可能落在同一/相邻 window，若被截断节点的后续独立 apply 丢失，终态停在中间值（如 val=64）；
- 实际 window 切分受到达节奏影响，PK 变更点不一定正好在边界上，但 100 TX 必然跨至少 2 个 window。

---

### Case 43: PK 释放-复用交错（UPDATE 释放旧 PK 后 INSERT 复用）

**Case ID**：`CONTSEQ_043`  
**名称**：PK Release-Reuse Interleaving (UPDATE Frees PK, INSERT Reuses It)  
**场景描述**：初始 id=1；TX1: UPDATE id 1→100（释放 PK 1）；TX2: INSERT id=1 新行；TX3: UPDATE id=1 的 val。验证 DAG 以 before-key 建边保序：TX2 的 INSERT(key=1) 必须排在 TX1 的 UPDATE(before-key=1) 之后，TX3 排在 TX2 之后。

**覆盖的代码路径**：
- DAG 建边：UPDATE 同时提取 before-key/after-key（L1076-1088）——TX1 的 before-key=1 与 TX2 INSERT 的 key=1 建依赖边
- `applySupersedeMachine`（L539-615）：TX1 斩链后，TX2/TX3 在 key=1 上形成新链，不得与旧链合并
- `isIdentityOrUkChanged`（L1246-1269）

**前置建表 DDL**：
```sql
CREATE TABLE test_contseq_043 (
  id INT PRIMARY KEY,
  val VARCHAR(20)
) ENGINE=InnoDB;

INSERT INTO test_contseq_043 VALUES (1, 'origin');
```

**源端执行 SQL 序列**：
```sql
BEGIN;
  UPDATE test_contseq_043 SET id = 100 WHERE id = 1;      -- TX1: 释放 PK 1
COMMIT;

BEGIN;
  INSERT INTO test_contseq_043 VALUES (1, 'reborn');       -- TX2: 复用 PK 1
COMMIT;

BEGIN;
  UPDATE test_contseq_043 SET val = 'reborn_v2' WHERE id = 1;  -- TX3: 新行上 UPDATE
COMMIT;
```

**预期目标端结果**：
```
(1,'reborn_v2'), (100,'origin')；全表行数=2
```

**验证 SQL**：
```sql
SELECT GROUP_CONCAT(CONCAT('(', id, ',', val, ')') ORDER BY id) FROM test_contseq_043;
-- 预期：(1,reborn_v2),(100,origin)
SELECT COUNT(*) FROM test_contseq_043;   -- 预期：2
```

**风险点**：
- 若 TX2 的 INSERT 未与 TX1 的 before-key 建边而先执行，目标端报 Duplicate Entry，OVERWRITE 策略下可能静默覆盖导致 id=100 行丢失（行数=1）；
- TX3 若与 TX1 的旧链误合并，'reborn_v2' 可能落到 id=100 行上。

---

### 附加项（FAULT-INJ，默认不执行）：目标端预删行制造 UPDATE miss 验证整批降级

**说明**：在执行某连续变更 case 前，手工在**目标端**预先 DELETE 掉待更新行（如 `DELETE FROM test_contseq_032 WHERE id = 1;`），再在源端执行 UPDATE 序列，制造批量 UPDATE affected rows 不符，验证 `flushCaseWhenUpdate` 的整批降级单条重执路径（L962-966）。

**注意**：此项需人为写目标端，违反"源端写入/目标端只读"的测试纪律，**默认不执行、不纳入脚本**，仅在需要专项验证降级路径时手动按需执行，执行后需重建目标端数据再跑常规 case。预期行为：降级后逐条重执，miss 行按 conflictStrategy=OVERWRITE 补齐，日志可见降级记录。

---

### 执行结果记录（2026-07-29）

**执行环境**：购买实例，源 `pxc-hzs404x0wbd1mx.polarx.rds.aliyuncs.com:3306` → 目标 `pxc-hzrpuyen2ru1bz.polarx.rds.aliyuncs.com:3306`，版本 `5.7.25-TDDL-5.4.21-20260629`（与 Case 1-31 同一环境）。

**执行脚本**：`.rc/contseq1.sh`（Case 32-35）、`.rc/contseq2.sh`（Case 36-40）、`.rc/contseq3.sh`（Case 41-43）。

**结果**：Case 32-43 共 12 个 case（含 39a/39b 两变体）、34 项断言**全部 PASS**。

| 分组 | 覆盖 Case | PASS | FAIL |
|------|-----------|:----:|:----:|
| contseq1.sh | 32-35 | 12 | 0 |
| contseq2.sh | 36-40 | 14 | 0 |
| contseq3.sh | 41-43 | 8 | 0 |

**说明**：
- 各 case 终态、残留检查、行数校验均符合预期；
- Case 36-38（LogicalRelocate）与 Case 42（100 事务长链）均在 wait_sync 内追平（behind 0~1s）；
- 完整输出留存于 `.rc/contseq1.sh.out` ~ `.rc/contseq3.sh.out`。

---

## 测试执行指南

### 执行流程

1. **准备测试环境**
   - 源端：创建所有 case 的表结构
   - 目标端：保持为空（CDC 会同步创建）

2. **逐 case 执行**
   - 执行源端 SQL 序列
   - 等待 CDC 同步完成（监控 RECEIVE_DELAY ≤ 5s）
   - 运行验证 SQL，对比预期结果

3. **记录结果**
   - 成功：PASS
   - 失败：记录差异数据，启用 compaction 调试日志重跑

4. **汇总报告**
   - 统计通过率
   - 列举失败 case 和差异数据
   - 确认修复有效性

### 关键指标

| 指标 | 预期值 |
|------|--------|
| Case 通过率 | ≥ 95% |
| **本次 bug 变体（Case 1, 2, 6, 14, 15, 29, 30）** | **100% PASS** |
| 目标端数据一致性 | 100% 与源端一致 |
| 无数据丢失 | 0 行丢失 |

### 连续变更 case（32-43）注意事项

1. **执行前确认开关与策略**：compaction=true、batch=true、conflictStrategy=OVERWRITE，三者缺一则未覆盖目标代码路径，结果不具参考意义。
2. **wait_sync 超时放宽**：Case 36-38 涉及 LogicalRelocate（分区键变更拆 DELETE+INSERT 跨分片），目标端同步较慢，wait_sync 超时放宽至 120s（对应脚本 contseq2.sh/contseq3.sh 已默认 120s）。
3. **观察 CDC 日志**：执行期间在目标端 CDC 日志中搜索 `[Dedup] TRIGGERED`，确认去重/分段逻辑实际被触发（未触发说明 case 未命中 compaction 路径，需检查链路配置）。
4. **校验纪律**：不能只看 checksum，必须含行数 + 逐列精确值双重校验——回环（Case 33/37）与互换（Case 35）场景下，错误结果的 checksum 可能与正确结果碰巧一致。
5. **执行脚本**：`.rc/contseq1.sh`（Case 32-35）、`.rc/contseq2.sh`（Case 36-40）、`.rc/contseq3.sh`（Case 41-43）。

---

## 附录：Case 矩阵速查

| Case ID | 名称 | Intra-TX | Inter-TX | Window | Batch | Concur | Special | Combo | Bug变体 |
|---------|------|:--------:|:--------:|:------:|:-----:|:------:|:-------:|:-----:|:------:|
| 1 | 2 UPDATEs | ✓ | - | - | - | - | - | - | **Yes** |
| 2 | 5 UPDATEs | ✓ | - | - | - | - | - | - | **Yes** |
| 3 | INSERT+UPDATE | ✓ | - | - | - | - | - | - | **Yes** |
| 4 | UPDATE+DELETE | ✓ | - | - | - | - | - | - | **Yes** |
| 5 | Full Lifecycle | ✓ | - | - | - | - | - | - | **Yes** |
| 6 | Mixed Rows | ✓ | - | - | - | - | - | - | **Yes** |
| 7 | 2 TXs UPDATE | - | ✓ | - | - | - | - | - | No |
| 8 | 5 TXs Chain | - | ✓ | - | - | - | - | - | No |
| 9 | TX INSERT→UPDATE | - | ✓ | - | - | - | - | - | No |
| 10 | TX UPDATE→DELETE | - | ✓ | - | - | - | - | - | No |
| 11 | 3 TX Full Chain | - | ✓ | - | - | - | - | - | No |
| 12 | PK Change | - | ✓ | - | - | - | - | - | No |
| 13 | Multi Chains | - | ✓ | - | - | - | - | - | No |
| 14 | EVENT Truncate | - | - | ✓ | - | - | - | - | **Yes** |
| 15 | TX Truncate | - | - | ✓ | - | - | - | - | **Yes** |
| 16 | Large Single TX | - | - | ✓ | - | - | - | - | No |
| 17 | No Hot Chains | - | - | ✓ | - | - | - | - | No |
| 18 | batch_size=50 | - | - | - | ✓ | - | - | - | No |
| 19 | batch_size+1 | - | - | - | ✓ | - | - | - | No |
| 20 | Identity Split | - | - | - | ✓ | - | - | - | No |
| 21 | forceAllColumns | - | - | - | ✓ | - | - | - | No |
| 22 | Independent TX | - | - | - | - | ✓ | - | - | No |
| 23 | Dependent TX | - | - | - | - | ✓ | - | - | No |
| 24 | Consumed Node | - | - | - | - | ✓ | - | - | No |
| 25 | NULL Identity | - | - | - | - | - | ✓ | - | No |
| 26 | Large Column | - | - | - | - | - | ✓ | - | No |
| 27 | Composite PK | - | - | - | - | - | ✓ | - | No |
| 28 | No PK Table | - | - | - | - | - | ✓ | - | No |
| 29 | Combo Intra+Inter | ✓ | ✓ | - | - | - | - | ✓ | **Yes** |
| 30 | Multi-Table Dup | - | - | - | - | - | - | ✓ | **Yes** |
| 31 | DDL Barrier | - | - | - | - | - | - | ✓ | No |
| 32 | PK Chain A→B→C | - | ✓ | - | ✓ | - | - | - | No |
| 33 | PK Loop A→B→A | - | ✓ | - | - | ✓ | - | - | No |
| 34 | Intra-TX PK Chain | ✓ | - | - | ✓ | - | - | - | No |
| 35 | PK Swap via Temp | ✓ | - | - | ✓ | ✓ | - | - | No |
| 36 | Shard Chain 1→2→3 | - | ✓ | - | ✓ | - | ✓ | - | No |
| 37 | Shard Oscillation | - | ✓ | - | - | ✓ | ✓ | - | No |
| 38 | PK+Shard Chain | - | ✓ | - | ✓ | - | ✓ | - | No |
| 39 | DELETE→Re-INSERT | ✓ | ✓ | - | - | - | - | - | No |
| 40 | Multi-DEL + PK Reuse | - | ✓ | - | ✓ | - | - | - | No |
| 41 | Multi-Segment Split | ✓ | - | - | ✓ | - | - | ✓ | No |
| 42 | 100-TX + PK@50 | - | ✓ | ✓ | - | - | - | ✓ | No |
| 43 | PK Release-Reuse | - | ✓ | - | - | ✓ | - | - | No |

**Case 32-43 执行结果**：全部 PASS（2026-07-29，购买实例环境，详见[第八章执行结果记录](#执行结果记录2026-07-29)）。

**重点验证集（必须通过）**：Cases 1, 2, 6, 14, 15, 29, 30

