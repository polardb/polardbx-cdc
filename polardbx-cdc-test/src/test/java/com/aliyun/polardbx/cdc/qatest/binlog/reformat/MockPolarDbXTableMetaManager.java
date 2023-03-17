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
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.topology.LogicBasicInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by ShuGuang & ziyang.lb
 */
@Slf4j
public class MockPolarDbXTableMetaManager extends PolarDbXTableMetaManager {

    private final TableMeta phyTableMeta;
    private final TableMeta logicTableMeta;
    private final LogicBasicInfo logicBasicInfo;

    public MockPolarDbXTableMetaManager(TableMeta phyTableMeta, TableMeta logicTableMeta) {
        super("storageInstId", new AuthenticationInfo());
        this.phyTableMeta = phyTableMeta;
        this.logicTableMeta = logicTableMeta;
        logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName("d1");
        logicBasicInfo.setTableName("t1");
    }

    @Override
    public TableMeta findPhyTable(String schema, String table) {
        return phyTableMeta;
    }

    @Override
    public LogicBasicInfo getLogicBasicInfo(String phySchema, String phyTable) {
        return logicBasicInfo;
    }

    @Override
    public TableMeta findLogicTable(String schema, String table) {
        return logicTableMeta;
    }
}
