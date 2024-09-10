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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ConsistCheckFactoryTest {
    @Test
    public void testCreateConsistCheck() {
        TopologyManager topologyManager = Mockito.mock(TopologyManager.class);
        PolarDbXLogicTableMeta polarDbXLogicTableMeta = Mockito.mock(PolarDbXLogicTableMeta.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(PolarDbXTableMetaManager.class);
        ConsistencyChecker consistencyChecker =
            ConsistencyCheckerFactory.create(topologyManager, polarDbXLogicTableMeta, polarDbXTableMetaManager, "");
        Assert.assertNotNull(consistencyChecker);
    }
}
