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
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddColumn;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableModifyColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableOption;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.LowerCaseTableNameVariables;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.aliyun.polardbx.binlog.CommonUtils.escape;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_CONVERTER_ALGORITHM_BLACKLIST;

public class DDLConverter {

    private static final Logger logger = LoggerFactory.getLogger(DDLConverter.class);

    private static String tryLowercase(String sql, int lowerCaseTableNames) {
        return lowerCaseTableNames
            == LowerCaseTableNameVariables.LOWERCASE.getValue() ? sql.toLowerCase() : sql;
    }

    public static String formatPolarxDDL(String polarxDDL, String dbCharset, String tbCollation,
                                         int lowerCaseTableNames) {
        return formatPolarxDDL(null, polarxDDL, dbCharset, tbCollation, lowerCaseTableNames);
    }

    public static String formatPolarxDDL(String tableName, String polarxDDL, String dbCharset, String tbCollation,
                                         int lowerCaseTableNames) {

        String ddl = polarxDDL;
        try {
            SQLStatementParser parser =
                SQLParserUtils.createSQLStatementParser(polarxDDL, DbType.mysql, FastSQLConstant.FEATURES);
            List<SQLStatement> statementList = parser.parseStatementList();
            SQLStatement statement = statementList.get(0);
            if (statement instanceof SQLCreateTableStatement) {
                SQLCreateTableStatement createTableStatement = (SQLCreateTableStatement) statement;
                if (StringUtils.isNotBlank(tbCollation)) {
                    String charset = CharsetConversion.getCharsetByCollation(tbCollation);
                    if (StringUtils.isNotBlank(charset)) {
                        createTableStatement.addOption("CHARACTER SET", new SQLIdentifierExpr(charset));
                    }
                    createTableStatement.addOption("COLLATE", new SQLIdentifierExpr(tbCollation));
                }
                hack4RepairTableName(tableName, createTableStatement, polarxDDL);
                ddl = createTableStatement.toString();
            } else if (statement instanceof SQLCreateDatabaseStatement) {
                SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                createDatabaseStatement.setCharacterSet(dbCharset);
                ddl = createDatabaseStatement.toString();
            }
            ddl = tryLowercase(ddl, lowerCaseTableNames);
        } catch (Exception e) {
            logger.error("parse ddl failed when format polarx ddl sql! ", e);
        }
        return ddl;
    }

    public static String convertNormalDDL(String polarxDDL, String dbCharset, String tbCollation,
                                          int lowerCaseTableNames, String tso) {
        return convertNormalDDL(null, polarxDDL, dbCharset, tbCollation, lowerCaseTableNames, tso);
    }

    /**
     * 目前polarx 特有的DDL 除建表之外，其他 拆分变更表和gsi 都会被过滤掉，
     * 其余 alter table 都是正常ddl，可以同步给mysql，这里只考虑create table语句。
     */
    public static String convertNormalDDL(String tableName, String polarxDDL, String dbCharset, String tbCollation,
                                          int lowerCaseTableNames, String tso) {

        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(polarxDDL, DbType.mysql, FastSQLConstant.FEATURES);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);

        StringBuilder commentsBuilder = new StringBuilder();
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DDL_PRIVATEDDL_SUPPORT)) {
            String privateDdlSql =
                SQLUtils.toSQLString(sqlStatement, DbType.mysql, new SQLUtils.FormatOption(true, false));
            commentsBuilder.append("# POLARX_ORIGIN_SQL=").append(privateDdlSql).append("\n");
            commentsBuilder.append("# POLARX_TSO=").append(tso).append("\n");
        }

        if (sqlStatement instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) sqlStatement;
            createDatabaseStatement.setPartitionMode(null);
            createDatabaseStatement.setLocality(null);
            createDatabaseStatement.setCharacterSet(dbCharset);
        } else if (sqlStatement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
            createTableStatement.setBroadCast(false);
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
            if (StringUtils.isNotBlank(tbCollation)) {
                String charset = CharsetConversion.getCharsetByCollation(tbCollation);
                if (StringUtils.isNotBlank(charset)) {
                    createTableStatement.addOption("CHARACTER SET", new SQLIdentifierExpr(charset));
                }
                createTableStatement.addOption("COLLATE", new SQLIdentifierExpr(tbCollation));
            }
            // 寻找自增主键, 去掉sequence type
            List<SQLTableElement> sqlTableElementList = createTableStatement.getTableElementList();
            Iterator<SQLTableElement> it = sqlTableElementList.iterator();
            while (it.hasNext()) {
                SQLTableElement el = it.next();
                if (el instanceof SQLColumnDefinition) {
                    SQLColumnDefinition definition = (SQLColumnDefinition) el;
                    definition.setSequenceType(null);
                    if (SystemDB.isDrdsImplicitId(definition.getName().getSimpleName())) {
                        it.remove();
                    }
                }
                if (el instanceof MySqlTableIndex) {
                    MySqlTableIndex index = (MySqlTableIndex) el;
                    if (index.getIndexDefinition().isGlobal()) {
                        it.remove();
                    }
                    if (hasImplicit(index.getIndexDefinition())) {
                        it.remove();
                    }
                }
                if (el instanceof MySqlPrimaryKey) {
                    MySqlPrimaryKey primaryKey = (MySqlPrimaryKey) el;
                    SQLIndexDefinition indexDefinition = primaryKey.getIndexDefinition();
                    if (hasImplicit(indexDefinition)) {
                        it.remove();
                        continue;
                    }
                    if (primaryKey.getName() != null && SystemDB.isDrdsImplicitId(primaryKey.getName()
                        .getSimpleName())) {
                        it.remove();
                        continue;
                    }
                }
            }
            hack4RepairTableName(tableName, createTableStatement, polarxDDL);
            return commentsBuilder.toString() + tryLowercase(createTableStatement.toUnformattedString(),
                lowerCaseTableNames);
        } else if (sqlStatement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement sqlAlterTableStatement = (SQLAlterTableStatement) sqlStatement;
            List<SQLAlterTableItem> items = sqlAlterTableStatement.getItems();
            Iterator<SQLAlterTableItem> iterator = items.iterator();

            while (iterator.hasNext()) {
                SQLAlterTableItem item = iterator.next();
                if (item instanceof SQLAlterTableAddIndex) {
                    SQLAlterTableAddIndex addIndex = (SQLAlterTableAddIndex) item;
                    addIndex.setClustered(false);
                    addIndex.setGlobal(false);
                    addIndex.setPartitioning(null);
                    addIndex.setDbPartitionBy(null);
                    addIndex.setTablePartitionBy(null);
                    addIndex.setTablePartitions(null);
                    addIndex.getIndexDefinition().setLocal(false);
                }
                if (item instanceof SQLAlterTableAddConstraint) {
                    SQLConstraint constraint = ((SQLAlterTableAddConstraint) item).getConstraint();
                    if (constraint instanceof MySqlUnique) {
                        MySqlUnique mySqlUnique = ((MySqlUnique) constraint);
                        mySqlUnique.setClustered(false);
                        mySqlUnique.setGlobal(false);
                        mySqlUnique.setLocal(false);
                        mySqlUnique.setPartitioning(null);
                        mySqlUnique.setDbPartitionBy(null);
                        mySqlUnique.setTablePartitionBy(null);
                        mySqlUnique.setTablePartitions(null);
                    }
                }
                if (item instanceof MySqlAlterTableModifyColumn) {
                    MySqlAlterTableModifyColumn modifyColumn = (MySqlAlterTableModifyColumn) item;
                    modifyColumn.getNewColumnDefinition().setSequenceType(null);
                }

                if (item instanceof SQLAlterTableAddColumn) {
                    SQLAlterTableAddColumn alterTableAlterColumn = (SQLAlterTableAddColumn) item;
                    alterTableAlterColumn.getColumns().forEach(c -> c.setSequenceType(null));
                }

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
        } else if (sqlStatement instanceof SQLCreateIndexStatement) {
            SQLCreateIndexStatement sqlCreateIndexStatement = (SQLCreateIndexStatement) sqlStatement;
            sqlCreateIndexStatement.setClustered(false);
            sqlCreateIndexStatement.setGlobal(false);
            sqlCreateIndexStatement.setLocal(false);
            sqlCreateIndexStatement.setDbPartitionBy(null);
            sqlCreateIndexStatement.setPartitioning(null);
            sqlCreateIndexStatement.setTablePartitionBy(null);
            sqlCreateIndexStatement.setTablePartitions(null);
        } else if (sqlStatement instanceof SQLDropTableStatement) {
            SQLDropTableStatement sqlDropTableStatement = (SQLDropTableStatement) sqlStatement;
            sqlDropTableStatement.setPurge(false);
        }

        return commentsBuilder.toString() + sqlStatement.toString();
    }

    private static boolean hasImplicit(SQLIndexDefinition indexDefinition) {
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
    private static void hack4RepairTableName(String tableName, SQLCreateTableStatement createTableStatement,
                                             String ddlSql) {
        if (StringUtils.isBlank(tableName)) {
            return;
        }

        String tableNameInSql = createTableStatement.getTableName();
        String tableNameInSqlNormal = SQLUtils.normalize(tableNameInSql);

        if (!StringUtils.equals(tableName, tableNameInSqlNormal)) {
            createTableStatement.setTableName("`" + escape(tableName) + "`");
            logger.warn("repair table name in create sql, before : {}, after :{}", tableNameInSql, tableName);
        }
    }

    private static Set<String> getAlgorithmBlacklist() {
        String configValue = DynamicApplicationConfig.getString(META_DDL_CONVERTER_ALGORITHM_BLACKLIST);
        if (StringUtils.isNotBlank(configValue)) {
            String[] splitValues = StringUtils.split(configValue.toLowerCase(), ",");
            return Sets.newHashSet(splitValues);
        }
        return new HashSet<>();
    }
}
