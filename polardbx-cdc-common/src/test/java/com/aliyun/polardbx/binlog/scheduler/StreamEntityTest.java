/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StreamEntityTest extends BaseTest {

    @Test
    public void testAllArgsConstructor() {
        StreamEntity entity = new StreamEntity("test-stream", 123456789L);
        assertEquals("test-stream", entity.getStreamName());
        assertEquals(123456789L, entity.getTimestamp());
    }

    @Test
    public void testSettersAndGetters() {
        StreamEntity entity = new StreamEntity("another-stream", 123456789L);

        assertEquals("another-stream", entity.getStreamName());
        assertEquals(123456789L, entity.getTimestamp());
    }

    @Test
    public void testEquals_SameObject() {
        StreamEntity entity = new StreamEntity("test-stream", 123456789L);
        assertEquals(entity, entity);
    }

    @Test
    public void testEquals_NullObject() {
        StreamEntity entity = new StreamEntity("test-stream", 123456789L);
        assertNotEquals(null, entity);
    }

    @Test
    public void testEquals_DifferentClass() {
        StreamEntity entity = new StreamEntity("test-stream", 123456789L);
        assertNotEquals("some string", entity);
    }

    @Test
    public void testEquals_SameValues() {
        StreamEntity entity1 = new StreamEntity("test-stream", 123456789L);
        StreamEntity entity2 = new StreamEntity("test-stream", 987654321L);
        assertEquals(entity1, entity2);
    }

    @Test
    public void testEquals_DifferentStreamNames() {
        StreamEntity entity1 = new StreamEntity("test-stream-1", 123456789L);
        StreamEntity entity2 = new StreamEntity("test-stream-2", 123456789L);
        assertNotEquals(entity1, entity2);
    }

    @Test
    public void testHashCode_SameStreamName() {
        StreamEntity entity1 = new StreamEntity("test-stream", 123456789L);
        StreamEntity entity2 = new StreamEntity("test-stream", 987654321L);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    public void testHashCode_DifferentStreamName() {
        StreamEntity entity1 = new StreamEntity("test-stream-1", 123456789L);
        StreamEntity entity2 = new StreamEntity("test-stream-2", 123456789L);
        assertNotEquals(entity1.hashCode(), entity2.hashCode());
    }

    @Test
    public void testToString() {
        StreamEntity entity = new StreamEntity("test-stream", 123456789L);
        String toStringResult = entity.toString();
        assertNotNull(toStringResult);
        assertTrue(toStringResult.contains("test-stream"));
        assertTrue(toStringResult.contains("123456789"));
    }

    @Test
    public void testStreamEntityInTreeSet() {
        StreamEntity entity1 = new StreamEntity("stream-1", 100L);
        StreamEntity entity2 = new StreamEntity("stream-2", 200L);
        StreamEntity entity3 = new StreamEntity("stream-3", 300L);

        TreeSet<StreamEntity> treeSet = new TreeSet<>();
        treeSet.add(entity1);
        treeSet.add(entity2);
        treeSet.add(entity3);

        Iterator<StreamEntity> iterator = treeSet.iterator();
        Assert.assertSame(entity1, iterator.next());
        Assert.assertSame(entity2, iterator.next());
        Assert.assertSame(entity3, iterator.next());
    }
}
