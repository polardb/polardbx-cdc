/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.dbmeta;

import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String SHOW_DATABASES = "SHOW DATABASES";
    private static final String SHOW_CREATE_TABLE = "SHOW CREATE TABLE `%s`.`%s`";
    private static final String SHOW_TABLES = "SHOW TABLES";
    private static final String PRIMARY = "PRIMARY";

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType) throws SQLException {
        TableInfo tableInfo = new TableInfo(schema, tbName);
        List<ColumnInfo> columns = getTableColumnInfos(dataSource, schema, tbName);
        List<String> pks = getTablePks(dataSource, schema, tbName);
        List<String> uks = getTableUks(dataSource, schema, tbName);

        tableInfo.setColumns(columns);
        tableInfo.setPks(pks);
        tableInfo.setUks(uks);

        if (hostType.getValue() == HostType.POLARX2.getValue()) {
            List<String> shardKeys = getTableShardKeys(dataSource, tbName);
            if (shardKeys.size() > 0) {
                tableInfo.setDbShardKey(shardKeys.get(0));
            }
            if (shardKeys.size() > 1) {
                tableInfo.setTbShardKey(shardKeys.get(1));
            }
        }
        return tableInfo;
    }

    public static List<String> getDatabases(DataSource dataSource) throws Throwable {
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
        } catch (Throwable e) {
            log.error("failed in getDatabases", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return dbs;
    }

    public static List<String> getTables(DataSource dataSource) throws Throwable {
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
        } catch (Throwable e) {
            log.error("failed in getTables", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return tables;
    }

    public static String getCreateTable(DataSource dataSource, String schema, String tbName) throws Throwable {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String sql = String.format(SHOW_CREATE_TABLE, schema, tbName);
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            return rs.getString(2);
        } catch (Throwable e) {
            log.error("failed in getCreateTable", e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    /**
     *
     */
    private static List<ColumnInfo> getTableColumnInfos(DataSource dataSource, String schema,
                                                        String tbName) throws SQLException {
        List<ColumnInfo> columnList = new ArrayList<>();

        Connection conn = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            schema = getIdentifierName(schema, metaData);
            tbName = getIdentifierName(tbName, metaData);
            rs = metaData.getColumns("", schema, tbName, null);

            while (rs.next()) {
                String columnName = rs.getString(4);
                int columnType = rs.getInt(5);
                String typeName = rs.getString(6);
                int nullable = rs.getInt(11);
                String[] typeSplit = typeName.split(" ");

                // unsigned types
                if (typeSplit.length > 1) {
                    if (columnType == Types.INTEGER && typeSplit[1].toUpperCase().equals("UNSIGNED")) {
                        columnType = Types.BIGINT;
                    }

                    if (columnType == Types.BIGINT && typeSplit[1].toUpperCase().equals("UNSIGNED")) {
                        columnType = Types.BIGINT;
                    }
                }

                if (columnType == Types.BIT) {
                    if (typeName.contains("TINYINT")) {
                        columnType = Types.TINYINT;
                    }
                }

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
                if (!columnName.equals(RplConstants.POLARX_IMPLICIT_ID) &&
                    !columnName.equals(RplConstants.RDS_IMPLICIT_ID)) {
                    columnList.add(new ColumnInfo(columnName, columnType, "",
                        (nullable != DatabaseMetaData.columnNoNulls)));
                }
            }
        } catch (Throwable e) {
            log.error("failed in getTableColumnInfos, schema:{}, tbName:{}", schema, tbName);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, null, conn);
        }

        return columnList;
    }

    /**
     *
     */
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
                pmap.put(rs.getInt(5), rs.getString(4));
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

    private static List<String> getTableUks(DataSource dataSource, String schema, String tbName) throws SQLException {
        List<String> uks = new ArrayList<>();
        Map<String, List<KeyColumnInfo>> ukGroups = getTableUkGroups(dataSource, schema, tbName);
        int minGroupSize = Integer.MAX_VALUE;
        String minGroupKey = "";
        for (String groupKey : ukGroups.keySet()) {
            int groupSize = ukGroups.get(groupKey).size();
            if (groupSize < minGroupSize) {
                minGroupSize = groupSize;
                minGroupKey = groupKey;
            }
        }

        if (StringUtils.isNotBlank(minGroupKey)) {
            for (KeyColumnInfo column : ukGroups.get(minGroupKey)) {
                uks.add(column.getColumnName());
            }
        }

        return uks;
    }

    private static List<String> getTableShardKeys(DataSource dataSource, String tbName) throws SQLException {

        List<String> shardKeys = new ArrayList<>();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        String sql = String.format(SHOW_RULE, tbName);

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String dbShardKey = rs.getString("DB_PARTITION_KEY");
                String tbShardKey = rs.getString("TB_PARTITION_KEY");
                if (StringUtils.isNotBlank(dbShardKey)) {
                    shardKeys.add(dbShardKey);
                }
                if (StringUtils.isNotBlank(tbShardKey)) {
                    shardKeys.add(tbShardKey);
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
    private static Map<String, List<KeyColumnInfo>> getTableUkGroups(DataSource dataSource, String schema,
                                                                     String tbName) throws SQLException {
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        String sql = String.format(SHOW_INDEXES, schema, tbName);

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
}
