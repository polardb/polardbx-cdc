/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.icbc;

import lombok.Data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Data
public class DdlTableMetaInfoRecord {

    public String schemaName;
    public String tableName;
    public String ddlStmt;
    public String ddlType;
    public Long jobId;
    public Long sourceJobId;
    public String tableMetaInfo;

    public DdlTableMetaInfoRecord() {
    }

    public DdlTableMetaInfoRecord(String schemaName, String tableName, String ddlStmt, String ddlType, Long jobId,
                                  Long sourceJobId, String tableMetaInfo) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.ddlStmt = ddlStmt;
        this.ddlType = ddlType;
        this.jobId = jobId;
        this.sourceJobId = sourceJobId;
        this.tableMetaInfo = tableMetaInfo;
    }

    public DdlTableMetaInfoRecord fill(ResultSet rs) throws SQLException {
        this.schemaName = rs.getString("schema_name");
        this.tableName = rs.getString("table_name");
        this.ddlStmt = rs.getString("ddl_stmt");
        this.ddlType = rs.getString("ddl_type");
        this.jobId = rs.getLong("job_id");
        this.sourceJobId = rs.getLong("source_job_id");
        this.tableMetaInfo = rs.getString("table_meta_info");
        return this;
    }
}
