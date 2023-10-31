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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexDefinition;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexOptions;
import com.alibaba.polardbx.druid.sql.ast.SQLPartitionBy;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.DrdsMovePartition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddColumn;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropColumnItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableSetOption;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnUniqueKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_DDL_ALGORITHM_BLACKLIST;
import static com.aliyun.polardbx.binlog.DynamicApplicationVersionConfig.getBoolean;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

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

    public static String buildDdlEventSql(String polarxDDL, String dbCharset, String tbCollation, String tso) {
        return buildDdlEventSql(null, polarxDDL, dbCharset, tbCollation, tso, null);
    }

    public static String buildDdlEventSql(String tableName, String polarxDDL, String dbCharset, String tbCollation,
                                          String tso) {
        return buildDdlEventSql(tableName, polarxDDL, dbCharset, tbCollation, tso, null);
    }

    public static String buildDdlEventSql(String tableName, String ddlSqlForPolar, String dbCharset, String tbCollation,
                                          String tso, String ddlSqlForMysql) {
        StringBuilder sqlBuilder = new StringBuilder();
        buildDdlEventSqlForPolarPart(sqlBuilder, ddlSqlForPolar, dbCharset, tbCollation, tso);
        buildDdlEventSqlForMysqlPart(sqlBuilder, tableName, dbCharset, tbCollation, ddlSqlForMysql);
        return sqlBuilder.toString();
    }

    static void buildDdlEventSqlForPolarPart(StringBuilder sqlBuilder, String ddlSqlForPolar, String dbCharset,
                                             String tbCollation, String tso) {
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
            createTableStatement.setTableGroup(null);
            createTableStatement.setJoinGroup(null);
            tryAttacheCharacterInfo(createTableStatement, tbCollation);

            SQLPartitionBy sqlPartitionBy = createTableStatement.getPartitioning();
            if (sqlPartitionBy != null && sqlPartitionBy.getPartitions() != null) {
                sqlPartitionBy.getPartitions().forEach(p -> p.setLocality(null));
            }
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement alterTableStatement = (SQLAlterTableStatement) sqlStatement;
            // 暂时屏蔽align to tableGroup
            alterTableStatement.setAlignToTableGroup(null);
            if (alterTableStatement.getPartition() != null
                && alterTableStatement.getPartition().getPartitions() != null) {
                alterTableStatement.getPartition().getPartitions().forEach(p -> {
                    p.setLocality(null);
                });
            }
            if (alterTableStatement.getItems() != null) {
                alterTableStatement.getItems().removeIf(item -> item instanceof DrdsMovePartition);
                alterTableStatement.getItems().removeIf(i -> {
                    if (i instanceof SQLAlterTableSetOption) {
                        SQLAlterTableSetOption setOption = (SQLAlterTableSetOption) i;
                        return setOption.isAlterTableGroup();
                    }
                    return false;
                });

                //remove _drds_implicit_id_
                alterTableStatement.getItems().forEach(DDLConverter::tryRemoveDropImplicitPk);
            }
        }

        String privateDdlSql = SQLUtils.toSQLString(sqlStatement, DbType.mysql,
            new SQLUtils.FormatOption(true, false));
        privateDdlSql = StringUtils.replace(privateDdlSql, "\n", "");
        sqlBuilder.append(CommonUtils.PRIVATE_DDL_DDL_PREFIX).append(privateDdlSql).append("\n");
        sqlBuilder.append(CommonUtils.PRIVATE_DDL_TSO_PREFIX).append(tso).append("\n");
    }

    static void buildDdlEventSqlForMysqlPart(StringBuilder sqlBuilder, String tableName, String dbCharset,
                                             String tbCollation,
                                             String ddlSqlForNormalMysql) {
        if (StringUtils.isBlank(ddlSqlForNormalMysql)) {
            return;
        }

        SQLStatement sqlStatement = parseSQLStatement(ddlSqlForNormalMysql);
        if (sqlStatement instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) sqlStatement;
            createDatabaseStatement.setPartitionMode(null);
            createDatabaseStatement.setDefaultSingle(null);
            createDatabaseStatement.setLocality(null);
            createDatabaseStatement.setCharacterSet(dbCharset);
        } else if (sqlStatement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
            normalizeCreateTable(tableName, tbCollation, createTableStatement);
            sqlBuilder.append(createTableStatement.toUnformattedString());
            return;
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
            normalizeAlterTable(sqlAlterTableStatement);
        } else if (sqlStatement instanceof SQLCreateIndexStatement) {
            SQLCreateIndexStatement sqlCreateIndexStatement = (SQLCreateIndexStatement) sqlStatement;
            sqlCreateIndexStatement.getIndexDefinition().setKey(true);
            reformatIndex(sqlCreateIndexStatement.getIndexDefinition());
        } else if (sqlStatement instanceof SQLDropTableStatement) {
            SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
            sqlDropTableStatement.setPurge(false);
        }

        sqlBuilder.append(SQLUtils.toSQLString(sqlStatement, DbType.mysql,
            new SQLUtils.FormatOption(true, false)));
    }

    private static void normalizeCreateTable(String tableName, String tbCollation,
                                             MySqlCreateTableStatement createTableStatement) {
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

        // try attache character info
        tryAttacheCharacterInfo(createTableStatement, tbCollation);

        // 寻找自增主键, 去掉sequence type
        List<SQLTableElement> sqlTableElementList = createTableStatement.getTableElementList();
        Iterator<SQLTableElement> it = sqlTableElementList.iterator();
        String autoColumn = null;
        Set<String> keySet = Sets.newHashSet();
        while (it.hasNext()) {
            SQLTableElement el = it.next();
            if (el instanceof SQLColumnDefinition) {
                SQLColumnDefinition definition = (SQLColumnDefinition) el;
                definition.setSequenceType(null);
                definition.setLogical(false);
                definition.setVirtual(false);
                definition.setStored(false);
                definition.setGeneratedAlawsAs(null);
                if (definition.isAutoIncrement()) {
                    autoColumn = definition.getColumnName();
                }
                if (SystemDB.isDrdsImplicitId(definition.getName().getSimpleName())) {
                    it.remove();
                }
            }
            if (el instanceof MySqlPrimaryKey) {
                MySqlPrimaryKey primaryKey = (MySqlPrimaryKey) el;
                SQLIndexDefinition indexDefinition = primaryKey.getIndexDefinition();
                keySet.add(indexDefinition.getColumns().get(0).toString());
                if (hasImplicitPk(indexDefinition)) {
                    it.remove();
                    continue;
                }
                if (primaryKey.getName() != null && SystemDB.isDrdsImplicitId(primaryKey.getName()
                    .getSimpleName())) {
                    it.remove();
                    continue;
                }
            }
            if (el instanceof MySqlUnique) {
                MySqlUnique unique = (MySqlUnique) el;
                unique.getIndexDefinition().setIndex(false);
                unique.getIndexDefinition().setKey(true);
                reformatIndex(unique.getIndexDefinition());
                keySet.add(unique.getIndexDefinition().getColumns().get(0).toString());
            }

            if (el instanceof MySqlTableIndex) {
                MySqlTableIndex tableIndex = (MySqlTableIndex) el;
                tableIndex.getIndexDefinition().setKey(true);
                tableIndex.getIndexDefinition().setIndex(false);
                reformatIndex(tableIndex.getIndexDefinition());
                keySet.add(tableIndex.getIndexDefinition().getColumns().get(0).toString());
            }
            if (el instanceof MySqlKey) {
                MySqlKey mySqlKey = (MySqlKey) el;
                reformatIndex(mySqlKey.getIndexDefinition());
                keySet.add(mySqlKey.getIndexDefinition().getColumns().get(0).toString());
            }
        }
        if (StringUtils.isNotBlank(autoColumn) && !keySet.contains(autoColumn)) {
            MySqlKey key = new MySqlKey();
            key.addColumn(new SQLSelectOrderByItem(new SQLIdentifierExpr(autoColumn)));
            sqlTableElementList.add(key);
        }
        hack4RepairTableName(tableName, createTableStatement);
    }

    private static void normalizeAlterTable(SQLAlterTableStatement sqlAlterTableStatement) {
        sqlAlterTableStatement.setAlignToTableGroup(null);
        List<SQLAlterTableItem> items = sqlAlterTableStatement.getItems();
        Iterator<SQLAlterTableItem> iterator = items.iterator();

        while (iterator.hasNext()) {
            SQLAlterTableItem item = iterator.next();
            if (item instanceof SQLAlterTableAddIndex) {
                SQLAlterTableAddIndex addIndex = (SQLAlterTableAddIndex) item;
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
                modifyColumn.getNewColumnDefinition().setGeneratedAlawsAs(null);
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
                });
            }

            tryRemoveDropImplicitPk(item);

            if (item instanceof MySqlAlterTableOption) {
                MySqlAlterTableOption option = (MySqlAlterTableOption) item;
                String optionName = option.getName();
                if ("ALGORITHM".equalsIgnoreCase(optionName)) {
                    if (option.getValue() instanceof SQLIdentifierExpr) {
                        SQLIdentifierExpr identifierExpr = (SQLIdentifierExpr) option.getValue();
                        if (getAlgorithmBlacklist()
                            .contains(StringUtils.lowerCase(identifierExpr.getSimpleName()))) {
                            iterator.remove();
                        }
                    }
                }
            }
        }
    }

    private static void tryAttacheCharacterInfo(MySqlCreateTableStatement createTableStatement, String tbCollation) {
        boolean isLike = createTableStatement.getLike() != null;
        List<SQLAssignItem> optionItemList = createTableStatement.getTableOptions();
        Set<String> optionSet = new HashSet<>();

        for (SQLAssignItem i : optionItemList) {
            String option = StringUtils.upperCase(SQLUtils.normalize(i.getTarget().toString()));
            optionSet.add(option);
            String oldValue = i.getValue().toString();
            if (i.getValue() instanceof SQLBinaryOpExpr) {
                SQLBinaryOpExpr opExpr = (SQLBinaryOpExpr) i.getValue();
                String operator = StringUtils.upperCase(SQLUtils.normalize(opExpr.getOperator().toString()));
                if (!StringUtils.equalsAny(operator, "CHARACTER SET", "CHARACTER", "CHARSET", "COLLATE")) {
                    continue;
                }
                optionSet.add(operator);
                SQLIdentifierExpr right = tryToNormalize(opExpr.getRight().toString());
                if (right != null) {
                    opExpr.setRight(right);
                }
                continue;
            }
            if (!StringUtils.equalsAny(option, "CHARACTER SET", "CHARACTER", "CHARSET", "COLLATE")) {
                continue;
            }
            SQLIdentifierExpr value = tryToNormalize(oldValue);
            if (value != null) {
                i.setValue(value);
            }
        }
        if (!isLike && StringUtils.isNotBlank(tbCollation)) {
            String charset = CharsetConversion.getCharsetByCollation(tbCollation);
            if (!optionSet.contains("CHARACTER") &&
                !optionSet.contains("CHARSET") &&
                !optionSet.contains("CHARACTER SET") &&
                StringUtils.isNotBlank(charset)) {
                createTableStatement.addOption("CHARACTER SET", new SQLIdentifierExpr(charset));
            }

            if (!optionSet.contains("COLLATE")) {
                createTableStatement.addOption("COLLATE", new SQLIdentifierExpr(tbCollation));
            }
        }
    }

    static void tryRemoveDropImplicitPk(SQLAlterTableItem item) {
        if (item instanceof SQLAlterTableDropColumnItem) {
            SQLAlterTableDropColumnItem dropColumnItem = (SQLAlterTableDropColumnItem) item;
            if (dropColumnItem.getColumns() != null) {
                dropColumnItem.getColumns()
                    .removeIf(sqlName -> SystemDB.isDrdsImplicitId(sqlName.getSimpleName()));
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
    }

    private static boolean isAutoShardKey(String indexName) {
        if (indexName.startsWith("`")) {
            indexName = indexName.substring(1);
        }
        return StringUtils.startsWithIgnoreCase(indexName, "auto_shard_key");
    }

    private static SQLIdentifierExpr tryToNormalize(String value) {
        String normalizeValue = SQLUtils.normalize(value);
        if (!StringUtils.equalsIgnoreCase(normalizeValue, value)) {
            return new SQLIdentifierExpr(normalizeValue);
        }
        return null;
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

    //hack reason : https://aone.alibaba-inc.com/issue/36088240
    private static void hack4RepairTableName(String tableName, SQLCreateTableStatement createTableStatement) {
        if (StringUtils.isBlank(tableName)) {
            return;
        }

        String tableNameInSql = createTableStatement.getTableName();
        String tableNameInSqlNormal = SQLUtils.normalize(tableNameInSql);

        if (!StringUtils.equals(tableName, tableNameInSqlNormal)) {
            createTableStatement.setTableName("`" + escape(tableName) + "`");
            log.warn("repair table name in create sql, before : {}, after :{}", tableNameInSql, tableName);
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

    private static String indexName(SQLIndexDefinition indexDefinition) {
        if (indexDefinition.getName() == null) {
            return StringUtils
                .join(indexDefinition.getColumns().stream().map(s -> s.toString()).collect(Collectors.toList()), "_");
        } else {
            return indexDefinition.getName().toString();
        }
    }
}
