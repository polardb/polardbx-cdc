/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * created by ziyang.lb
 **/
public class CreateDropTableWithExistFilter {

    //@see Aone，ID:39665786
    public static boolean shouldIgnore(String sql, Long ddlRecordId, Long ddlJobId, DDLExtInfo ddlExtInfo) {
        // task_id不为空，代表CN版本已经支持打标操作的线性一致（针对 create table if not exits 和 drop table if exists）
        // 对于已经支持线性一致性打标的版本，则不需要再进行忽略
        if (ddlRecordId != null && ddlJobId == null && (ddlExtInfo == null || ddlExtInfo.getTaskId() == null)
            && isCreateTableWithIfNotExist(sql)) {
            return true;
        }
        return ddlRecordId != null && ddlJobId == null && (ddlExtInfo == null || ddlExtInfo.getTaskId() == null)
            && isDropTableWithIfExist(sql);
    }

    private static boolean isCreateTableWithIfNotExist(String sql) {
        SQLStatement stmt = parseSQLStatement(sql);

        if (stmt instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) stmt;
            return createTableStatement.isIfNotExists();
        }
        return false;
    }

    private static boolean isDropTableWithIfExist(String sql) {
        SQLStatement stmt = parseSQLStatement(sql);

        if (stmt instanceof SQLDropTableStatement) {
            SQLDropTableStatement dropTableStatement = (SQLDropTableStatement) stmt;
            return dropTableStatement.isIfExists();
        }
        return false;
    }
}
