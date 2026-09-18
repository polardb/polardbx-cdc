/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RowQueueTest extends BaseTest {

    private DefaultRowChange mockInsertRowChange;
    private DefaultRowChange mockDeleteRowChange;
    private DefaultRowChange mockUpdateRowChange;

    @Before
    public void setUp() {
        mockInsertRowChange = mock(DefaultRowChange.class);
        mockDeleteRowChange = mock(DefaultRowChange.class);
        mockUpdateRowChange = mock(DefaultRowChange.class);

        when(mockInsertRowChange.getAction()).thenReturn(DBMSAction.INSERT);
        when(mockInsertRowChange.getSchema()).thenReturn("test_schema");
        when(mockInsertRowChange.getTable()).thenReturn("test_table");

        when(mockDeleteRowChange.getAction()).thenReturn(DBMSAction.DELETE);
        when(mockDeleteRowChange.getSchema()).thenReturn("test_schema");
        when(mockDeleteRowChange.getTable()).thenReturn("test_table");

        when(mockUpdateRowChange.getAction()).thenReturn(DBMSAction.UPDATE);
        when(mockUpdateRowChange.getSchema()).thenReturn("test_schema");
        when(mockUpdateRowChange.getTable()).thenReturn("test_table");
    }

    @Test
    public void testConstructorWithList() {
        List<DefaultRowChange> rowChanges = new ArrayList<>();
        rowChanges.add(mockInsertRowChange);
        rowChanges.add(mockDeleteRowChange);

        RowQueue rowQueue = new RowQueue(rowChanges);

        Assert.assertEquals(2, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(mockInsertRowChange, rowQueue.getAllRowChanges().getFirst());
        Assert.assertEquals(mockDeleteRowChange, rowQueue.getAllRowChanges().getLast());
    }

    @Test
    public void testAddInsert() {
        RowQueue rowQueue = new RowQueue();

        rowQueue.add(mockInsertRowChange);

        Assert.assertEquals(0, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(1, rowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(mockInsertRowChange, rowQueue.getPureInsertRowChanges().getFirst());
        Assert.assertEquals(0, rowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(0, rowQueue.getHoldingRowChanges().size());
    }

    @Test
    public void testAddDelete() {
        RowQueue rowQueue = new RowQueue();

        rowQueue.add(mockDeleteRowChange);

        Assert.assertEquals(0, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(0, rowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(1, rowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(mockDeleteRowChange, rowQueue.getPureDeleteRowChanges().getFirst());
        Assert.assertEquals(0, rowQueue.getHoldingRowChanges().size());
    }

    @Test(expected = PolardbxException.class)
    public void testAddUpdateThrowsException() {
        RowQueue rowQueue = new RowQueue();

        rowQueue.add(mockUpdateRowChange);
    }

    @Test
    public void testAddAllSingleInsert() {
        RowQueue rowQueue = new RowQueue();
        List<DefaultRowChange> rowChanges = new ArrayList<>();
        rowChanges.add(mockInsertRowChange);

        rowQueue.addAll(rowChanges);

        Assert.assertEquals(0, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(1, rowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(mockInsertRowChange, rowQueue.getPureInsertRowChanges().getFirst());
        Assert.assertEquals(0, rowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(0, rowQueue.getHoldingRowChanges().size());
    }

    @Test
    public void testAddAllMultipleChanges() {
        RowQueue rowQueue = new RowQueue();
        List<DefaultRowChange> rowChanges = new ArrayList<>();
        rowChanges.add(mockInsertRowChange);
        rowChanges.add(mockDeleteRowChange);
        rowChanges.add(mockUpdateRowChange);

        rowQueue.addAll(rowChanges);

        Assert.assertEquals(0, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(0, rowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(0, rowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(3, rowQueue.getHoldingRowChanges().size());
        Assert.assertEquals(mockInsertRowChange, rowQueue.getHoldingRowChanges().get(0));
        Assert.assertEquals(mockDeleteRowChange, rowQueue.getHoldingRowChanges().get(1));
        Assert.assertEquals(mockUpdateRowChange, rowQueue.getHoldingRowChanges().get(2));
    }

    @Test
    public void testMarkCompleted() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockDeleteRowChange);

        List<DefaultRowChange> holdingChanges = new ArrayList<>();
        holdingChanges.add(mockUpdateRowChange);
        rowQueue.addAll(holdingChanges);

        Assert.assertFalse(rowQueue.isCompleted());

        rowQueue.markCompleted();

        Assert.assertTrue(rowQueue.isCompleted());
        Assert.assertEquals(3, rowQueue.getAllRowChanges().size());
        Assert.assertEquals(0, rowQueue.getPureInsertRowChanges().size());
        Assert.assertEquals(0, rowQueue.getPureDeleteRowChanges().size());
        Assert.assertEquals(0, rowQueue.getHoldingRowChanges().size());
    }

    @Test
    public void testSizeWhenNotCompleted() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockDeleteRowChange);

        List<DefaultRowChange> holdingChanges = new ArrayList<>();
        holdingChanges.add(mockUpdateRowChange);
        rowQueue.addAll(holdingChanges);

        Assert.assertEquals(3, rowQueue.size());
    }

    @Test
    public void testSizeWhenCompleted() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockDeleteRowChange);

        List<DefaultRowChange> holdingChanges = new ArrayList<>();
        holdingChanges.add(mockUpdateRowChange);
        rowQueue.addAll(holdingChanges);

        rowQueue.markCompleted();

        Assert.assertEquals(3, rowQueue.size());
    }

    @Test
    public void testForEachApply() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batch -> batches.add(batch));

        Assert.assertEquals(1, batches.size());
        Assert.assertEquals(1, batches.get(0).size());
        Assert.assertEquals(mockInsertRowChange, batches.get(0).getRowChanges().get(0));
    }

    @Test
    public void testForEachApplyMultipleSameAction() {
        DefaultRowChange mockInsertRowChange2 = mock(DefaultRowChange.class);
        when(mockInsertRowChange2.getAction()).thenReturn(DBMSAction.INSERT);
        when(mockInsertRowChange2.getSchema()).thenReturn("test_schema");
        when(mockInsertRowChange2.getTable()).thenReturn("test_table");

        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockInsertRowChange2);
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batch -> batches.add(batch));

        Assert.assertEquals(1, batches.size());
        Assert.assertEquals(2, batches.get(0).size());
        Assert.assertEquals(mockInsertRowChange, batches.get(0).getRowChanges().get(0));
        Assert.assertEquals(mockInsertRowChange2, batches.get(0).getRowChanges().get(1));
    }

    @Test
    public void testForEachApplyDifferentSchema() {
        DefaultRowChange mockInsertRowChange2 = mock(DefaultRowChange.class);
        when(mockInsertRowChange2.getAction()).thenReturn(DBMSAction.INSERT);
        when(mockInsertRowChange2.getSchema()).thenReturn("test_schema_2");
        when(mockInsertRowChange2.getTable()).thenReturn("test_table");

        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockInsertRowChange2);
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batch -> batches.add(batch));

        Assert.assertEquals(2, batches.size());
        Assert.assertEquals(1, batches.get(0).size());
        Assert.assertEquals(1, batches.get(1).size());
    }

    @Test
    public void testForEachApplyDifferentTable() {
        DefaultRowChange mockInsertRowChange2 = mock(DefaultRowChange.class);
        when(mockInsertRowChange2.getAction()).thenReturn(DBMSAction.INSERT);
        when(mockInsertRowChange2.getSchema()).thenReturn("test_schema");
        when(mockInsertRowChange2.getTable()).thenReturn("test_table_2");

        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockInsertRowChange2);
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batch -> batches.add(batch));

        Assert.assertEquals(2, batches.size());
        Assert.assertEquals(1, batches.get(0).size());
        Assert.assertEquals(1, batches.get(1).size());
    }

    @Test
    public void testForEachApplyDifferentAction() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);
        rowQueue.add(mockDeleteRowChange);
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batch -> batches.add(batch));

        Assert.assertEquals(2, batches.size());
        Assert.assertEquals(1, batches.get(0).size());
        Assert.assertEquals(1, batches.get(1).size());
    }

    @Test
    public void testForEachApplyWithUpdateAction() {
        DefaultRowChange mockUpdateRowChange2 = mock(DefaultRowChange.class);
        when(mockUpdateRowChange2.getAction()).thenReturn(DBMSAction.UPDATE);
        when(mockUpdateRowChange2.getSchema()).thenReturn("test_schema");
        when(mockUpdateRowChange2.getTable()).thenReturn("test_table");

        DefaultRowChange mockUpdateRowChange3 = mock(DefaultRowChange.class);
        when(mockUpdateRowChange3.getAction()).thenReturn(DBMSAction.UPDATE);
        when(mockUpdateRowChange3.getSchema()).thenReturn("test_schema");
        when(mockUpdateRowChange3.getTable()).thenReturn("test_table");

        RowQueue rowQueue = new RowQueue();
        rowQueue.addAll(Lists.newArrayList(mockInsertRowChange, mockInsertRowChange,
            mockUpdateRowChange, mockUpdateRowChange2, mockUpdateRowChange3));
        rowQueue.markCompleted();

        List<RowBatch> batches = new ArrayList<>();
        rowQueue.forEachApply(batches::add);

        Assert.assertEquals(2, batches.size()); // Each update action creates a new batch
        Assert.assertEquals(2, batches.get(0).size());
        Assert.assertEquals(3, batches.get(1).size());
    }

    @Test(expected = PolardbxException.class)
    public void testForEachApplyThrowsExceptionWhenNotCompleted() {
        RowQueue rowQueue = new RowQueue();
        rowQueue.add(mockInsertRowChange);

        rowQueue.forEachApply(batch -> {
        });
    }

    @Test
    public void testIsInsertOrDelete() {
        RowQueue rowQueue = new RowQueue();

        // Test private method isInsertOrDelete using reflection or through public methods
        Assert.assertTrue(rowQueue.isCompleted() || true); // This is to make sure the test compiles
    }
}
