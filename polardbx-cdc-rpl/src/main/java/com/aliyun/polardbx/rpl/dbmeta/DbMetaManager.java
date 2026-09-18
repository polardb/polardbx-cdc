/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlExprParser;
import com.alibaba.polardbx.druid.sql.parser.ByteString;
import com.alibaba.polardbx.druid.sql.parser.Lexer;
import com.alibaba.polardbx.druid.sql.parser.Token;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final String SHOW_COLUMNS =
        "SELECT COLUMN_NAME, CHARACTER_SET_NAME, COLLATION_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
    private static final Pattern PREFIX_INDEX_COLUMN = Pattern.compile("^`?([^`(]+)`?\\s*\\((\\d+)\\)$");
    private static final boolean ignoreUK =
        DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION);

    private static final boolean forceIgnoreUK = DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FORCE_IGNORE_UK);
    ;

    private static final boolean isLabEnv = DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV);

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType) throws SQLException {
        return getTableInfo(dataSource, schema, tbName, hostType, null);
    }

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType, Map<String, String> customizedUsingUkAsPkTables)
        throws SQLException {
        boolean enableUk = hostType == HostType.POLARX2 || !ignoreUK;
        if (forceIgnoreUK) {
            enableUk = false;
        }
        return getTableInfo(dataSource, schema, tbName, hostType, enableUk, customizedUsingUkAsPkTables);
    }

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType, boolean needUkAndGsi) throws SQLException {
        return getTableInfo(dataSource, schema, tbName, hostType, needUkAndGsi, null);
    }

    public static TableInfo getTableInfo(DataSource dataSource, String schema, String tbName,
                                         HostType hostType, boolean needUkAndGsi,
                                         Map<String, String> customizedUsingUkAsPk) throws SQLException {
        TableInfo tableInfo = new TableInfo(schema, tbName);
        buildTableBasicInfo(dataSource, schema, tbName, tableInfo);
        List<ColumnInfo> columns = getTableColumnInfos(dataSource, schema, tbName);
        List<String> pks = getTablePks(tableInfo, dataSource, schema, tbName, customizedUsingUkAsPk, hostType);
        tableInfo.setColumns(columns);
        tableInfo.setPks(pks);
        validateTableColumns(tableInfo);
        Map<String, List<KeyColumnInfo>> ukGroups = Collections.emptyMap();
        if (needUkAndGsi) {
            ukGroups = getTableUkGroups(dataSource, schema, tbName, hostType);

            // 收集所有 UK 列名（去重）
            Set<String> ukSet = new HashSet<>();
            for (List<KeyColumnInfo> group : ukGroups.values()) {
                for (KeyColumnInfo column : group) {
                    if (column.getColumnName() != null) {
                        ukSet.add(column.getColumnName().toLowerCase());
                    }
                }
            }
            tableInfo.setUks(new ArrayList<>(ukSet));

            // 设置 ukGroups: 将 Map<String, List<KeyColumnInfo>> 转换为 List<List<String>>
            List<List<String>> ukGroupsList = new ArrayList<>();
            for (Map.Entry<String, List<KeyColumnInfo>> entry : ukGroups.entrySet()) {
                List<String> oneGroup = new ArrayList<>();
                for (KeyColumnInfo keyColumnInfo : entry.getValue()) {
                    if (keyColumnInfo.getColumnName() != null) {
                        oneGroup.add(keyColumnInfo.getColumnName().toLowerCase());
                    }
                }
                if (!oneGroup.isEmpty()) {
                    ukGroupsList.add(oneGroup);
                }
            }
            tableInfo.setUkGroups(ukGroupsList);

            if (isLabEnv && hostType == HostType.POLARX2) {
                for (ColumnInfo column : columns) {
                    if (column.isGenerated() && tableInfo.getUks().contains(column.getName())) {
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
        if (needUkAndGsi) {
            evaluateParallelApplyKeyCompatibility(tableInfo, ukGroups);
        } else {
            // V3 cannot safely build a PK+UK DAG when UK discovery is disabled by compatibility configuration.
            tableInfo.setParallelApplyKeyUnsupported(true);
            tableInfo.setParallelApplyKeyIncompatibleReason("unique key metadata discovery is disabled");
        }
        return tableInfo;
    }

    /**
     * Load the EXTERNALIZE marker that is missing from JDBC column metadata and DESC output.
     * <p>
     * The target CN exposes the logical marker only in SHOW CREATE TABLE. Keep the marker on
     * {@link ColumnInfo}, rather than adding the column to {@link TableInfo#getKeyList()}, because an
     * externalized value is not a row identity and may be a physical BlobRef address in a row image.
     * <p>
     * Metadata loading fails closed: silently treating a table as non-externalized would allow RPL's
     * all-column UPDATE optimizations to persist the BlobRef address as the logical TEXT/BLOB value.
     */
    public static void loadExternalizedColumnInfo(DataSource dataSource, String schema, String tbName,
                                                  TableInfo tableInfo) throws SQLException {
        final String createTable;
        try {
            createTable = getCreateTable(dataSource, schema, tbName);
        } catch (Exception e) {
            throw new SQLException(
                String.format("failed to load SHOW CREATE TABLE for external-column metadata: %s.%s",
                    schema, tbName), e);
        }

        if (StringUtils.isBlank(createTable)) {
            throw new SQLException(
                String.format("empty SHOW CREATE TABLE result for external-column metadata: %s.%s",
                    schema, tbName));
        }

        tableInfo.setCreateTable(createTable);
        if (!StringUtils.containsIgnoreCase(createTable, "EXTERNALIZE")) {
            return;
        }

        final SQLCreateTableStatement createTableStatement;
        try {
            createTableStatement = parseCreateTableColumnDefinitions(createTable);
        } catch (Throwable t) {
            throw new SQLException(
                String.format(
                    "failed to parse SHOW CREATE TABLE column definitions for external-column metadata: %s.%s",
                    schema, tbName), t);
        }

        for (SQLColumnDefinition columnDefinition : createTableStatement.getColumnDefinitions()) {
            if (!columnDefinition.isExternalize()) {
                continue;
            }

            String columnName = com.alibaba.polardbx.druid.sql.SQLUtils
                .normalize(columnDefinition.getColumnName()).toLowerCase(Locale.ROOT);
            ColumnInfo columnInfo = tableInfo.getColumnInfoOrNull(columnName);
            if (columnInfo == null) {
                throw new SQLException(
                    String.format("externalized column %s from SHOW CREATE TABLE is absent in JDBC metadata: %s.%s",
                        columnName, schema, tbName));
            }
            columnInfo.setExternalized(true);
        }
        tableInfo.invalidateExternalizedColumnNames();
    }

    /**
     * Parse the CREATE TABLE prefix through the outer column-list parenthesis.
     * <p>
     * PolarDB-X SHOW CREATE TABLE may append legacy sharding clauses after the column list. Those
     * clauses are unrelated to external-column metadata and older CN versions may serialize unusual
     * quoted shard keys into SQL that the CDC parser cannot consume. The lexer only locates the
     * structural boundary of the column list; the AST parser remains the sole authority for column
     * semantics such as EXTERNALIZE. Keep the source as {@link ByteString} while slicing because lexer
     * positions are byte offsets, not Java String character offsets.
     */
    public static SQLCreateTableStatement parseCreateTableColumnDefinitions(String createTable) {
        ByteString createTableBytes = ByteString.from(createTable);
        Lexer lexer = new MySqlExprParser(createTableBytes, false).getLexer();
        boolean tableKeywordSeen = false;
        boolean columnDefinitionsStarted = false;
        int parenthesisDepth = 0;

        while (lexer.token() != Token.EOF) {
            Token token = lexer.token();
            if (token == Token.TABLE) {
                tableKeywordSeen = true;
            } else if (token == Token.LPAREN && tableKeywordSeen) {
                columnDefinitionsStarted = true;
                parenthesisDepth++;
            } else if (token == Token.RPAREN && columnDefinitionsStarted) {
                parenthesisDepth--;
                if (parenthesisDepth == 0) {
                    SQLStatement statement = SQLUtils.parseSQLStatement(createTableBytes.substring(0, lexer.pos()));
                    if (!(statement instanceof SQLCreateTableStatement)) {
                        throw new IllegalArgumentException("SHOW CREATE TABLE did not parse as CREATE TABLE");
                    }
                    return (SQLCreateTableStatement) statement;
                }
            }
            lexer.nextToken();
        }

        throw new IllegalArgumentException("SHOW CREATE TABLE has no complete column definition list");
    }

    public static List<String> getDatabases(DataSource dataSource) throws Exception {
        List<String> dbs = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SHOW_DATABASES);
            rs = stmt.executeQuery();
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
            rs = stmt.executeQuery();
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
            rs = stmt.executeQuery();
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
        try (Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            statement.setString(1, schema);
            statement.setString(2, tbName);
            try (ResultSet resultSet = statement.executeQuery()) {
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
                                                        String tbName)
        throws SQLException {
        List<ColumnInfo> columnList = new ArrayList<>();

        Connection conn = null;
        ResultSet rs = null;
        ResultSet descRs = null;
        Statement stmt = null;
        Map<String, Pair<String, String>> columnCharsetCollationMap = new HashMap<>();

        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            DatabaseMetaData metaData = conn.getMetaData();
            schema = getIdentifierName(schema, metaData);
            tbName = getIdentifierName(tbName, metaData);

            // 先查询字符集和排序规则信息
            try (PreparedStatement charsetStatement = conn.prepareStatement(SHOW_COLUMNS)) {
                charsetStatement.setString(1, schema);
                charsetStatement.setString(2, tbName);
                try (ResultSet charsetRs = charsetStatement.executeQuery()) {
                    while (charsetRs.next()) {
                        String columnName = charsetRs.getString("COLUMN_NAME");
                        String characterSetName = charsetRs.getString("CHARACTER_SET_NAME");
                        String collationName = charsetRs.getString("COLLATION_NAME");
                        columnCharsetCollationMap.put(columnName, new ImmutablePair<>(characterSetName, collationName));
                    }
                }
            }

            // 这里获取的列信息里面没有on update信息，因此通过desc单独获取
            rs = getMetadataColumns(metaData, schema, tbName);
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

                // 获取字符集和排序规则信息
                String characterSetName = null;
                String collationName = null;
                if (columnCharsetCollationMap.containsKey(columnName)) {
                    Pair<String, String> charsetCollationPair = columnCharsetCollationMap.get(columnName);
                    characterSetName = charsetCollationPair.getLeft();
                    collationName = charsetCollationPair.getRight();
                }

                ColumnInfo columnInfo = new ColumnInfo(columnName.toLowerCase(), columnType, "",
                    (nullable != DatabaseMetaData.columnNoNulls), StringUtils.equals(isGeneratedColumn, "YES"),
                    typeName, size, characterSetName, collationName);

                if (descRs != null && descRs.next()) {
                    String extra = descRs.getString("Extra").toLowerCase();
                    boolean onUpdate = extra.contains("on update");
                    columnInfo.setOnUpdate(onUpdate);
                    // JDBC 驱动对 DEFAULT_GENERATED 列也会报 IS_GENERATEDCOLUMN=YES，
                    // 但只有 VIRTUAL GENERATED / STORED GENERATED 才是真正的生成列，
                    // DEFAULT_GENERATED（如 DEFAULT CURRENT_TIMESTAMP）不是，需要修正
                    if (columnInfo.isGenerated() && !extra.contains("virtual generated")
                        && !extra.contains("stored generated")) {
                        columnInfo.setGenerated(false);
                    }
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

    private static List<String> getTablePks(TableInfo tableInfo, DataSource dataSource, String schema, String tbName,
                                            Map<String, String> customizedUsingUkAsPk, HostType hostType)
        throws SQLException {
        List<String> pks = new ArrayList<>();
        Connection conn = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            schema = getIdentifierName(schema, metaData);
            tbName = getIdentifierName(tbName, metaData);
            String fullTableName = String.format("%s.%s", schema, tbName);

            if (customizedUsingUkAsPk != null && customizedUsingUkAsPk.containsKey(fullTableName)) {
                String targetUkName = customizedUsingUkAsPk.get(fullTableName);
                Map<String, List<KeyColumnInfo>> ukGroups = getTableUkGroups(dataSource, schema, tbName, hostType);
                if (ukGroups.containsKey(targetUkName)) {
                    for (KeyColumnInfo keyColumnInfo : ukGroups.get(targetUkName)) {
                        if (keyColumnInfo.isExpression() || keyColumnInfo.getColumnName() == null) {
                            throw new RuntimeException(
                                "expression uk " + targetUkName + " cannot be used as pk for table " + fullTableName);
                        }
                    }
                    pks.addAll(ukGroups.get(targetUkName).stream().map(KeyColumnInfo::getColumnName)
                        .collect(Collectors.toList()));
                    tableInfo.setUkAsPkTable(true);
                    tableInfo.setUkAsPkKeyName(targetUkName);
                } else {
                    throw new RuntimeException(
                        "uk name " + targetUkName + " not found in table " + fullTableName);
                }
            } else {
                rs = getMetadataPrimaryKeys(metaData, schema, tbName);
                SortedMap<Integer, String> pmap = new TreeMap<Integer, String>();
                while (rs.next()) {
                    pmap.put(rs.getInt(5), rs.getString(4).toLowerCase());
                }
                pks.addAll(pmap.values());
            }
        } catch (SQLException e) {
            log.error("failed in getTablePks, schema:{}, tbName:{}", schema, tbName);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, null, conn);
        }

        return pks;
    }

    public static List<String> getTableUks(DataSource dataSource, String schema, String tbName, HostType hostType)
        throws SQLException {
        Set<String> ukSet = new HashSet<>();
        Map<String, List<KeyColumnInfo>> ukGroups = getTableUkGroups(dataSource, schema, tbName, hostType);
        for (Iterable<KeyColumnInfo> group : ukGroups.values()) {
            for (KeyColumnInfo column : group) {
                if (column.getColumnName() != null) {
                    ukSet.add(column.getColumnName().toLowerCase());
                }
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
            rs = stmt.executeQuery();

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
                                                                    String tbName, HostType hostType)
        throws SQLException {
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        getTableUkGroupsFromLocalIndex(dataSource, schema, tbName, ukGroups);
        if (hostType == HostType.POLARX2) {
            getTableUkGroupsFromGlobalIndex(dataSource, schema, tbName, ukGroups);
        }
        return ukGroups;
    }

    public static void getTableUkGroupsFromLocalIndex(DataSource dataSource, String schema, String tbName,
                                                      HashMap<String, List<KeyColumnInfo>> ukGroups)
        throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        String sql = String.format(SHOW_INDEXES, CommonUtils.escape(schema), CommonUtils.escape(tbName));

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            res = stmt.executeQuery();

            while (res.next()) {
                int nonUnique = res.getInt("Non_unique");
                String keyName = res.getString("Key_name");
                int seqInIndex = res.getInt("Seq_in_index");
                String columnName = res.getString("Column_name");
                Object subPartValue = res.getObject("Sub_part");
                Integer subPart = subPartValue instanceof Number ? ((Number) subPartValue).intValue() : null;

                if (nonUnique == 1) {
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(PRIMARY, keyName)) {
                    continue;
                }

                // 表达式索引（MySQL 8.0+）的 Column_name 为 NULL。保留占位元数据，
                // 由 TableInfo 兼容性判定让 V3 回退串行，而不是让整个复制任务因元数据加载失败。
                KeyColumnInfo column = new KeyColumnInfo(tbName, keyName, columnName, nonUnique, seqInIndex,
                    subPart, columnName == null);
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
            log.error("failed in getTableUkGroups from local indexes : {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(res, stmt, conn);
        }
    }

    public static void getTableUkGroupsFromGlobalIndex(DataSource dataSource, String schema, String tbName,
                                                       HashMap<String, List<KeyColumnInfo>> ukGroups)
        throws SQLException {

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet res = null;
        String sql = String.format(SHOW_GLOBAL_INDEX, CommonUtils.escape(schema), CommonUtils.escape(tbName));

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            res = stmt.executeQuery();

            while (res.next()) {
                int nonUnique = res.getInt("NON_UNIQUE");
                String keyName = res.getString("KEY_NAME");
                String indexNames = res.getString("INDEX_NAMES");

                if (nonUnique == 1) {
                    continue;
                }

                if (StringUtils.equalsIgnoreCase(PRIMARY, keyName)) {
                    continue;
                }

                String[] columnNames = StringUtils.split(indexNames, ",");
                for (int i = 0; i < columnNames.length; i++) {
                    String rawColumnName = columnNames[i].trim();
                    Matcher prefixMatcher = PREFIX_INDEX_COLUMN.matcher(rawColumnName);
                    String columnName = stripIdentifierQuotes(rawColumnName);
                    Integer subPart = null;
                    boolean expression = false;
                    if (prefixMatcher.matches()) {
                        columnName = prefixMatcher.group(1).trim();
                        subPart = Integer.valueOf(prefixMatcher.group(2));
                    } else if (rawColumnName.indexOf('(') >= 0 || rawColumnName.indexOf(')') >= 0) {
                        columnName = null;
                        expression = true;
                    }
                    KeyColumnInfo column = new KeyColumnInfo(tbName, keyName, columnName, nonUnique, i + 1,
                        subPart, expression);
                    ukGroups.computeIfAbsent(keyName, k -> new ArrayList<>()).add(column);
                }
            }
        } catch (Throwable e) {
            log.error("failed in getTableUkGroups from global indexes : {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(res, stmt, conn);
        }
    }

    /**
     * Marks tables whose key type has neither an exact representation nor a supported DAG normalization.
     */
    static void evaluateParallelApplyKeyCompatibility(TableInfo tableInfo,
                                                      Map<String, List<KeyColumnInfo>> ukGroups) {
        String incompatibleReason = findParallelApplyKeyIncompatibleReason(tableInfo, ukGroups);
        tableInfo.setParallelApplyKeyUnsupported(incompatibleReason != null);
        tableInfo.setParallelApplyKeyIncompatibleReason(incompatibleReason);
    }

    private static String findParallelApplyKeyIncompatibleReason(TableInfo tableInfo,
                                                                 Map<String, List<KeyColumnInfo>> ukGroups) {
        for (Map.Entry<String, List<KeyColumnInfo>> entry : ukGroups.entrySet()) {
            for (KeyColumnInfo keyColumnInfo : entry.getValue()) {
                if (keyColumnInfo.isExpression() || keyColumnInfo.getColumnName() == null) {
                    return String.format("unique index %s contains an expression", entry.getKey());
                }
                if (keyColumnInfo.getSubPart() != null) {
                    return String.format("unique index %s uses prefix column %s(%d)", entry.getKey(),
                        keyColumnInfo.getColumnName(), keyColumnInfo.getSubPart());
                }
            }
        }

        // getKeyList is also used by compaction and CASE WHEN identity matching. It contains PK and shard keys;
        // for a no-PK table it intentionally contains every comparable column used to locate the row.
        Set<String> keyColumns = new LinkedHashSet<>(tableInfo.getKeyList());
        keyColumns.addAll(tableInfo.getUks());
        for (String keyColumn : keyColumns) {
            ColumnInfo columnInfo = tableInfo.getColumnInfoOrNull(keyColumn);
            if (columnInfo == null) {
                return String.format("key column %s is missing from table metadata", keyColumn);
            }
            if (columnInfo.isGenerated()) {
                return String.format("key column %s is generated", keyColumn);
            }
            if (!isExactParallelApplyKeyType(columnInfo) && !isNormalizableCharacterKeyType(columnInfo)) {
                String collationSuffix = StringUtils.isBlank(columnInfo.getCollationName()) ? ""
                    : ", collation=" + columnInfo.getCollationName();
                return String.format("key column %s has unsupported type %s%s", keyColumn,
                    columnInfo.getTypeName(), collationSuffix);
            }
        }
        return null;
    }

    /**
     * Exact-value whitelist for Java-side key comparison. Character strings are deliberately excluded because
     * String.equals cannot model MySQL collation, padding and accent/case equivalence. FLOAT/DOUBLE are excluded
     * because database equality around signed zero and special values is not represented by their string form.
     */
    private static boolean isExactParallelApplyKeyType(ColumnInfo columnInfo) {
        String typeName = StringUtils.defaultString(columnInfo.getTypeName()).toUpperCase(Locale.ROOT);
        // JDBC metadata maps MySQL spatial types from Types.OTHER to Types.BINARY above. Type name must win over
        // the JDBC code here, otherwise POINT/GEOMETRY keys would accidentally pass the binary whitelist.
        if (isComplexParallelApplyKeyType(typeName)) {
            return false;
        }
        if (typeName.startsWith("ENUM") || typeName.startsWith("SET") || typeName.startsWith("YEAR")) {
            return true;
        }
        switch (columnInfo.getType()) {
        case Types.BOOLEAN:
        case Types.BIT:
        case Types.TINYINT:
        case Types.SMALLINT:
        case Types.INTEGER:
        case Types.BIGINT:
        case Types.NUMERIC:
        case Types.DECIMAL:
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
        case Types.BINARY:
        case Types.VARBINARY:
            return true;
        default:
            return false;
        }
    }

    private static boolean isComplexParallelApplyKeyType(String typeName) {
        return typeName.contains("VECTOR")
            || typeName.contains("GEOMETRY")
            || typeName.contains("POINT")
            || typeName.contains("LINESTRING")
            || typeName.contains("POLYGON");
    }

    /**
     * Character PK/UK values use a lower-case, trailing-space-trimmed derivative only while building the DAG.
     * Original row images and exact compaction identity keys are deliberately left untouched.
     */
    private static boolean isNormalizableCharacterKeyType(ColumnInfo columnInfo) {
        switch (columnInfo.getType()) {
        case Types.CHAR:
        case Types.VARCHAR:
        case Types.NCHAR:
        case Types.NVARCHAR:
            return true;
        default:
            return false;
        }
    }

    private static String stripIdentifierQuotes(String columnName) {
        String result = StringUtils.trim(columnName);
        if (result != null && result.length() >= 2 && result.charAt(0) == '`'
            && result.charAt(result.length() - 1) == '`') {
            return result.substring(1, result.length() - 1);
        }
        return result;
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
            res = stmt.executeQuery();
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

    // JDBC metadata names are not SQL identifiers: do not wrap them in backticks. MySQL uses
    // catalogs by default, while H2 and Connector/J databaseTerm=SCHEMA use schemas.
    static ResultSet getMetadataColumns(DatabaseMetaData metadata, String schema, String table) throws SQLException {
        boolean useSchema = metadata.supportsSchemasInTableDefinitions();
        return metadata.getColumns(useSchema ? null : schema,
            useSchema ? metadataPattern(metadata, schema) : null, metadataPattern(metadata, table), null);
    }

    static ResultSet getMetadataPrimaryKeys(DatabaseMetaData metadata, String schema, String table)
        throws SQLException {
        boolean useSchema = metadata.supportsSchemasInTableDefinitions();
        // Unlike getColumns, getPrimaryKeys takes exact names, not search patterns.
        return metadata.getPrimaryKeys(useSchema ? null : schema, useSchema ? schema : null, table);
    }

    private static String metadataPattern(DatabaseMetaData metadata, String name) throws SQLException {
        String escape = metadata.getSearchStringEscape();
        if (StringUtils.isEmpty(escape)) {
            if (name.contains("_") || name.contains("%")) {
                throw new SQLException("JDBC metadata does not support escaping table/schema pattern: " + name);
            }
            return name;
        }
        return name.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%");
    }

    static void validateTableColumns(TableInfo tableInfo) throws SQLException {
        if (tableInfo.getColumns().isEmpty()) {
            throw new SQLException("empty JDBC column metadata for " + tableInfo.getSchema() + "." + tableInfo.getName());
        }
        for (String pk : tableInfo.getPks()) {
            if (tableInfo.getColumnInfoOrNull(pk) == null) {
                throw new SQLException("key column " + pk + " is absent in JDBC column metadata for "
                    + tableInfo.getSchema() + "." + tableInfo.getName());
            }
        }
    }

    private static String wrapEscape(String name) {
        return '`' + CommonUtils.escape(name) + '`';
    }
}
