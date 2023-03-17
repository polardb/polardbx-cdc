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

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.GlobalBinlogTopologyBuilder;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.ip;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.port;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class StorageCountStrategyTest {

    @Before
    public void init() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void apply() {
        ResourceManager resourceManager = new ResourceManager("polardb-x");
        List<Container> capacity = resourceManager.availableContainers();
        StorageInfoMapper storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        List<StorageInfo> storageInfo = storageInfoMapper.selectDistinct(c ->
            c.where(status, isEqualTo(0))
                .and(instKind, isEqualTo(0))
                .groupBy(ip, port)
        );
        GlobalBinlogTopologyBuilder storageCountStrategy =
            new GlobalBinlogTopologyBuilder(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
        List<BinlogTaskConfig> apply =
            storageCountStrategy.buildTopology(capacity, storageInfo, ExecutionConfig.ORIGIN_TSO, 100, "");
        System.out.println(JSON.toJSONString(apply));

        //BinlogTaskConfigMapper binlogTaskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        //binlogTaskConfigMapper.delete(DeleteDSLCompleter.allRows());
        //binlogTaskConfigMapper.insertMultiple(apply);

    }
}