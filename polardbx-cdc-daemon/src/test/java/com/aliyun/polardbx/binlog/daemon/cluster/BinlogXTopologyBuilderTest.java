/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.daemon.cluster.topology.BinlogXTopologyBuilder;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
public class BinlogXTopologyBuilderTest extends BaseTest {

    @Test
    public void tesSortByFreeResourceDesc() {
        List<Container> list = new ArrayList<>();
        list.add(Container.builder().capability(Resource.builder().memory_mb(100).build()).build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(200).build()).build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(100).build()).build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(300).build()).build());
        list.add(Container.builder().capability(Resource.builder().memory_mb(50).build()).build());

        new BinlogXTopologyBuilder("").sortByFreeResourceDesc(list);
        List<Integer> sortedList = list.stream()
            .map(Container::getCapability)
            .map(Resource::getFreeMemMb)
            .collect(Collectors.toList());
        Assert.assertEquals(Lists.newArrayList(180, 120, 60, 60, 30), sortedList);
    }
}
