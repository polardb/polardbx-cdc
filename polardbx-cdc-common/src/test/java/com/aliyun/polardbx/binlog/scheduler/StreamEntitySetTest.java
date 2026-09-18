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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class StreamEntitySetTest extends BaseTest {

    @Test
    public void testConstructorWithCollection() {
        List<StreamEntity> entities = new ArrayList<>();
        entities.add(new StreamEntity("stream-1", 100L));
        entities.add(new StreamEntity("stream-2", 200L));
        entities.add(new StreamEntity("stream-3", 150L));

        StreamEntitySet streamEntitySet = new StreamEntitySet(entities);
        Assert.assertEquals(3, streamEntitySet.size());

        StreamEntity maxEntity = streamEntitySet.getStreamEntityWithMaxTimestamp();
        Assert.assertEquals(200L, maxEntity.getTimestamp());
        Assert.assertEquals("stream-2", maxEntity.getStreamName());
    }

    @Test
    public void testConstructorWithComparator() {
        // 创建一个按时间戳降序排列的比较器
        StreamEntitySet streamEntitySet = new StreamEntitySet(
            Comparator.comparingLong(StreamEntity::getTimestamp).reversed());

        StreamEntity entity1 = new StreamEntity("stream-1", 100L);
        StreamEntity entity2 = new StreamEntity("stream-2", 200L);
        StreamEntity entity3 = new StreamEntity("stream-3", 150L);

        streamEntitySet.add(entity1);
        streamEntitySet.add(entity2);
        streamEntitySet.add(entity3);

        // 验证排序是否正确（按时间戳降序）
        Iterator<StreamEntity> iterator = streamEntitySet.iterator();
        Assert.assertSame(entity2, iterator.next()); // 200L
        Assert.assertSame(entity3, iterator.next()); // 150L
        Assert.assertSame(entity1, iterator.next()); // 100L
    }

    @Test
    public void testConstructorWithSortedSet() {
        // 创建一个已排序的TreeSet
        SortedSet<StreamEntity> sortedSet = new TreeSet<>(
            Comparator.comparingLong(StreamEntity::getTimestamp));

        StreamEntity entity1 = new StreamEntity("stream-1", 100L);
        StreamEntity entity2 = new StreamEntity("stream-2", 200L);
        StreamEntity entity3 = new StreamEntity("stream-3", 150L);

        sortedSet.add(entity1);
        sortedSet.add(entity2);
        sortedSet.add(entity3);

        // 使用SortedSet构造StreamEntitySet
        StreamEntitySet streamEntitySet = new StreamEntitySet(sortedSet);

        // 验证元素数量
        Assert.assertEquals(3, streamEntitySet.size());

        // 验证排序是否保持（按时间戳升序）
        Iterator<StreamEntity> iterator = streamEntitySet.iterator();
        Assert.assertSame(entity1, iterator.next()); // 100L
        Assert.assertSame(entity3, iterator.next()); // 150L
        Assert.assertSame(entity2, iterator.next()); // 200L
    }

    @Test
    public void testSortCapability() {
        StreamEntity entity1 = new StreamEntity("stream-1", 100L);
        StreamEntity entity2 = new StreamEntity("stream-2", 200L);
        StreamEntity entity3 = new StreamEntity("stream-3", 300L);
        StreamEntity entity4 = new StreamEntity("stream-4", 200L);

        StreamEntitySet streamEntitySet = new StreamEntitySet();
        streamEntitySet.add(entity1);
        streamEntitySet.add(entity2);
        streamEntitySet.add(entity3);
        streamEntitySet.add(entity4);

        Iterator<StreamEntity> iterator = streamEntitySet.iterator();
        Assert.assertSame(entity1, iterator.next()); // 字典序
        Assert.assertSame(entity2, iterator.next());
        Assert.assertSame(entity3, iterator.next());
        Assert.assertSame(entity4, iterator.next());
    }

    @Test
    public void testGetStreamEntityWithMaxTimestamp() {
        StreamEntity entity1 = new StreamEntity("stream-1", 100L);
        StreamEntity entity2 = new StreamEntity("stream-2", 200L);
        StreamEntity entity3 = new StreamEntity("stream-3", 300L);
        StreamEntity entity4 = new StreamEntity("stream-4", 200L);

        StreamEntitySet streamEntitySet = new StreamEntitySet();
        streamEntitySet.add(entity1);
        streamEntitySet.add(entity2);
        streamEntitySet.add(entity3);
        streamEntitySet.add(entity4);

        StreamEntity maxEntity = streamEntitySet.getStreamEntityWithMaxTimestamp();
        Assert.assertEquals(entity3, maxEntity);
    }

    @Test
    public void testGetStreamEntityWithMaxTimestampSameTime() {
        // 测试相同时间戳的情况下，按streamName排序
        StreamEntity entity1 = new StreamEntity("stream-a", 100L);
        StreamEntity entity2 = new StreamEntity("stream-c", 100L);
        StreamEntity entity3 = new StreamEntity("stream-b", 100L);

        StreamEntitySet streamEntitySet = new StreamEntitySet();
        streamEntitySet.add(entity1);
        streamEntitySet.add(entity2);
        streamEntitySet.add(entity3);

        StreamEntity maxEntity = streamEntitySet.getStreamEntityWithMaxTimestamp();
        Assert.assertEquals(entity2, maxEntity); // "stream-c" 字典序最大
    }

    @Test
    public void testEmptySet() {
        StreamEntitySet streamEntitySet = new StreamEntitySet();
        // 空集合应该抛出IndexOutOfBoundsException
        try {
            streamEntitySet.getStreamEntityWithMaxTimestamp();
            Assert.fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // 预期异常
        }
    }

    @Test
    public void testSingleElement() {
        StreamEntity entity = new StreamEntity("stream-1", 100L);
        StreamEntitySet streamEntitySet = new StreamEntitySet();
        streamEntitySet.add(entity);

        StreamEntity maxEntity = streamEntitySet.getStreamEntityWithMaxTimestamp();
        Assert.assertEquals(entity, maxEntity);
    }
}
