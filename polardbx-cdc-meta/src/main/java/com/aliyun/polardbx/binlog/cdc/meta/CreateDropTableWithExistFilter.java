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

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * created by ziyang.lb
 **/
public class CreateDropTableWithExistFilter {

    //@see Aone，ID:39665786
    public static boolean shouldIgnore(String sql, Long ddlRecordId, Long ddlJobId) {
        if (ddlRecordId != null && ddlJobId == null && isCreateTableWithIfNotExist(sql)) {
            return true;
        }
        if (ddlRecordId != null && ddlJobId == null && isDropTableWithIfExist(sql)) {
            return true;
        }
        return false;
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
