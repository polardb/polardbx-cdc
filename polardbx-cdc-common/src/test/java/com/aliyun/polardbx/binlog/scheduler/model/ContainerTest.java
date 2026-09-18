/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContainerTest extends com.aliyun.polardbx.binlog.testing.BaseTest {

    private List<Container> containerList;

    @Before
    public void setUp() {
        containerList = new ArrayList<>();

        // 创建具有不同资源能力的容器
        Container container1 = Container.builder()
            .containerId("container1")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.1")
            .build();

        Container container2 = Container.builder()
            .containerId("container2")
            .capability(Resource.builder().memory_mb(2048).cpu(4).used(1024).build())
            .ip("192.168.1.2")
            .build();

        Container container3 = Container.builder()
            .containerId("container3")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(768).build())
            .ip("192.168.1.3")
            .build();

        containerList.add(container1);
        containerList.add(container2);
        containerList.add(container3);
    }

    @Test
    public void testCompareFreeResource() {
        Container container1 = containerList.get(0); // 512MB free
        Container container2 = containerList.get(1); // 1024MB free
        Container container3 = containerList.get(2); // 256MB free

        // container2 has more free memory than container1
        assertTrue(Container.compareResource(container2, container1) > 0);

        // container1 has more free memory than container3
        assertTrue(Container.compareResource(container1, container3) > 0);

        // container2 has more free memory than container3
        assertTrue(Container.compareResource(container2, container3) > 0);

        // Same free memory but different CPU
        Container container4 = Container.builder()
            .containerId("container4")
            .capability(Resource.builder().memory_mb(1024).cpu(4).used(512).build())
            .ip("192.168.1.4")
            .build();

        Container container5 = Container.builder()
            .containerId("container5")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.5")
            .build();

        // Same memory but container4 has more CPU
        assertTrue(Container.compareResource(container4, container5) > 0);
    }

    @Test
    public void testSortByResourceDesc() {
        Container container4 = Container.builder()
            .containerId("container4")
            .capability(Resource.builder().memory_mb(1024).cpu(4).used(768).build())
            .ip("192.168.1.4")
            .build();
        Container container5 = Container.builder()
            .containerId("container5")
            .capability(Resource.builder().memory_mb(1024).cpu(4).used(768).build())
            .ip("192.168.1.4")
            .build();
        containerList.add(container4);
        containerList.add(container5);

        Container.sortByResourceDesc(containerList);

        assertEquals("container2", containerList.get(0).getContainerId());
        assertEquals("container1", containerList.get(1).getContainerId());
        assertEquals("container5", containerList.get(2).getContainerId());
        assertEquals("container4", containerList.get(3).getContainerId());
        assertEquals("container3", containerList.get(4).getContainerId());
    }

    @Test
    public void testSortByResourceDesc_2() {
        List<Container> list = new ArrayList<>();
        list.add(Container.builder().capability(Resource.builder().memory_mb(100).build()).containerId("1").build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(200).build()).containerId("2").build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(100).build()).containerId("3").build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(300).build()).containerId("4").build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(50).build()).containerId("5").build());

        Container.sortByResourceDesc(list);
        List<Integer> sortedList = list.stream()
            .map(Container::getCapability)
            .map(Resource::getFreeMemMb)
            .collect(Collectors.toList());
        assertEquals(Lists.newArrayList(180, 120, 60, 60, 30), sortedList);
    }

    @Test
    public void testEquals() {
        // 测试相同对象
        Container container1 = Container.builder()
            .containerId("container1")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.1")
            .build();

        assertEquals(container1, container1);

        // 测试相同containerId的不同对象
        Container container2 = Container.builder()
            .containerId("container1")
            .capability(Resource.builder().memory_mb(2048).cpu(4).used(1024).build())
            .ip("192.168.1.2")
            .build();

        assertEquals(container1, container2);

        // 测试不同的containerId
        Container container3 = Container.builder()
            .containerId("container2")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.1")
            .build();

        Assert.assertNotEquals(container1, container3);

        // 测试与null比较
        Assert.assertNotEquals(container1, null);

        // 测试与不同类型的对象比较
        Assert.assertNotEquals(container1, new Object());
    }

    @Test
    public void testHashCode() {
        // 相同containerId应该有相同的hashCode
        Container container1 = Container.builder()
            .containerId("container1")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.1")
            .build();

        Container container2 = Container.builder()
            .containerId("container1")
            .capability(Resource.builder().memory_mb(2048).cpu(4).used(1024).build())
            .ip("192.168.1.2")
            .build();

        assertEquals(container1.hashCode(), container2.hashCode());

        // 不同containerId应该有不同的hashCode
        Container container3 = Container.builder()
            .containerId("container2")
            .capability(Resource.builder().memory_mb(1024).cpu(2).used(512).build())
            .ip("192.168.1.1")
            .build();

        Assert.assertNotEquals(container1.hashCode(), container3.hashCode());
    }
}

