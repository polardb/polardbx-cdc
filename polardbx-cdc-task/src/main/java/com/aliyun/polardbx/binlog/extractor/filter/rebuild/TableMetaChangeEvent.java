/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import lombok.Data;

@Data
public class TableMetaChangeEvent {

    private boolean phyChange;

    private String biTable;
    private String aiTable;
    private String schema;
    private TableMeta tableMeta;
    private TableMetaChangeAction changeAction;
    private RuntimeContext rc;
    private boolean topologyChange = false;

    public void toLowerCase() {
        biTable = biTable != null ? CommonUtils.unwrap(biTable.toLowerCase()) : null;
        aiTable = aiTable != null ? CommonUtils.unwrap(aiTable.toLowerCase()) : null;
        schema = schema != null ? CommonUtils.unwrap(schema.toLowerCase()) : null;
    }

}
