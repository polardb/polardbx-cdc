/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.BinlogXTopologyBuilder;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
public class BinlogXTopologyBuilderTest {

    @Before
    public void init() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
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
        Assert.assertEquals(Lists.newArrayList(210, 140, 70, 70, 35), sortedList);
    }
}
