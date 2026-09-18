/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.fastjson.JSON;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexDefinition;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexOptions;
import com.alibaba.polardbx.druid.sql.ast.SQLPartition;
import com.alibaba.polardbx.druid.sql.ast.SQLPartitionBy;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.SQLStatementImpl;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsMovePartition;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsSplitPartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddColumn;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropColumnItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableGroupStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableSetOption;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCharacterDataType;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnPrimaryKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnUniqueKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableGroupStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsAlterTableModifyTtlOptions;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsAlterTableSingle;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableModifyColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableOption;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DDL_SET_TABLE_GROUP_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_ALGORITHM_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.binlog.util.SQLUtils.removeSomeHints;
import static com.aliyun.polardbx.binlog.util.SQLUtils.toSQLStringWithTrueUcase;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class DDLConverter {

    public static String processDdlSqlCharacters(String polarxDDL, String dbCharset, String tbCollation) {
        return processDdlSqlCharacters(null, polarxDDL, dbCharset, tbCollation);
    }

    public static String processDdlSqlCharacters(String tableName, String polarxDDL, String dbCharset,
                                                 String tbCollation) {
        if (StringUtils.isBlank(polarxDDL)) {
            return polarxDDL;
        }

        String ddl = polarxDDL;
        try {
            SQLStatement statement = parseSQLStatement(polarxDDL);
            if (statement instanceof MySqlCreateTableStatement) {
                MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) statement;
                tryAttacheCharacterInfo(createTableStatement, tbCollation);
                hack4RepairTableName(tableName, createTableStatement);
                ddl = createTableStatement.toString();
            } else if (statement instanceof SQLCreateDatabaseStatement) {
                SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                createDatabaseStatement.setCharacterSet(dbCharset);
                ddl = createDatabaseStatement.toString();
            }
        } catch (Throwable e) {
            log.error("process ddl sql characters error, sql {}! ", polarxDDL, e);
            throw e;
        }
        return ddl;
    }

    /**
     * 提取 ddl 注释中的value
     */
    private static String extractCommentValue(String ddl, String key) {
        int l = ddl.length();
        int i = 0;
        int e = 0;
        do {
            i = ddl.indexOf("/*", i);
            if (i != -1) {
                e = ddl.indexOf("*/", i + 2);
                if (e > i) {
                    String searchPattern = ddl.substring(i + 2, e).trim();
                    i = e;
                    String[] kv = searchPattern.split("=", 2);
                    if (kv.length != 2) {
                        continue;
                    }
                    if (StringUtils.equalsIgnoreCase(kv[0].trim(), key)) {
                        return kv[1].trim();
                    }
                } else {
                    break;
                }
            }
        } while (i != -1);
        return null;
    }

    public static String buildDdlEventSql(String polarxDDL, String dbCharset, String tbCollation, String tso) {
        return buildDdlEventSql(null, polarxDDL, dbCharset, tbCollation, tso, null, null, false, null);
    }

    public static String buildDdlEventSql(String tableName, String polarxDDL, String dbCharset, String tbCollation,
                                          String tso) {
        return buildDdlEventSql(tableName, polarxDDL, dbCharset, tbCollation, tso, null, null, false, null);
    }

    public static String buildDdlEventSql(String tableName, String ddlSqlForPolar, String dbCharset, String tbCollation,
                                          String tso, String ddlSqlForMysql) {
        return buildDdlEventSql(tableName, ddlSqlForPolar, dbCharset, tbCollation, tso, ddlSqlForMysql, null, false,
            null);
    }

    public static String buildDdlEventSql(String tableName, String ddlSqlForPolar, String dbCharset, String tbCollation,
                                          String tso, String ddlSqlForMysql, String ddlRecordSql, boolean isCci,
                                          Map<String, Object> polarxVariables) {
        StringBuilder sqlBuilder = new StringBuilder();
        buildDdlEventSqlForPolarPart(sqlBuilder, ddlSqlForPolar, dbCharset, tbCollation, tso, isCci, polarxVariables);
        buildDdlEventSqlForMysqlPart(sqlBuilder, tableName, dbCharset, tbCollation, ddlSqlForMysql, ddlRecordSql);
        return sqlBuilder.toString();
    }

    static void buildDdlEventSqlForPolarPart(StringBuilder sqlBuilder, String ddlSqlForPolar, String dbCharset,
                                             String tbCollation, String tso, boolean isCci,
                                             Map<String, Object> polarxVariables) {
        if (StringUtils.isBlank(ddlSqlForPolar) || !getBoolean(ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED)) {
            return;
        }

        SQLStatement sqlStatement = parseSQLStatement(ddlSqlForPolar);

        if (sqlStatement instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) sqlStatement;
            createDatabaseStatement.setLocality(null);
            createDatabaseStatement.setCharacterSet(dbCharset);
        } else if (sqlStatement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
            createTableStatement.setLocality(null);
            tryAttacheCharacterInfo(createTableStatement, tbCollation);
            if (!getBoolean(BINLOG_DDL_SET_TABLE_GROUP_ENABLED)) {
                createTableStatement.setTableGroup(null);
                createTableStatement.setJoinGroup(null);
            }

            SQLPartitionBy sqlPartitionBy = createTableStatement.getPartitioning();
            if (sqlPartitionBy != null && sqlPartitionBy.getPartitions() != null) {
                sqlPartitionBy.getPartitions().forEach(p -> p.setLocality(null));
            }
            createTableStatement.getTableElementList().forEach(DDLConverter::removeLocalityInGsiForCreateTable);
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement alterTableStatement = (SQLAlterTableStatement) sqlStatement;
            if (!getBoolean(BINLOG_DDL_SET_TABLE_GROUP_ENABLED)) {
                alterTableStatement.setAlignToTableGroup(null);
            }

            alterTableStatement.setLocality(null);
            if (alterTableStatement.getPartition() != null) {
                if (alterTableStatement.getPartition().getPartitions() != null) {
                    alterTableStatement.getPartition().getPartitions().forEach(p -> p.setLocality(null));
                }
            }
            if (alterTableStatement.getItems() != null) {
                alterTableStatement.getItems().removeIf(item -> item instanceof DrdsMovePartition);
                alterTableStatement.getItems().removeIf(i -> {
                    if (!getBoolean(BINLOG_DDL_SET_TABLE_GROUP_ENABLED)) {
                        if (i instanceof SQLAlterTableSetOption) {
                            SQLAlterTableSetOption setOption = (SQLAlterTableSetOption) i;
                            return setOption.isAlterTableGroup();
                        }
                    }
                    return false;
                });

                // remove _drds_implicit_id_
                alterTableStatement.getItems().forEach(DDLConverter::tryRemoveDropImplicitPk);
                // remove locality info in GSI
                alterTableStatement.getItems().forEach(DDLConverter::removeLocalityInGsiForAlterTable);
                // remove locality info in DrdsAlterTableSingle
                alterTableStatement.getItems().forEach(i -> {
                    if (i instanceof DrdsAlterTableSingle) {
                        ((DrdsAlterTableSingle) i).setLocality(null);
                    }
                });
            }
        } else if (sqlStatement instanceof SQLCreateTableGroupStatement) {
            SQLCreateTableGroupStatement createTableGroupStatement = (SQLCreateTableGroupStatement) sqlStatement;
            createTableGroupStatement.setLocality(null);
            SQLPartitionBy sqlPartitionBy = createTableGroupStatement.getSqlPartitionBy();
            if (sqlPartitionBy != null && sqlPartitionBy.getPartitions() != null) {
                sqlPartitionBy.getPartitions().forEach(p -> p.setLocality(null));
            }
        } else if (sqlStatement instanceof SQLCreateIndexStatement) {
            SQLCreateIndexStatement createIndexStatement = (SQLCreateIndexStatement) sqlStatement;
            if (createIndexStatement.getPartitioning() != null) {
                createIndexStatement.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
            }
        } else if (sqlStatement instanceof SQLAlterTableGroupStatement) {
            SQLAlterTableGroupStatement alterTableGroupStatement = (SQLAlterTableGroupStatement) sqlStatement;
            if (alterTableGroupStatement.getItem() != null
                && alterTableGroupStatement.getItem() instanceof DrdsSplitPartition) {
                DrdsSplitPartition splitPartition = (DrdsSplitPartition) alterTableGroupStatement.getItem();
                splitPartition.getPartitions().forEach(p -> {
                    if (p instanceof SQLPartition) {
                        ((SQLPartition) p).setLocality(null);
                    }
                });
            }
        }

        String privateDdlSql = "";
        if (sqlStatement != null) {
            SQLHintsFilter.filter(sqlStatement);
            removeSomeHints(sqlStatement);
            removeAsyncDdlFlags(sqlStatement);
            privateDdlSql = toSQLStringWithTrueUcase(sqlStatement);
        }

        if (StringUtils.contains(privateDdlSql, "\n")) {
            log.warn("polarx original sql contains CRLF, encoding to base64, tso : {}, sql : {}", tso, privateDdlSql);
            privateDdlSql = Base64.getEncoder().encodeToString(privateDdlSql.getBytes());
            sqlBuilder.append(CommonUtils.PRIVATE_DDL_ENCODE_BASE64).append("\n");
        }

        String ddlId = extractCommentValue(ddlSqlForPolar, "DDL_ID");
        if (StringUtils.isBlank(ddlId)) {
            ddlId = "0";
        }
        String ddlTypes = "";
        if (isCci) {
            ddlTypes = ddlTypes + "CCI";
        }
        String extraDdl = extractCommentValue(ddlSqlForPolar, "EXTRA_DDL");
        sqlBuilder.append(CommonUtils.PRIVATE_DDL_DDL_PREFIX).append(privateDdlSql).append("\n");
        sqlBuilder.append(CommonUtils.PRIVATE_DDL_TSO_PREFIX).append(tso).append("\n");
        sqlBuilder.append(CommonUtils.PRIVATE_DDL_ID_PREFIX).append(ddlId).append("\n");
        if (StringUtils.isNotBlank(ddlTypes)) {
            sqlBuilder.append(CommonUtils.PRIVATE_DDL_DDL_TYPES_PREFIX).append(ddlTypes).append("\n");
        }
        if (StringUtils.isNotBlank(extraDdl)) {
            sqlBuilder.append(CommonUtils.PRIVATE_DDL_EXTRA_DDL_PREFIX).append(extraDdl).append("\n");
        }
        if (polarxVariables != null && !polarxVariables.isEmpty()) {
            sqlBuilder.append(CommonUtils.PRIVATE_DDL_POLARX_VARIABLES_PREFIX)
                .append(JSON.toJSONString(polarxVariables))
                .append("\n");
        }

    }

    static void buildDdlEventSqlForMysqlPart(StringBuilder sqlBuilder, String tableName, String dbCharset,
                                             String tbCollation,
                                             String ddlSqlForNormalMysql) {
        buildDdlEventSqlForMysqlPart(sqlBuilder, tableName, dbCharset, tbCollation, ddlSqlForNormalMysql, null);
    }

    static void buildDdlEventSqlForMysqlPart(StringBuilder sqlBuilder, String tableName, String dbCharset,
                                             String tbCollation,
                                             String ddlSqlForNormalMysql,
                                             String ddlRecordSql) {
        if (StringUtils.isBlank(ddlSqlForNormalMysql)) {
            return;
        }

        SQLStatement sqlStatement = parseSQLStatement(ddlSqlForNormalMysql);

        // 去掉异步DDL相关的hint和标志，避免下游PolarDB-X也异步执行DDL
        SQLHintsFilter.filter(sqlStatement);
        removeSomeHints(sqlStatement);
        removeAsyncDdlFlags(sqlStatement);

        if (sqlStatement instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) sqlStatement;
            createDatabaseStatement.setPartitionMode(null);
            createDatabaseStatement.setDefaultSingle(null);
            createDatabaseStatement.setLocality(null);
            createDatabaseStatement.setCharacterSet(dbCharset);
        } else if (sqlStatement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
            normalizeCreateTable(tableName, tbCollation, createTableStatement, ddlRecordSql);
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
            normalizeAlterTable(sqlAlterTableStatement);
        } else if (sqlStatement instanceof SQLCreateIndexStatement) {
            SQLCreateIndexStatement sqlCreateIndexStatement = (SQLCreateIndexStatement) sqlStatement;
            if ("VECTOR".equalsIgnoreCase(sqlCreateIndexStatement.getIndexDefinition().getType())) {
                return;
            }
            sqlCreateIndexStatement.getIndexDefinition().setKey(true);
            reformatIndex(sqlCreateIndexStatement.getIndexDefinition());
        } else if (sqlStatement instanceof SQLDropTableStatement) {
            SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
            sqlDropTableStatement.setPurge(false);
        }

        sqlBuilder.append(toSQLStringWithTrueUcase(sqlStatement));
    }

    private static void normalizeCreateTable(String tableName, String tbCollation,
                                             MySqlCreateTableStatement createTableStatement, String ddlRecordSql) {
        // remove private syntax in main statement
        createTableStatement.setBroadCast(false);
        createTableStatement.setType(null);
        createTableStatement.setPartitioning(null);
        createTableStatement.setDbPartitionBy(null);
        createTableStatement.setDbPartitions(null);
        createTableStatement.setExPartition(null);
        createTableStatement.setTablePartitionBy(null);
        createTableStatement.setTablePartitions(null);
        createTableStatement.setPrefixBroadcast(false);
        createTableStatement.setPrefixPartition(false);
        createTableStatement.setTableGroup(null);
        createTableStatement.setAutoSplit(null);
        createTableStatement.setJoinGroup(null);
        createTableStatement.setLocality(null);
        createTableStatement.setLocalPartitioning(null);
        createTableStatement.setLocation(null);
        createTableStatement.setSingle(false);
        // DBLE复制表语法，可以指定locality的表，对mysql过滤
        // https://aliyuque.antfin.com/coronadb/design/wh5lbx3b722geqkg#0ff7ea86
        createTableStatement.setReplicas(false);

        // try attache character info
        tryAttacheCharacterInfo(createTableStatement, tbCollation);

        // 寻找自增主键, 去掉sequence type
        List<SQLTableElement> sqlTableElementList = createTableStatement.getTableElementList();
        Iterator<SQLTableElement> it = sqlTableElementList.iterator();
        String autoColumnDefinedWithoutKey = null;
        Set<String> keyColumnSet = Sets.newHashSet();
        Set<String> keySet = Sets.newHashSet();
        while (it.hasNext()) {
            SQLTableElement el = it.next();
            if (el instanceof SQLColumnDefinition) {
                SQLColumnDefinition definition = (SQLColumnDefinition) el;
                definition.setSequenceType(null);
                definition.setLogical(false);
                definition.setVirtual(false);
                definition.setStored(false);
                definition.setExternalize(false);
                definition.setGeneratedAlawsAs(null);
                definition.setUnitCount(null);
                definition.setUnitIndex(null);
                definition.setStep(null);
                convertVectorToVarbinary(definition);
                if (definition.isAutoIncrement() && !definition.isPrimaryKey() &&
                    !isColumnDefContainsUnique(definition)) {
                    autoColumnDefinedWithoutKey = definition.getColumnName();
                }
                if (SystemDB.isDrdsImplicitId(definition.getName().getSimpleName())) {
                    it.remove();
                }
            }
            if (el instanceof MySqlPrimaryKey) {
                MySqlPrimaryKey primaryKey = (MySqlPrimaryKey) el;
                SQLIndexDefinition indexDefinition = primaryKey.getIndexDefinition();
                keyColumnSet.add(indexDefinition.getColumns().get(0).toString());
                if (hasImplicitPk(indexDefinition)) {
                    it.remove();
                    continue;
                }
                if (primaryKey.getName() != null && SystemDB.isDrdsImplicitId(primaryKey.getName().getSimpleName())) {
                    it.remove();
                    continue;
                }
            }
            if (el instanceof MySqlUnique) {
                MySqlUnique unique = (MySqlUnique) el;
                unique.getIndexDefinition().setIndex(false);
                unique.getIndexDefinition().setKey(true);
                reformatIndex(unique.getIndexDefinition());
                keyColumnSet.add(unique.getIndexDefinition().getColumns().get(0).toString());
                if (unique.getName() != null) {
                    keySet.add(SQLUtils.normalize(unique.getName().getSimpleName()));
                }
            }

            if (el instanceof MySqlTableIndex) {
                MySqlTableIndex tableIndex = (MySqlTableIndex) el;
                if ("VECTOR".equalsIgnoreCase(tableIndex.getIndexDefinition().getType())) {
                    it.remove();
                    continue;
                }
                tableIndex.getIndexDefinition().setKey(true);
                tableIndex.getIndexDefinition().setIndex(false);
                reformatIndex(tableIndex.getIndexDefinition());
                keyColumnSet.add(tableIndex.getIndexDefinition().getColumns().get(0).toString());
                if (tableIndex.getName() != null) {
                    keySet.add(SQLUtils.normalize(tableIndex.getName().getSimpleName()));
                }

                String indexType = tableIndex.getIndexDefinition().getOptions().getIndexType();
                if (indexType != null && indexType.equalsIgnoreCase("HASH")) {
                    for (SQLSelectOrderByItem item : tableIndex.getIndexDefinition().getColumns()) {
                        if (item.getType() != null) {
                            if (item.getType().name().equalsIgnoreCase("ASC")) {
                                item.setType(null);
                            } else if (item.getType().name().equalsIgnoreCase("DESC")) {
                                item.setType(null);
                            }
                        }
                    }
                }
            }
            if (el instanceof MySqlKey) {
                MySqlKey mySqlKey = (MySqlKey) el;
                reformatIndex(mySqlKey.getIndexDefinition());
                keyColumnSet.add(mySqlKey.getIndexDefinition().getColumns().get(0).toString());
                if (mySqlKey.getName() != null) {
                    keySet.add(SQLUtils.normalize(mySqlKey.getName().getSimpleName()));
                }
                String indexType = mySqlKey.getIndexDefinition().getOptions().getIndexType();
                if (indexType != null && indexType.equalsIgnoreCase("HASH")) {
                    for (SQLSelectOrderByItem item : mySqlKey.getIndexDefinition().getColumns()) {
                        if (item.getType() != null) {
                            if (item.getType().name().equalsIgnoreCase("ASC")) {
                                item.setType(null);
                            } else if (item.getType().name().equalsIgnoreCase("DESC")) {
                                item.setType(null);
                            }
                        }
                    }
                }
            }
        }

        if (StringUtils.isNotBlank(autoColumnDefinedWithoutKey) && !keyColumnSet.contains(
            autoColumnDefinedWithoutKey)) {
            MySqlKey key = new MySqlKey();
            key.addColumn(new SQLSelectOrderByItem(new SQLIdentifierExpr(autoColumnDefinedWithoutKey)));
            sqlTableElementList.add(key);
        }
        tryAddAutoShardIndex(createTableStatement, ddlRecordSql, keySet);
        hack4RepairTableName(tableName, createTableStatement);
        removeTtlOption(createTableStatement);
    }

    private static boolean isColumnDefContainsUnique(SQLColumnDefinition columnDefinition) {
        for (SQLColumnConstraint constraint : columnDefinition.getConstraints()) {
            if (constraint instanceof SQLColumnUniqueKey || constraint instanceof SQLColumnPrimaryKey) {
                return true;
            }
        }
        return false;
    }

    //@see historical compatibility behavior
    private static void tryAddAutoShardIndex(MySqlCreateTableStatement createTableStatement, String ddlRecordSql,
                                             Set<String> keySet) {
        if (StringUtils.isBlank(ddlRecordSql)) {
            return;
        }

        if (createTableStatement.getLike() != null) {
            return;
        }

        MySqlCreateTableStatement baseCreateTableStmt =
            com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement(ddlRecordSql);
        if (baseCreateTableStmt == null) {
            return;
        }

        baseCreateTableStmt.getTableElementList().forEach(e -> {
            String indexName = "";
            if (e instanceof MySqlUnique) {
                MySqlUnique unique = (MySqlUnique) e;
                indexName = unique.getName() != null ? SQLUtils.normalize(unique.getName().getSimpleName()) : "";
            } else if (e instanceof MySqlTableIndex) {
                MySqlTableIndex tableIndex = (MySqlTableIndex) e;
                indexName = tableIndex.getName() != null ?
                    SQLUtils.normalize(tableIndex.getName().getSimpleName()) : "";
            } else if (e instanceof MySqlKey) {
                MySqlKey mySqlKey = (MySqlKey) e;
                indexName = mySqlKey.getName() != null ? SQLUtils.normalize(mySqlKey.getName().getSimpleName()) : "";
            }

            boolean isAutoShardKey = isAutoShardKey(indexName);
            if (isAutoShardKey && !keySet.contains(indexName)) {
                createTableStatement.getTableElementList().add(e);
            }
        });
    }

    private static void normalizeAlterTable(SQLAlterTableStatement sqlAlterTableStatement) {
        sqlAlterTableStatement.setAlignToTableGroup(null);
        sqlAlterTableStatement.setTargetImplicitTableGroup(null);
        sqlAlterTableStatement.getIndexTableGroupPair().clear();
        List<SQLAlterTableItem> items = sqlAlterTableStatement.getItems();
        Iterator<SQLAlterTableItem> iterator = items.iterator();

        while (iterator.hasNext()) {
            SQLAlterTableItem item = iterator.next();
            if (item instanceof SQLAlterTableAddIndex) {
                SQLAlterTableAddIndex addIndex = (SQLAlterTableAddIndex) item;
                if ("VECTOR".equalsIgnoreCase(addIndex.getIndexDefinition().getType())) {
                    iterator.remove();
                    continue;
                }
                reformatIndex(addIndex.getIndexDefinition());
                SQLIndexOptions sqlIndexOptions = addIndex.getIndexDefinition().getOptions();
                if (sqlIndexOptions != null) {
                    if ("OMC".equalsIgnoreCase(sqlIndexOptions.getAlgorithm())) {
                        sqlIndexOptions.setAlgorithm(null);
                    }
                    if (sqlIndexOptions.getIndexType() != null) {
                        sqlIndexOptions.setIndexType(null);
                    }
                }
            }

            if (item instanceof DrdsAlterTableModifyTtlOptions) {
                iterator.remove();
            }

            if (item instanceof SQLAlterTableAddConstraint) {
                SQLConstraint constraint = ((SQLAlterTableAddConstraint) item).getConstraint();
                if (constraint instanceof MySqlUnique) {
                    MySqlUnique mySqlUnique = ((MySqlUnique) constraint);
                    reformatIndex(mySqlUnique.getIndexDefinition());
                    SQLIndexOptions sqlIndexOptions = mySqlUnique.getIndexDefinition().getOptions();
                    if (sqlIndexOptions != null && "OMC".equalsIgnoreCase(sqlIndexOptions.getAlgorithm())) {
                        sqlIndexOptions.setAlgorithm(null);
                    }
                }
                if (constraint instanceof MySqlPrimaryKey) {
                    MySqlPrimaryKey primaryKey = (MySqlPrimaryKey) constraint;
                    SQLIndexOptions sqlIndexOptions = primaryKey.getIndexDefinition().getOptions();
                    primaryKey.getIndexDefinition().setCovering(Lists.newArrayList());
                    if (sqlIndexOptions != null && "OMC".equalsIgnoreCase(sqlIndexOptions.getAlgorithm())) {
                        sqlIndexOptions.setAlgorithm(null);
                    }
                }
            }

            if (item instanceof MySqlAlterTableModifyColumn) {
                MySqlAlterTableModifyColumn modifyColumn = (MySqlAlterTableModifyColumn) item;
                modifyColumn.getNewColumnDefinition().setSequenceType(null);
                modifyColumn.getNewColumnDefinition().setLogical(false);
                modifyColumn.getNewColumnDefinition().setVirtual(false);
                modifyColumn.getNewColumnDefinition().setStored(false);
                modifyColumn.getNewColumnDefinition().setExternalize(false);
                modifyColumn.getNewColumnDefinition().setGeneratedAlawsAs(null);
                deduplicateCharsetExpr(modifyColumn.getNewColumnDefinition());
                convertVectorToVarbinary(modifyColumn.getNewColumnDefinition());
            }

            if (item instanceof SQLAlterTableAddColumn) {
                SQLAlterTableAddColumn alterTableAlterColumn = (SQLAlterTableAddColumn) item;
                alterTableAlterColumn.getColumns().forEach(c -> {
                    // 对于生成列，我们的策略是对下游单机mysql透明，即会隐藏掉生成列的特性
                    // 当转换为单机形态ddl sql时，如果新增的列是生成列，需要将unique属性去掉，否则下游会报错
                    if ((c.isLogical() || c.isVirtual() || c.isStored()) && c.getGeneratedAlawsAs() != null) {
                        c.getConstraints().removeIf(cst -> cst instanceof SQLColumnUniqueKey);
                    }
                    c.setSequenceType(null);
                    c.setGeneratedAlawsAs(null);
                    c.setLogical(false);
                    c.setVirtual(false);
                    c.setStored(false);
                    c.setExternalize(false);
                    deduplicateCharsetExpr(c);
                    convertVectorToVarbinary(c);
                });
            }

            tryRemoveDropImplicitPk(item);

            if (item instanceof MySqlAlterTableOption) {
                MySqlAlterTableOption option = (MySqlAlterTableOption) item;
                String optionName = option.getName();
                if ("ALGORITHM".equalsIgnoreCase(optionName)) {
                    if (option.getValue() instanceof SQLIdentifierExpr) {
                        SQLIdentifierExpr identifierExpr = (SQLIdentifierExpr) option.getValue();
                        if (getAlgorithmBlacklist().contains(StringUtils.lowerCase(identifierExpr.getSimpleName()))) {
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    // 包级可见，便于单测直接对 charset/collate 归一化与补全逻辑做验证
    static void tryAttacheCharacterInfo(MySqlCreateTableStatement createTableStatement, String tbCollation) {
        boolean isLike = createTableStatement.getLike() != null;
        List<SQLAssignItem> optionItemList = createTableStatement.getTableOptions();
        Set<String> optionSet = new HashSet<>();
        String optionCharset = null;

        for (SQLAssignItem i : optionItemList) {
            String option = StringUtils.upperCase(SQLUtils.normalize(i.getTarget().toString()));
            optionSet.add(option);
            if (!StringUtils.equalsAny(option, "CHARACTER SET", "CHARACTER", "CHARSET", "COLLATE")) {
                continue;
            }
            // 只有取值是单纯的名字或字符串字面量时才做归一化，其它表达式（如 SQLBinaryOpExpr）保持原样，
            // 避免把整段表达式拍平成一个标识符，破坏语句结构
            if (!(i.getValue() instanceof SQLIdentifierExpr) && !(i.getValue() instanceof SQLCharExpr)) {
                continue;
            }
            i.setValue(buildCharacterOptionValue(i.getValue().toString()));
            if (!StringUtils.equals(option, "COLLATE")) {
                optionCharset = SQLUtils.normalize(i.getValue().toString());
            }
        }
        if (!isLike && StringUtils.isNotBlank(tbCollation)) {
            String charset = CharsetConversion.getCharsetByCollation(tbCollation);
            if (!optionSet.contains("CHARACTER") && !optionSet.contains("CHARSET") && !optionSet.contains(
                "CHARACTER SET") && StringUtils.isNotBlank(charset)) {
                createTableStatement.addOption("CHARACTER SET", buildCharacterOptionValue(charset));
            }

            if (!optionSet.contains("COLLATE")) {
                if (StringUtils.isBlank(optionCharset) || optionCharset.equalsIgnoreCase(charset)) {
                    // 仅在以下情况补全collate信息：
                    // 1. DDL中未显式指定charset
                    // 2. 或者DDL中显式指定的charset与tbCollation对应的charset相同
                    createTableStatement.addOption("COLLATE", buildCharacterOptionValue(tbCollation));
                }
            }
        }
    }

    static void tryRemoveDropImplicitPk(SQLAlterTableItem item) {
        if (item instanceof SQLAlterTableDropColumnItem) {
            SQLAlterTableDropColumnItem dropColumnItem = (SQLAlterTableDropColumnItem) item;
            if (dropColumnItem.getColumns() != null) {
                dropColumnItem.getColumns().removeIf(sqlName -> SystemDB.isDrdsImplicitId(sqlName.getSimpleName()));
            }
        }
    }

    private static void removeLocalityInGsiForCreateTable(SQLTableElement element) {
        if (element instanceof MySqlTableIndex) {
            MySqlTableIndex tableIndex = (MySqlTableIndex) element;
            if (tableIndex.getPartitioning() != null) {
                tableIndex.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
            }
        } else if (element instanceof MySqlKey) {
            if (!(element instanceof MySqlPrimaryKey)) {
                if (element instanceof MySqlUnique) {
                    MySqlUnique mySqlUnique = (MySqlUnique) element;
                    if (mySqlUnique.getPartitioning() != null) {
                        mySqlUnique.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
                    }
                } else {
                    MySqlKey mySqlKey = (MySqlKey) element;
                    if (mySqlKey.getIndexDefinition().getPartitioning() != null) {
                        mySqlKey.getIndexDefinition().getPartitioning().getPartitions()
                            .forEach(p -> p.setLocality(null));
                    }
                }
            }
        }
    }

    private static void removeLocalityInGsiForAlterTable(SQLAlterTableItem item) {
        if (item instanceof SQLAlterTableAddIndex) {
            SQLAlterTableAddIndex addIndex = (SQLAlterTableAddIndex) item;
            if (addIndex.getPartitioning() != null) {
                addIndex.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
            }
        } else if (item instanceof SQLAlterTableAddConstraint) {
            SQLConstraint constraint = ((SQLAlterTableAddConstraint) item).getConstraint();
            if (constraint instanceof MySqlUnique) {
                MySqlUnique mySqlUnique = ((MySqlUnique) constraint);
                if (mySqlUnique.getPartitioning() != null) {
                    mySqlUnique.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
                }
            } else if (constraint instanceof MySqlTableIndex) {
                MySqlTableIndex tableIndex = (MySqlTableIndex) constraint;
                if (tableIndex.getPartitioning() != null) {
                    tableIndex.getPartitioning().getPartitions().forEach(p -> p.setLocality(null));
                }
            }
        }
    }

    private static void reformatIndex(SQLIndexDefinition indexDefinition) {
        indexDefinition.setClustered(false);
        indexDefinition.setGlobal(false);
        indexDefinition.setLocal(false);
        indexDefinition.setPartitioning(null);
        indexDefinition.setDbPartitionBy(null);
        indexDefinition.setTbPartitionBy(null);
        indexDefinition.setTbPartitions(null);
        indexDefinition.setCovering(Lists.newArrayList());
        indexDefinition.setTableGroup(null);
        indexDefinition.setWithImplicitTablegroup(false);
        indexDefinition.setVisible(true);
        indexDefinition.setColumnar(false);
        indexDefinition.setWithDicName(null);
        indexDefinition.getOptions().setDictionaryColumns(null);
    }

    static boolean isAutoShardKey(String indexName) {
        if (indexName != null && indexName.startsWith("`")) {
            indexName = indexName.substring(1);
        }
        return StringUtils.startsWithIgnoreCase(indexName, "auto_shard_key");
    }

    /**
     * 从 DROP INDEX / ALTER TABLE DROP INDEX 语句中提取被 DROP 的索引名列表。
     */
    public static Set<String> extractDroppedIndexNames(String sql) {
        Set<String> names = new HashSet<>();
        try {
            SQLStatement sqlStatement = parseSQLStatement(sql);
            if (sqlStatement instanceof SQLDropIndexStatement) {
                SQLDropIndexStatement stmt = (SQLDropIndexStatement) sqlStatement;
                names.add(SQLUtils.normalize(stmt.getIndexName().getSimpleName()));
            } else if (sqlStatement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement stmt = (SQLAlterTableStatement) sqlStatement;
                for (SQLAlterTableItem item : stmt.getItems()) {
                    if (item instanceof SQLAlterTableDropIndex) {
                        names.add(SQLUtils.normalize(((SQLAlterTableDropIndex) item).getIndexName().getSimpleName()));
                    }
                }
            }
        } catch (Throwable t) {
            log.error("extractDroppedIndexNames failed, sql: " + sql, t);
        }
        return names;
    }

    /**
     * 从 SQL 中移除指定索引的 DROP 操作（纯 SQL 改写，无业务逻辑）。
     * <p>
     * 调用方提前确定哪些索引的 DROP 需要被抑制，将其名称放入 indexNamesToSuppress。
     *
     * @param indexNamesToSuppress 需要被抑制的索引名集合（大小写不敏感匹配）
     * @return 改写后的 SQL；整条被抑制则返回 null；无需改写则返回原 sql
     */
    public static String tryRemoveDropIndex(String sql, Set<String> indexNamesToSuppress) {
        if (indexNamesToSuppress == null || indexNamesToSuppress.isEmpty()) {
            return sql;
        }
        try {
            SQLStatement sqlStatement = parseSQLStatement(sql);

            if (sqlStatement instanceof SQLDropIndexStatement) {
                SQLDropIndexStatement dropIndexStatement = (SQLDropIndexStatement) sqlStatement;
                String indexName = SQLUtils.normalize(dropIndexStatement.getIndexName().getSimpleName());
                if (indexNamesToSuppress.stream().anyMatch(n -> StringUtils.equalsIgnoreCase(n, indexName))) {
                    log.info("skip drop index sql, index: {}, sql: {}", indexName, sql);
                    return null;
                }
            } else if (sqlStatement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
                if (!sqlAlterTableStatement.getItems().isEmpty()) {
                    boolean changeFlag = false;
                    Iterator<SQLAlterTableItem> iterator = sqlAlterTableStatement.getItems().iterator();
                    while (iterator.hasNext()) {
                        SQLAlterTableItem alterTableItem = iterator.next();
                        if (alterTableItem instanceof SQLAlterTableDropIndex) {
                            SQLAlterTableDropIndex dropIndex = (SQLAlterTableDropIndex) alterTableItem;
                            String indexName = SQLUtils.normalize(dropIndex.getIndexName().getSimpleName());
                            if (indexNamesToSuppress.stream()
                                .anyMatch(n -> StringUtils.equalsIgnoreCase(n, indexName))) {
                                log.info("skip drop index item, index: {}", indexName);
                                iterator.remove();
                                changeFlag = true;
                            }
                        }
                    }
                    if (changeFlag) {
                        String newSql = sqlAlterTableStatement.toUnformattedString();
                        try {
                            parseSQLStatement(newSql);
                        } catch (Throwable t) {
                            log.error("skip drop index sql " + sql);
                            return null;
                        }
                        log.info("rewrite drop index sql, before: {}, after: {}", sql, newSql);
                        return newSql;
                    }
                }
            }
            return sql;
        } catch (Throwable t) {
            log.error("try rewrite drop index sql error!", t);
            throw t;
        }
    }

    /**
     * 构造 CHARACTER SET / COLLATE 选项的值表达式。
     * 部分 charset、collation 名同时也是 SQL 保留字（如 binary 对应 token BINARY），
     * 归一化为裸词后 Druid 再次解析该 DDL 时会把它识别为 BINARY token，表现为两种故障：
     * 1. 抛 ParserException（如 DEFAULT CHARSET = binary ROW_FORMAT = Dynamic）；
     * 2. 静默吞并后续 option（如 DEFAULT CHARSET = binary DEFAULT COLLATE = `binary` 会被解析成
     * 一个值为 SQLUnaryExpr 的 CHARSET 选项，COLLATE 选项直接丢失，charset 元数据被污染）。
     * 故对这类保留字保留反引号，确保 reformat 产出的 DDL 可被再次解析且语义不变。
     * MySQL 侧对 charset/collation 名支持裸词、反引号、字符串字面量三种写法，加反引号不影响下游消费。
     * 需要保留反引号的保留字通过配置 {@code task_reformat_ddl_character_quote_keywords} 维护，
     * 后续新增同类保留字只需改配置。
     */
    private static SQLIdentifierExpr buildCharacterOptionValue(String rawValue) {
        String normalized = SQLUtils.normalize(rawValue);
        if (getCharacterQuoteKeywords().contains(StringUtils.lowerCase(normalized))) {
            return new SQLIdentifierExpr("`" + normalized + "`");
        }
        return new SQLIdentifierExpr(normalized);
    }

    private static Set<String> getCharacterQuoteKeywords() {
        String configValue = DynamicApplicationConfig.getString(TASK_REFORMAT_DDL_CHARACTER_QUOTE_KEYWORDS);
        if (StringUtils.isNotBlank(configValue)) {
            String[] splitValues = StringUtils.split(configValue.toLowerCase(), ",");
            return Sets.newHashSet(splitValues);
        }
        return new HashSet<>();
    }

    private static boolean hasImplicitPk(SQLIndexDefinition indexDefinition) {
        if (indexDefinition != null) {
            List<SQLSelectOrderByItem> columns = indexDefinition.getColumns();
            for (SQLSelectOrderByItem item : columns) {
                if (SystemDB.isDrdsImplicitId(item.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    //hack reason : historical compatibility behavior
    private static void hack4RepairTableName(String tableName, SQLCreateTableStatement createTableStatement) {
        if (StringUtils.isBlank(tableName)) {
            return;
        }

        String tableNameInSql = createTableStatement.getTableName();
        String tableNameInSqlNormal = SQLUtils.normalizeNoTrim(tableNameInSql);

        if (!StringUtils.equals(tableName, tableNameInSqlNormal)) {
            createTableStatement.setTableName("`" + escape(tableName) + "`");
            log.warn("repair table name in create sql, before : {}, after :{}", tableNameInSql, tableName);
        }
    }

    private static void removeTtlOption(SQLCreateTableStatement createTableStatement) {
        createTableStatement.getTableOptions().removeIf(
            item -> item.getTarget() != null && StringUtils.equalsIgnoreCase(item.getTarget().toString(), "TTL"));
    }

    /**
     * 去掉DDL语句上的异步执行标志（async=true）。
     * 带async=true的DDL（如 ALTER TABLE ADD INDEX xxx async=true）
     * 在输出到binlog时需要去掉异步特性，以避免影响DTS等下游同步工具。
     */
    static void removeAsyncDdlFlags(SQLStatement sqlStatement) {
        if (sqlStatement instanceof SQLStatementImpl) {
            SQLStatementImpl stmtImpl = (SQLStatementImpl) sqlStatement;
            if (stmtImpl.getAsync() != null) {
                stmtImpl.setAsync(null);
            }
        }
    }

    private static Set<String> getAlgorithmBlacklist() {
        String configValue = DynamicApplicationConfig.getString(TASK_REFORMAT_DDL_ALGORITHM_BLACKLIST);
        if (StringUtils.isNotBlank(configValue)) {
            String[] splitValues = StringUtils.split(configValue.toLowerCase(), ",");
            return Sets.newHashSet(splitValues);
        }
        return new HashSet<>();
    }

    private static void convertVectorToVarbinary(SQLColumnDefinition columnDefinition) {
        SQLDataType dataType = columnDefinition.getDataType();
        if (dataType == null || !"VECTOR".equalsIgnoreCase(dataType.getName())) {
            return;
        }
        int dimension = 0;
        if (!dataType.getArguments().isEmpty()) {
            com.alibaba.polardbx.druid.sql.ast.SQLExpr arg = dataType.getArguments().get(0);
            if (arg instanceof SQLIntegerExpr) {
                dimension = ((SQLIntegerExpr) arg).getNumber().intValue();
            }
        }
        dataType.setName("VARBINARY");
        dataType.getArguments().clear();
        if (dimension > 0) {
            // VECTOR(N) stores N float32 values, each 4 bytes
            dataType.getArguments().add(new SQLIntegerExpr(dimension * 4));
        }
    }

    /**
     * Druid解析含 CHARACTER SET 的生成列时，会将字符集信息同时存储在 SQLCharacterDataType.charSetName
     * 和 SQLColumnDefinition.charsetExpr 两处。移除 GENERATED ALWAYS AS 后序列化时两处均会输出，导致重复。
     * 此方法在数据类型已携带 charset 时清除列定义级别的 charsetExpr，避免重复输出。
     */
    private static void deduplicateCharsetExpr(SQLColumnDefinition columnDef) {
        if (columnDef.getCharsetExpr() != null
            && columnDef.getDataType() instanceof SQLCharacterDataType
            && StringUtils.isNotBlank(((SQLCharacterDataType) columnDef.getDataType()).getCharSetName())) {
            columnDef.setCharsetExpr(null);
        }
    }

    private static String indexName(SQLIndexDefinition indexDefinition) {
        if (indexDefinition.getName() == null) {
            return StringUtils.join(
                indexDefinition.getColumns().stream().map(s -> s.toString()).collect(Collectors.toList()), "_");
        } else {
            return indexDefinition.getName().toString();
        }
    }
}
