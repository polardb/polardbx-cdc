/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yudong
 * @since 2023/10/8 16:06
 **/
@Slf4j
public class ReplicaFullValidUtil {

    /**
     * Get all table names in db.
     */
    public static List<String> showTables(JdbcTemplate jdbcTemplate, String dbName) {
        if (StringUtils.isEmpty(dbName)) {
            throw new IllegalArgumentException("db name can not be empty!");
        }

        String sql = "SHOW TABLES FROM ?";
        return jdbcTemplate.query(conn -> {
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setString(1, dbName);
            return preparedStatement;
        }, (rs, rowNum) -> rs.getString(1));
    }

    public static boolean isTableExist(JdbcTemplate jdbcTemplate, String dbName, String tbName) {
        if (StringUtils.isEmpty(dbName) || StringUtils.isEmpty(tbName)) {
            throw new IllegalArgumentException("db name or table name can not be empty!");
        }

        String sql = "SHOW TABLES FROM ? LIKE ?";
        Boolean res = jdbcTemplate.query(new PreparedStatementCreator() {
            @NotNull
            @Override
            public PreparedStatement createPreparedStatement(@NotNull Connection conn) throws SQLException {
                PreparedStatement preparedStatement = conn.prepareStatement(sql);
                preparedStatement.setString(1, dbName);
                preparedStatement.setString(2, tbName);
                return preparedStatement;
            }
        }, ResultSet::next);

        return Boolean.TRUE.equals(res);
    }

    /**
     * get primary keys of table
     * 注意：无主键表和隐式主键都会返回 empty list
     *
     * @return primary keys
     */
    public static List<String> getPrimaryKey(Connection conn, String dbName, String tbName) throws SQLException {
        Set<String> keys = new HashSet<>();
        String sql = String.format("SELECT column_name FROM `INFORMATION_SCHEMA`.`KEY_COLUMN_USAGE`" +
            "WHERE table_schema='%s' AND table_name='%s' AND constraint_name='PRIMARY'", dbName, tbName);
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String name = rs.getString(1);
                keys.add(name);
            }
        } finally {
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeResultSet(rs);
        }

        List<String> res = new ArrayList<>();
        List<Pair<String, String>> columns = getColumnNameAndType(conn, dbName, tbName);
        for (Pair<String, String> p : columns) {
            String c = p.getLeft();
            if (keys.contains(c)) {
                res.add(c);
            }
        }
        return res;
    }

    public static String getColumnType(Connection conn, String dbName, String tbName, String columnName)
        throws SQLException {
        String result = null;
        List<Pair<String, String>> columns = getColumnNameAndType(conn, dbName, tbName);
        for (Pair<String, String> p : columns) {
            if (p.getLeft().equalsIgnoreCase(columnName)) {
                result = p.getRight();
                break;
            }
        }
        return result;
    }

    public static List<Pair<String, String>> getColumnNameAndType(Connection conn, String dbName, String tbName)
        throws SQLException {
        List<Pair<String, String>> result = new ArrayList<>();
        String sql = String.format("DESC `%s`.`%s`", escape(dbName), escape(tbName));
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String name = rs.getString(1);
                String type = rs.getString(2);
                result.add(Pair.of(name, type));
            }
        } finally {
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeResultSet(rs);
        }
        return result;
    }

    private static String escape(String str) {
        String regex = "(?<!`)`(?!`)";
        return str.replaceAll(regex, "``");
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

    public static String buildFullTableName(String db, String tb) {
        String fullTableNameFormat = "`%s`.`%s`";
        return String.format(fullTableNameFormat, escape(db.toLowerCase()), escape(tb.toLowerCase()));
    }

    public static Pair<String, String> separateFullTableName(String fullTableName) {
        String[] dbAndTb = fullTableName.split("\\.");
        return Pair.of(dbAndTb[0], dbAndTb[1]);
    }

    public static long getTableRowsCount(Connection conn, String dbName, String tbName) {
        String querySqlFormat =
            "SELECT `TABLE_ROWS` FROM `INFORMATION_SCHEMA`.`TABLES` WHERE `TABLE_SCHEMA` = '%s' AND `TABLE_NAME` = '%s'";
        Statement stmt = null;
        ResultSet rs = null;
        long result = -1L;
        try {
            String sql = String.format(querySqlFormat, dbName, tbName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                result = rs.getLong("TABLE_ROWS");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeResultSet(rs);
        }

        return result;
    }

    public static long getTableAvgRowSize(Connection conn, String dbName, String tbName) {
        String querySqlFormat =
            "SELECT `AVG_ROW_LENGTH` FROM `INFORMATION_SCHEMA`.`TABLES` WHERE `TABLE_SCHEMA` = '%s' AND `TABLE_NAME` = '%s'";
        Statement stmt = null;
        ResultSet rs = null;
        long result = -1L;
        try {
            String sql = String.format(querySqlFormat, dbName, tbName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                result = rs.getLong("AVG_ROW_LENGTH");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeResultSet(rs);
        }

        return result;
    }

    public static Set<String> getRows(Connection conn, String query) throws SQLException {
        Set<String> rows = new HashSet<>();
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    row.append(rs.getString(i));
                    if (i < columnCount) {
                        row.append(", ");
                    }
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }

    public static boolean checkAndGetDiffRows(Connection conn1, Connection conn2, String query,
                                              MutablePair<Set<String>, Set<String>> diffRows)
        throws SQLException {
        Set<String> instance1Rows = getRows(conn1, query);
        Set<String> instance2Rows = getRows(conn2, query);

        // 在源实例中存在但在目标实例中不存在的行
        Set<String> diffInInstance1 = new HashSet<>(instance1Rows);
        diffInInstance1.removeAll(instance2Rows);

        // 在目标实例中存在但在源实例中不存在的行
        Set<String> diffInInstance2 = new HashSet<>(instance2Rows);
        diffInInstance2.removeAll(instance1Rows);

        if (diffInInstance1.isEmpty() && diffInInstance2.isEmpty()) {
            return true;
        } else {
            if (!diffInInstance1.isEmpty()) {
                log.error("Rows in the first instance that are not in the second instance:");
                for (String row : diffInInstance1) {
                    log.error(row);
                }
            }
            if (!diffInInstance2.isEmpty()) {
                log.error("Rows in the second instance that are not in the first instance:");
                for (String row : diffInInstance2) {
                    log.error(row);
                }
            }
        }
        diffRows.setLeft(diffInInstance1);
        diffRows.setRight(diffInInstance2);
        return false;
    }
}
