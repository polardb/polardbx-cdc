/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StagingRowQueueTest extends BaseTest {

    private StagingRowQueue stagingRowQueue;
    private String testTableName = "test_db.test_table";
    private RowKey rowKey;

    @Before
    public void setUp() {
        stagingRowQueue = new StagingRowQueue();
        // 创建一个DefaultRowChange用于构造RowKey
        DefaultRowChange rowChange = createRowChange(DBMSAction.INSERT);
        rowChange.setSchema("test_db");
        rowChange.setTable("test_table");

        // 创建一个空的keys map用于构造RowKey
        Map<Integer, Serializable> keys = new HashMap<>();
        keys.put(1, "test_key");

        rowKey = new RowKey(rowChange, keys);
    }

    @Test
    public void testAddAllWithSingleInsert() {
        // 创建一个INSERT类型的DefaultRowChange
        DefaultRowChange insertChange = createRowChange(DBMSAction.INSERT);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(insertChange);

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证插入到了pureInsertRowChanges中
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().get(testTableName).size());
        Assert.assertEquals(insertChange, stagingRowQueue.getPureInsertRowChanges().get(testTableName).get(0));
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChangeSize());
        Assert.assertEquals(0, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testAddAllWithSingleDelete() {
        // 创建一个DELETE类型的DefaultRowChange
        DefaultRowChange deleteChange = createRowChange(DBMSAction.DELETE);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(deleteChange);

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证插入到了pureDeleteRowChanges中
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChanges().get(testTableName).size());
        Assert.assertEquals(deleteChange, stagingRowQueue.getPureDeleteRowChanges().get(testTableName).get(0));
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChangeSize());
        Assert.assertEquals(0, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testAddAllWithUpdate() {
        // 创建一个UPDATE类型的DefaultRowChange（不是INSERT或DELETE）
        DefaultRowChange updateChange = createRowChange(DBMSAction.UPDATE);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(updateChange);

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证插入到了holdingRowChanges中
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().containsKey(testTableName));
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().get(testTableName).size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().get(testTableName).containsKey(rowKey));
        Assert.assertEquals(updateChange, stagingRowQueue.getHoldingRowChanges().get(testTableName).get(rowKey).get(0));
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChangeSize());
        Assert.assertEquals(0, stagingRowQueue.getPureInsertRowChangeSize());
        Assert.assertEquals(0, stagingRowQueue.getPureDeleteRowChangeSize());
    }

    @Test
    public void testAddAllWithMultipleChanges() {
        // 创建多个DefaultRowChange
        DefaultRowChange change1 = createRowChange(DBMSAction.UPDATE);
        DefaultRowChange change2 = createRowChange(DBMSAction.INSERT);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(change1);
        changes.add(change2);

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证插入到了holdingRowChanges中
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().containsKey(testTableName));
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().get(testTableName).size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().get(testTableName).containsKey(rowKey));
        Assert.assertEquals(2, stagingRowQueue.getHoldingRowChanges().get(testTableName).get(rowKey).size());
        Assert.assertEquals(2, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testAddAllWithEmptyList() {
        // 测试空列表的情况
        List<DefaultRowChange> changes = new ArrayList<>();

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证holdingRowChanges中添加了一个空列表
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().containsKey(testTableName));
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().get(testTableName).size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().get(testTableName).containsKey(rowKey));
        Assert.assertEquals(0, stagingRowQueue.getHoldingRowChanges().get(testTableName).get(rowKey).size());
        Assert.assertEquals(0, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testClearPureInsertRowChanges() {
        // 先添加一些插入数据
        DefaultRowChange insertChange = createRowChange(DBMSAction.INSERT);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(insertChange);
        stagingRowQueue.addAll(testTableName, rowKey, changes);

        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChangeSize());

        // 清除纯插入行更改
        stagingRowQueue.clearPureInsertRowChanges();

        // 验证清除结果
        Assert.assertNull(stagingRowQueue.getPureInsertRowChanges());
        Assert.assertEquals(0, stagingRowQueue.getPureInsertRowChangeSize());
    }

    @Test
    public void testClearPureDeleteRowChanges() {
        // 先添加一些删除数据
        DefaultRowChange deleteChange = createRowChange(DBMSAction.DELETE);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(deleteChange);
        stagingRowQueue.addAll(testTableName, rowKey, changes);

        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChangeSize());

        // 清除纯删除行更改
        stagingRowQueue.clearPureDeleteRowChanges();

        // 验证清除结果
        Assert.assertNull(stagingRowQueue.getPureDeleteRowChanges());
        Assert.assertEquals(0, stagingRowQueue.getPureDeleteRowChangeSize());
    }

    @Test
    public void testClearHoldingRowChanges() {
        // 先添加一些持有数据
        DefaultRowChange updateChange = createRowChange(DBMSAction.UPDATE);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(updateChange);
        stagingRowQueue.addAll(testTableName, rowKey, changes);

        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChangeSize());

        // 清除持有行更改
        stagingRowQueue.clearHoldingRowChanges();

        // 验证清除结果
        Assert.assertNull(stagingRowQueue.getHoldingRowChanges());
        Assert.assertEquals(0, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testIsInsertOrDeleteWithInsert() {
        DefaultRowChange insertChange = createRowChange(DBMSAction.INSERT);
        Assert.assertTrue(stagingRowQueue.isInsertOrDelete(insertChange));
    }

    @Test
    public void testIsInsertOrDeleteWithDelete() {
        DefaultRowChange deleteChange = createRowChange(DBMSAction.DELETE);
        Assert.assertTrue(stagingRowQueue.isInsertOrDelete(deleteChange));
    }

    @Test
    public void testIsInsertOrDeleteWithOtherAction() {
        DefaultRowChange updateChange = createRowChange(DBMSAction.UPDATE);
        Assert.assertFalse(stagingRowQueue.isInsertOrDelete(updateChange));

        DefaultRowChange replaceChange = createRowChange(DBMSAction.REPLACE);
        Assert.assertFalse(stagingRowQueue.isInsertOrDelete(replaceChange));
    }

    @Test
    public void testAddInsertOrDeleteWithInsert() {
        DefaultRowChange insertChange = createRowChange(DBMSAction.INSERT);
        stagingRowQueue.addInsertOrDelete(testTableName, insertChange);

        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().get(testTableName).size());
        Assert.assertEquals(insertChange, stagingRowQueue.getPureInsertRowChanges().get(testTableName).get(0));
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChangeSize());
    }

    @Test
    public void testAddInsertOrDeleteWithDelete() {
        DefaultRowChange deleteChange = createRowChange(DBMSAction.DELETE);
        stagingRowQueue.addInsertOrDelete(testTableName, deleteChange);

        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChanges().get(testTableName).size());
        Assert.assertEquals(deleteChange, stagingRowQueue.getPureDeleteRowChanges().get(testTableName).get(0));
        Assert.assertEquals(1, stagingRowQueue.getPureDeleteRowChangeSize());
    }

    @Test
    public void testAddAllWithMixedActions() {
        // 测试多个不同类型的操作组合
        DefaultRowChange insertChange = createRowChange(DBMSAction.INSERT);
        DefaultRowChange updateChange = createRowChange(DBMSAction.UPDATE);
        List<DefaultRowChange> changes = new ArrayList<>();
        changes.add(insertChange);
        changes.add(updateChange);

        stagingRowQueue.addAll(testTableName, rowKey, changes);

        // 验证插入到了holdingRowChanges中（因为不是单一INSERT或DELETE）
        Assert.assertEquals(0, stagingRowQueue.getPureInsertRowChangeSize());
        Assert.assertEquals(0, stagingRowQueue.getPureDeleteRowChangeSize());
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().containsKey(testTableName));
        Assert.assertEquals(1, stagingRowQueue.getHoldingRowChanges().get(testTableName).size());
        Assert.assertTrue(stagingRowQueue.getHoldingRowChanges().get(testTableName).containsKey(rowKey));
        Assert.assertEquals(2, stagingRowQueue.getHoldingRowChanges().get(testTableName).get(rowKey).size());
        Assert.assertEquals(2, stagingRowQueue.getHoldingRowChangeSize());
    }

    @Test
    public void testMultipleTablesOperations() {
        // 测试多个表的操作
        String anotherTable = "another_db.another_table";

        // 创建另一个RowKey
        DefaultRowChange anotherRowChange = createRowChange(DBMSAction.INSERT);
        anotherRowChange.setSchema("another_db");
        anotherRowChange.setTable("another_table");
        Map<Integer, Serializable> anotherKeys = new HashMap<>();
        anotherKeys.put(1, "another_key");
        RowKey anotherRowKey = new RowKey(anotherRowChange, anotherKeys);

        DefaultRowChange insertChange1 = createRowChange(DBMSAction.INSERT);
        DefaultRowChange insertChange2 = createRowChange(DBMSAction.INSERT);

        List<DefaultRowChange> changes1 = new ArrayList<>();
        changes1.add(insertChange1);
        List<DefaultRowChange> changes2 = new ArrayList<>();
        changes2.add(insertChange2);

        stagingRowQueue.addAll(testTableName, rowKey, changes1);
        stagingRowQueue.addAll(anotherTable, anotherRowKey, changes2);

        // 验证两个表都有各自的插入操作
        Assert.assertEquals(2, stagingRowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().get(testTableName).size());
        Assert.assertEquals(1, stagingRowQueue.getPureInsertRowChanges().get(anotherTable).size());
        Assert.assertEquals(2, stagingRowQueue.getPureInsertRowChangeSize());
    }

    /**
     * 辅助方法：创建一个DefaultRowChange对象
     */
    private DefaultRowChange createRowChange(DBMSAction action) {
        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(action);
        return rowChange;
    }
}