/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.parser;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;

/**
 * @author agapple 2017年8月1日 下午7:30:42
 * @since 3.2.5
 */
public class DdlResult {

    private String schemaName;
    private String tableName;
    private String oriSchemaName; // rename ddl中的源表
    private String oriTableName; // rename ddl中的目标表
    private DBMSAction type;
    private DdlResult renameTableResult; // 多个rename table的存储

    /*
     * RENAME TABLE tbl_name TO new_tbl_name [, tbl_name2 TO new_tbl_name2] ...
     */

    public DdlResult() {
    }

    public DdlResult(String schemaName) {
        this.schemaName = schemaName;
    }

    public DdlResult(String schemaName, String tableName) {
        this.schemaName = schemaName;
        this.tableName = tableName;
    }

    public DdlResult(String schemaName, String tableName, String oriSchemaName, String oriTableName) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.oriSchemaName = oriSchemaName;
        this.oriTableName = oriTableName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public DBMSAction getType() {
        return type;
    }

    public void setType(DBMSAction type) {
        this.type = type;
    }

    public String getOriSchemaName() {
        return oriSchemaName;
    }

    public void setOriSchemaName(String oriSchemaName) {
        this.oriSchemaName = oriSchemaName;
    }

    public String getOriTableName() {
        return oriTableName;
    }

    public void setOriTableName(String oriTableName) {
        this.oriTableName = oriTableName;
    }

    public DdlResult getRenameTableResult() {
        return renameTableResult;
    }

    public void setRenameTableResult(DdlResult renameTableResult) {
        this.renameTableResult = renameTableResult;
    }

    public DdlResult clone() {
        DdlResult result = new DdlResult();
        result.setOriSchemaName(oriSchemaName);
        result.setOriTableName(oriTableName);
        result.setSchemaName(schemaName);
        result.setTableName(tableName);
        result.setType(type);
        return result;
    }

    @Override
    public String toString() {
        DdlResult ddlResult = this;
        StringBuffer sb = new StringBuffer();
        do {
            sb.append(String
                .format("DdlResult [schemaName=%s , tableName=%s , oriSchemaName=%s , oriTableName=%s , type=%s ];",
                    ddlResult.schemaName,
                    ddlResult.tableName,
                    ddlResult.oriSchemaName,
                    ddlResult.oriTableName,
                    ddlResult.type));
            ddlResult = ddlResult.renameTableResult;
        } while (ddlResult != null);
        return sb.toString();
    }
}
