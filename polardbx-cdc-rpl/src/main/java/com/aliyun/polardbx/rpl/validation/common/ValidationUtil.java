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
package com.aliyun.polardbx.rpl.validation.common;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.apache.commons.collections.CollectionUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_FULL_VALID_SAMPLE_COUNT;

/**
 * @author yudong
 * @since 2024/1/17 11:52
 **/
public class ValidationUtil {

    public static String buildFullTableName(String schemaName, String tableName) {
        return String.format("`%s`.`%s`", CommonUtils.escape(schemaName), CommonUtils.escape(tableName));
    }

    public static long getTableRowsCount(Connection conn, String dbName, String tbName)
        throws SQLException {
        boolean sampleCount = DynamicApplicationConfig.getBoolean(RPL_FULL_VALID_SAMPLE_COUNT);
        if (sampleCount) {
            return getTableRowsByCount(conn, dbName, tbName);
        } else {
            return getTableRowsFromInformationSchema(conn, dbName, tbName);
        }
    }

    public static long getTableRowsFromInformationSchema(Connection conn, String dbName, String tbName)
        throws SQLException {
        String infoSql = String.format("SELECT `TABLE_ROWS` FROM `INFORMATION_SCHEMA`.`TABLES` "
            + "WHERE `TABLE_SCHEMA` = '%s' AND `TABLE_NAME` = '%s'", dbName, tbName);
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(infoSql)) {
            if (rs.next()) {
                return rs.getLong("TABLE_ROWS");
            } else {
                throw new SQLException("failed to fetch table rows count!");
            }
        }
    }

    public static long getTableRowsByCount(Connection conn, String dbName, String tbName) throws SQLException {
        String countSql =
            String.format("SELECT count(1) FROM `%s`.`%s`", CommonUtils.escape(dbName), CommonUtils.escape(tbName));
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next()) {
                return rs.getLong(1);
            } else {
                throw new SQLException("failed to fetch table rows count!");
            }
        }
    }

    public static long getTableAvgRowSize(Connection conn, String dbName, String tbName) throws SQLException {
        String sql = String.format(
            "SELECT `AVG_ROW_LENGTH` FROM `INFORMATION_SCHEMA`.`TABLES` WHERE `TABLE_SCHEMA` = '%s' AND `TABLE_NAME` = '%s'",
            dbName, tbName);
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong("AVG_ROW_LENGTH");
            } else {
                throw new SQLException("failed to fetch avg row length!");
            }
        }
    }

    public static String buildPrimaryKeyStr(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append("`").append(CommonUtils.escape(key)).append("`").append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

}
