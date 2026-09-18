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
import org.junit.Test;
import org.mockito.Mockito;

public class RowBatchTest extends BaseTest {

    @Test
    public void testConstructor() {
        RowBatch rowBatch = new RowBatch();

        Assert.assertNotNull(rowBatch.getRowChanges());
        Assert.assertTrue(rowBatch.getRowChanges().isEmpty());
        Assert.assertEquals(0, rowBatch.size());
        Assert.assertFalse(rowBatch.canMerge());
    }

    @Test
    public void testAddWithInsertAction() {
        RowBatch rowBatch = new RowBatch();

        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);

        rowBatch.add(rowChange);

        Assert.assertEquals(1, rowBatch.size());
        Assert.assertEquals(1, rowBatch.getRowChanges().size());
        Assert.assertEquals(rowChange, rowBatch.getRowChanges().get(0));
        Assert.assertFalse(rowBatch.canMerge()); // 因为size=1，不能合并
    }

    @Test
    public void testAddWithMultipleInsertActions() {
        RowBatch rowBatch = new RowBatch();

        DefaultRowChange rowChange1 = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange1.getAction()).thenReturn(DBMSAction.INSERT);

        DefaultRowChange rowChange2 = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange2.getAction()).thenReturn(DBMSAction.INSERT);

        rowBatch.add(rowChange1);
        rowBatch.add(rowChange2);

        Assert.assertEquals(2, rowBatch.size());
        Assert.assertEquals(2, rowBatch.getRowChanges().size());
        Assert.assertEquals(rowChange1, rowBatch.getRowChanges().get(0));
        Assert.assertEquals(rowChange2, rowBatch.getRowChanges().get(1));
        Assert.assertTrue(rowBatch.canMerge()); // 因为size>1且没有UPDATE操作
    }

    @Test
    public void testAddWithUpdateAction() {
        RowBatch rowBatch = new RowBatch();

        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);

        DefaultRowChange updateRowChange = Mockito.mock(DefaultRowChange.class);
        Mockito.when(updateRowChange.getAction()).thenReturn(DBMSAction.UPDATE);

        rowBatch.add(rowChange);
        rowBatch.add(updateRowChange);

        Assert.assertEquals(2, rowBatch.size());
        Assert.assertEquals(2, rowBatch.getRowChanges().size());
        Assert.assertEquals(rowChange, rowBatch.getRowChanges().get(0));
        Assert.assertEquals(updateRowChange, rowBatch.getRowChanges().get(1));
        Assert.assertFalse(rowBatch.canMerge()); // 因为有UPDATE操作
    }

    @Test
    public void testCanMergeWithSingleInsert() {
        RowBatch rowBatch = new RowBatch();

        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange.getAction()).thenReturn(DBMSAction.INSERT);

        rowBatch.add(rowChange);

        Assert.assertFalse(rowBatch.canMerge()); // 因为size=1，不能合并
    }

    @Test
    public void testCanMergeWithSingleUpdate() {
        RowBatch rowBatch = new RowBatch();

        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange.getAction()).thenReturn(DBMSAction.UPDATE);

        rowBatch.add(rowChange);

        Assert.assertFalse(rowBatch.canMerge()); // 因为size=1，不能合并，即使有UPDATE操作
    }

    @Test
    public void testSize() {
        RowBatch rowBatch = new RowBatch();

        Assert.assertEquals(0, rowBatch.size());

        DefaultRowChange rowChange1 = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange1.getAction()).thenReturn(DBMSAction.INSERT);

        DefaultRowChange rowChange2 = Mockito.mock(DefaultRowChange.class);
        Mockito.when(rowChange2.getAction()).thenReturn(DBMSAction.DELETE);

        rowBatch.add(rowChange1);
        Assert.assertEquals(1, rowBatch.size());

        rowBatch.add(rowChange2);
        Assert.assertEquals(2, rowBatch.size());
    }
}
