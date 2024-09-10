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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

public class PolarDbXStorageTableMetaFactoryTest extends BaseTestWithGmsTables {
    @Test
    public void testCreatePolarDBXStorageTableMeta() throws Exception {
        TopologyManager topologyManager = new TopologyManager();
        PolarDbXLogicTableMeta polarDbXLogicTableMeta = PolarDbXLogicTableMetaFactory.create(topologyManager, "5.7");
        PolarDbXStorageTableMeta polarDbXStorageTableMeta =
            PolarDbXStorageTableMetaFactory.create("", polarDbXLogicTableMeta, topologyManager, "5.7");
        Assert.assertNotNull(polarDbXStorageTableMeta);
    }
}
