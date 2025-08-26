/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.BinlogXTopologyBuilder;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * created by ziyang.lb
 **/
public class BinlogXTopologyBuilderTest extends BaseTest {

    private List<BinlogTaskConfig> dispatcherList;
    private List<BinlogTaskConfig> dumperList;

    @Before
    public void setUp() {
        dispatcherList = new ArrayList<>();
        dumperList = new ArrayList<>();
    }

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
        assertEquals(Lists.newArrayList(180, 120, 60, 60, 30), sortedList);
    }

    @Test
    public void testBuildUpstreamSources_WithHashLevelDATANODE_MatchContainer() {
        mockConfig(BINLOGX_TRANSMIT_HASH_LEVEL, HashLevel.DATANODE.name());

        // Arrange
        dispatcherList.add(createTask("Dispatcher-1", "container-1"));
        dispatcherList.add(createTask("Dispatcher-2", "container-2"));

        BinlogTaskConfig dumper = createTask("Dumper-1", "container-1");
        dumperList.add(dumper);

        // Act
        BinlogXTopologyBuilder.buildUpstreamSources(dispatcherList, dumperList);

        // Assert
        ExecutionConfig executionConfig = JSONObject.parseObject(dumper.getConfig(), ExecutionConfig.class);
        assertNotNull(executionConfig.getSources());
        assertEquals(1, executionConfig.getSources().size());
        assertEquals("Dispatcher-1", executionConfig.getSources().get(0));
    }

    @Test
    public void testBuildUpstreamSources_WithHashLevelDATANODE_Not_MatchContainer() {
        mockConfig(BINLOGX_TRANSMIT_HASH_LEVEL, HashLevel.DATANODE.name());

        // Arrange
        dispatcherList.add(createTask("Dispatcher-1", "container-1"));
        dispatcherList.add(createTask("Dispatcher-2", "container-2"));

        BinlogTaskConfig dumper = createTask("Dumper-1", "container-3");
        dumperList.add(dumper);

        // Act
        BinlogXTopologyBuilder.buildUpstreamSources(dispatcherList, dumperList);

        // Assert
        ExecutionConfig executionConfig = JSONObject.parseObject(dumper.getConfig(), ExecutionConfig.class);
        assertNotNull(executionConfig.getSources());
        assertEquals(1, executionConfig.getSources().size());
        assertEquals("Dispatcher-1", executionConfig.getSources().get(0));
    }

    @Test
    public void testBuildUpstreamSources_WithHashLevelINSTANCE_AllDispatchers() {
        mockConfig(BINLOGX_TRANSMIT_HASH_LEVEL, HashLevel.RECORD.name());
        // Arrange
        dispatcherList.add(createTask("Dispatcher-1", "container-1"));
        dispatcherList.add(createTask("Dispatcher-2", "container-2"));

        BinlogTaskConfig dumper = createTask("Dumper-1", "container-1");
        dumperList.add(dumper);

        // Act
        BinlogXTopologyBuilder.buildUpstreamSources(dispatcherList, dumperList);

        // Assert
        ExecutionConfig executionConfig = JSONObject.parseObject(dumper.getConfig(), ExecutionConfig.class);
        assertNotNull(executionConfig.getSources());
        assertEquals(2, executionConfig.getSources().size());
        assertTrue(executionConfig.getSources().contains("Dispatcher-1"));
        assertTrue(executionConfig.getSources().contains("Dispatcher-2"));
    }

    @Test
    public void testBuildUpstreamSources_LoadBalancing_LeastUsed() {
        mockConfig(BINLOGX_TRANSMIT_HASH_LEVEL, HashLevel.DATANODE.name());

        // Arrange
        dispatcherList.add(createTask("Dispatcher-1", "container-1"));
        dispatcherList.add(createTask("Dispatcher-2", "container-1"));

        for (int i = 0; i < 5; i++) {
            BinlogTaskConfig dumper = createTask("Dumper-" + i, "container-2");
            dumperList.add(dumper);
        }

        // Act
        BinlogXTopologyBuilder.buildUpstreamSources(dispatcherList, dumperList);

        // 统计每个 dispatcher 被使用的次数
        int count1 = 0, count2 = 0;
        for (BinlogTaskConfig d : dumperList) {
            ExecutionConfig executionConfig = JSONObject.parseObject(d.getConfig(), ExecutionConfig.class);
            List<String> sources = executionConfig.getSources();
            if (sources.contains("Dispatcher-1")) {
                count1++;
            }
            if (sources.contains("Dispatcher-2")) {
                count2++;
            }
        }

        // Assert: 应该尽可能平均分配
        assertTrue(Math.abs(count1 - count2) <= 1);

    }

    // 创建一个 BinlogTaskConfig
    private BinlogTaskConfig createTask(String taskName, String containerId) {
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig() {
            @Override
            public String getTaskName() {
                return taskName;
            }

            @Override
            public String getContainerId() {
                return containerId;
            }
        };

        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setServerId(1111L);
        binlogTaskConfig.setConfig(JSONObject.toJSONString(executionConfig));
        return binlogTaskConfig;
    }
}
