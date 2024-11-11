/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.parser;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import lombok.Data;

/**
 * @author agapple 2017年8月1日 下午7:30:42
 * @since 3.2.5
 */

@Data
public class DdlResult {

    private String schemaName;
    private String tableName;
    private DBMSAction type;
    private Boolean hasIfExistsOrNotExists = false;
    private SQLStatement sqlStatement;

    /*
     * RENAME TABLE tbl_name TO new_tbl_name [, tbl_name2 TO new_tbl_name2] ...
     */

    public DdlResult() {
    }

    public DdlResult(String schemaName, String tableName) {
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    @Override
    public DdlResult clone() {
        DdlResult result = new DdlResult();
        result.setSchemaName(schemaName);
        result.setTableName(tableName);
        result.setType(type);
        result.setSqlStatement(sqlStatement);
        return result;
    }

    @Override
    public String toString() {
        DdlResult ddlResult = this;
        return String.format(
            "DdlResult [schemaName=%s , tableName=%s , type=%s];",
            ddlResult.schemaName,
            ddlResult.tableName,
            ddlResult.type);
    }
}
