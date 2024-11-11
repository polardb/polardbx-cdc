/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author shicai.xsc 2020/12/7 19:39
 * @since 5.0.0.0
 */
@Slf4j
public class DbMetaManager {

    private static final String SHOW_RULE = "SHOW RULE FROM `%s`;";
    private static final String SHOW_INDEXES = "SHOW INDEXES FROM `%s`.`%s` WHERE Non_unique=0";
    private static final String SHOW_GLOBAL_INDEX = "SHOW GLOBAL INDEX FROM `%s`.`%s`";
    private static final String SHOW_DATABASES = "SHOW DATABASES";
    private static final String SHOW_CREATE_TABLE = "SHOW CREATE TABLE `%s`.`%s`";
    private static final String SHOW_TABLES = "SHOW TABLES";
    private static final String PRIMARY = "PRIMARY";
    private static final String DESC = "DESC %s.%s";
    private static final boolean enableUk =
        !DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION);

    private static final boolean isLabEnv = DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV);

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType) throws SQLException {
        return getTableInfo(dataSource, schema, tbName, hostType, enableUk);
    }

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType, boolean needUkAndGsi) throws SQLException {
        TableInfo tableInfo = new TableInfo(schema, tbName);
        buildTableBasicInfo(dataSource, schema, tbName, tableInfo);
        List<ColumnInfo> columns = getTableColumnInfos(dataSource, schema, tbName);
        List<String> pks = getTablePks(dataSource, schema, tbName);
        tableInfo.setColumns(columns);
        tableInfo.setPks(pks);
        if (needUkAndGsi) {
            List<String> uks = getTableUks(dataSource, schema, tbName);
            tableInfo.setUks(uks);
            if (isLabEnv && hostType == HostType.POLARX2) {
                for (ColumnInfo column : columns) {
                    if (column.isGenerated() && uks.contains(column.getName())) {
                        tableInfo.setHasGeneratedUk(true);
                        break;
                    }
                }
                tableInfo.setGsiNum(getGsiNum(dataSource, schema, tbName));
            }
        }

        if (hostType == HostType.POLARX1 || hostType == HostType.POLARX2) {
            List<String> shardKeys = getTableShardKeys(dataSource, tbName);
            if (!shardKeys.isEmpty()) {
                tableInfo.setDbShardKey(shardKeys.get(0));
            }
            if (shardKeys.size() > 1) {
                tableInfo.setTbShardKey(shardKeys.get(1));
            }
        }
        return tableInfo;
    }

    public static List<String> getDatabases(DataSource dataSource) throws Exception {
        List<String> dbs = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SHOW_DATABASES);
            rs = stmt.executeQuery(SHOW_DATABASES);
            while (rs.next()) {
                dbs.add(rs.getString(1));
            }
        } catch (Exception e) {
            log.error("failed in getDatabases", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return dbs;
    }

    public static List<String> getTables(DataSource dataSource) throws Exception {
        List<String> tables = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SHOW_TABLES);
            rs = stmt.executeQuery(SHOW_TABLES);
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (Exception e) {
            log.error("failed in getTables", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return tables;
    }

    public static String getCreateTable(DataSource dataSource, String schema, String tbName) throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String sql = String.format(SHOW_CREATE_TABLE, CommonUtils.escape(schema), CommonUtils.escape(tbName));
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getString(2);
            }
            return null;
        } catch (Exception e) {
            log.error("failed in getCreateTable", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    static void buildTableBasicInfo(DataSource dataSource, String schema, String tbName, TableInfo tableInfo)
        throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (ResultSet resultSet = connection.createStatement().executeQuery(String.format(
                "SELECT * FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
                schema, tbName))) {
                if (resultSet.next()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        String columnName = metaData.getColumnName(i);
                        if (columnName.equalsIgnoreCase("ENGINE")) {
                            String engine = resultSet.getString(i);
                            tableInfo.setEngine(engine);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static List<ColumnInfo> getTableColumnInfos(DataSource dataSource, String schema,
                                                        String tbName) throws SQLException {
        List<ColumnInfo> columnList = new ArrayList<>();

        Connection conn = null;
        ResultSet rs = null;
        ResultSet descRs = null;
        Statement stmt = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            DatabaseMetaData metaData = conn.getMetaData();
            schema = getIdentifierName(schema, metaData);
            tbName = getIdentifierName(tbName, metaData);
            // 这里获取的列信息里面没有on update信息，因此通过desc单独获取
            rs = metaData.getColumns("", wrapEscape(schema), wrapEscape(tbName), null);
            if (!"H2".equalsIgnoreCase(metaData.getDatabaseProductName())) {
                // H2 数据库不支持desc操作
                descRs = stmt.executeQuery(String.format(DESC, wrapEscape(schema), wrapEscape(tbName)));
            }

            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                int columnType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                int nullable = rs.getInt("NULLABLE");
                String isGeneratedColumn = rs.getString("IS_GENERATEDCOLUMN");
                String[] typeSplit = typeName.split(" ");

                // unsigned types
                if (typeSplit.length > 1) {
                    if (columnType == Types.INTEGER && typeSplit[1].equalsIgnoreCase("UNSIGNED")) {
                        columnType = Types.BIGINT;
                    }

                    if (columnType == Types.BIGINT && typeSplit[1].equalsIgnoreCase("UNSIGNED")) {
                        columnType = Types.BIGINT;
                    }
                }

                if (columnType == Types.BIT) {
                    if (typeName.contains("TINYINT")) {
                        columnType = Types.TINYINT;
                    }
                }

                int size = rs.getInt("COLUMN_SIZE");

                if (columnType == Types.OTHER) {
                    switch (typeName) {
                    case "NVARCHAR":
                    case "NVARCHAR2":
                        columnType = Types.VARCHAR;
                        break;
                    // geography type
                    case "POINT":
                    case "LINESTRING":
                    case "POLYGON":
                    case "MULTIPOINT":
                    case "MULTILINESTRING":
                    case "MULTIPOLYGON":
                    case "GEOMETRY":
                    case "GEOMETRYCOLLECTION":
                        columnType = Types.BINARY;
                        break;
                    default:
                        break;
                    }
                }
                if (StringUtils.equalsIgnoreCase(RplConstants.POLARX_IMPLICIT_ID, columnName) ||
                    StringUtils.equalsIgnoreCase(RplConstants.RDS_IMPLICIT_ID, columnName)) {
                    continue;
                }

                ColumnInfo columnInfo = new ColumnInfo(columnName.toLowerCase(), columnType, "",
                    (nullable != DatabaseMetaData.columnNoNulls), StringUtils.equals(isGeneratedColumn, "YES"),
                    typeName, size);

                if (descRs != null && descRs.next()) {
                    boolean onUpdate = descRs.getString("Extra").toLowerCase().contains("on update");
                    columnInfo.setOnUpdate(onUpdate);
                }

                columnList.add(columnInfo);
            }
        } catch (Throwable e) {
            log.error("failed in getTableColumnInfos, schema:{}, tbName:{}", schema, tbName);
            throw e;
        } finally {
            JdbcUtils.closeResultSet(descRs);
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }

        return columnList;
    }

    private static List<String> getTablePks(DataSource dataSource, String schema, String tbName) throws SQLException {
        List<String> pks = new ArrayList<>();
        Connection conn = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            schema = getIdentifierName(schema, metaData);
            tbName = getIdentifierName(tbName, metaData);
            rs = metaData.getPrimaryKeys(null, schema, tbName);

            SortedMap<Integer, String> pmap = new TreeMap<Integer, String>();
            while (rs.next()) {
                pmap.put(rs.getInt(5), rs.getString(4).toLowerCase());
            }
            pks.addAll(pmap.values());
        } catch (SQLException e) {
            log.error("failed in getTablePks, schema:{}, tbName:{}", schema, tbName);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, null, conn);
        }

        return pks;
    }

    public static List<String> getTableUks(DataSource dataSource, String schema, String tbName) throws SQLException {
        Set<String> ukSet = new HashSet<>();
        Map<String, List<KeyColumnInfo>> ukGroups = getTableUkGroups(dataSource, schema, tbName);
        for (Iterable<KeyColumnInfo> group : ukGroups.values()) {
            for (KeyColumnInfo column : group) {
                ukSet.add(column.getColumnName().toLowerCase());
            }
        }
        return new ArrayList<>(ukSet);
    }

    private static List<String> getTableShardKeys(DataSource dataSource, String tbName) throws SQLException {

        List<String> shardKeys = new ArrayList<>();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        String sql = String.format(SHOW_RULE, CommonUtils.escape(tbName));

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String dbShardKey = rs.getString("DB_PARTITION_KEY");
                String tbShardKey = rs.getString("TB_PARTITION_KEY");
                if (StringUtils.isNotBlank(dbShardKey)) {
                    shardKeys.add(dbShardKey.toLowerCase());
                }
                if (StringUtils.isNotBlank(tbShardKey)) {
                    shardKeys.add(tbShardKey.toLowerCase());
                }
            }
        } catch (Throwable e) {
            log.error("failed in getTableShardKeys: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }

        return shardKeys;
    }

    /**
     *
     */
    public static Map<String, List<KeyColumnInfo>> getTableUkGroups(DataSource dataSource, String schema,
                                                                    String tbName) throws SQLException {
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        String sql = String.format(SHOW_INDEXES, CommonUtils.escape(schema), CommonUtils.escape(tbName));

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            res = stmt.executeQuery(sql);

            while (res.next()) {
                int nonUnique = res.getInt("Non_unique");
                String keyName = res.getString("Key_name");
                int seqInIndex = res.getInt("Seq_in_index");
                String columnName = res.getString("Column_name");

                if (nonUnique == 1) {
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(PRIMARY, keyName)) {
                    continue;
                }

                KeyColumnInfo column = new KeyColumnInfo(tbName, keyName, columnName, nonUnique, seqInIndex);
                if (ukGroups.containsKey(keyName)) {
                    ukGroups.get(keyName).add(column);
                } else {
                    List<KeyColumnInfo> columns = new ArrayList<>();
                    columns.add(column);
                    ukGroups.put(keyName, columns);
                    columns.sort(Comparator.comparingInt(KeyColumnInfo::getSeqInIndex));
                }
            }
        } catch (Throwable e) {
            log.error("failed in getTableUkGroups: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(res, stmt, conn);
        }
        return ukGroups;
    }

    private static int getGsiNum(DataSource dataSource, String schema, String tbName) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;

        String sql = String.format(SHOW_GLOBAL_INDEX, CommonUtils.escape(schema), CommonUtils.escape(tbName));

        int gsiNum = 0;
        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            res = stmt.executeQuery(sql);
            while (res.next()) {
                gsiNum++;
            }
        } catch (Throwable e) {
            log.error("failed in get global index: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(res, stmt, conn);
        }
        return gsiNum;
    }

    /**
     * metaData:
     * storesUpperCaseIdentifiers，storesUpperCaseQuotedIdentifiers，storesLowerCaseIdentifiers,
     * storesLowerCaseQuotedIdentifiers,storesMixedCaseIdentifiers,storesMixedCaseQuotedIdentifiers
     */
    private static String getIdentifierName(String name, DatabaseMetaData metaData) throws SQLException {
        if (metaData.storesUpperCaseIdentifiers()) {
            return StringUtils.upperCase(name);
        } else if (metaData.storesLowerCaseIdentifiers()) {
            return StringUtils.lowerCase(name);
        }

        return name;
    }

    private static String wrapEscape(String name) {
        return '`' + CommonUtils.escape(name) + '`';
    }
}
