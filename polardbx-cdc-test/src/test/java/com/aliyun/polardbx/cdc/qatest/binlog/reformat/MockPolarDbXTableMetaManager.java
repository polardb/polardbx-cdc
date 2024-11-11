/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
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
        super("storageInstId");
        this.phyTableMeta = phyTableMeta;
        this.logicTableMeta = logicTableMeta;
        logicBasicInfo = new LogicBasicInfo();
        logicBasicInfo.setSchemaName("d1");
        logicBasicInfo.setTableName("t1");
    }

    @Override
    public TableMeta findPhyTable(String schema, String table, boolean creatIfNotExist) {
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
