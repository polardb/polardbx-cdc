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

package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexDefinition;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.alibaba.polardbx.druid.sql.parser.SQLParserFeature;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.aliyun.polardbx.binlog.canal.LowerCaseTableNameVariables;
import com.aliyun.polardbx.binlog.canal.system.SystemDB;
import com.aliyun.polardbx.binlog.format.utils.CharsetConversion;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

public class DDLConverter {

    private final static SQLParserFeature[] defaultFeatures = {
        SQLParserFeature.EnableSQLBinaryOpExprGroup,
        SQLParserFeature.UseInsertColumnsCache, SQLParserFeature.OptimizedForParameterized,
        SQLParserFeature.TDDLHint, SQLParserFeature.EnableCurrentUserExpr, SQLParserFeature.DRDSAsyncDDL,
        SQLParserFeature.DRDSBaseline, SQLParserFeature.DrdsMisc, SQLParserFeature.DrdsGSI, SQLParserFeature.DrdsCCL
    };
    private static final Logger logger = LoggerFactory.getLogger(DDLConverter.class);

    private static String transferTable(String tableName, int lowerCaseTableNames) {
        return lowerCaseTableNames
            == LowerCaseTableNameVariables.LOWERCASE.getValue() ? tableName.toLowerCase() : tableName;
    }

    public static String formatPolarxDDL(String polarxDDL, String dbCharset, String tbCollation,
                                         int lowerCaseTableNames) {

        String ddl = polarxDDL;
        try {
            SQLStatementParser parser =
                SQLParserUtils.createSQLStatementParser(polarxDDL, DbType.mysql, defaultFeatures);
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
                ddl = createTableStatement.toString();
            } else if (statement instanceof SQLCreateDatabaseStatement) {
                SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) statement;
                createDatabaseStatement.setCharacterSet(dbCharset);
                ddl = createDatabaseStatement.toString();
            }
            ddl = transferTable(ddl, lowerCaseTableNames);
        } catch (Exception e) {
            logger.error("parse ddl failed! ", e);
        }
        return ddl;
    }

    /**
     * 目前polarx 特有的DDL 除建表之外，其他 拆分变更表和gsi 都会被过滤掉，
     * 其余 alter table 都是正常ddl，可以同步给mysql，这里只考虑create table语句。
     */
    public static String convertNormalDDL(String polarxDDL, String dbCharset, String tbCollation, String tso) {

        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(polarxDDL, DbType.mysql, defaultFeatures);
        List<SQLStatement> statementList = parser.parseStatementList();
        SQLStatement sqlStatement = statementList.get(0);

        StringBuilder hintsBuilder = new StringBuilder();
        hintsBuilder.append("/*POLARX_ORIGIN_SQL=").append(polarxDDL).append("*//*").append("TSO=").append(tso)
            .append("*/");

        if (sqlStatement instanceof SQLCreateDatabaseStatement) {
            SQLCreateDatabaseStatement createDatabaseStatement = (SQLCreateDatabaseStatement) sqlStatement;
            createDatabaseStatement.setPartitionMode(null);
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

            return hintsBuilder.toString() + createTableStatement.toUnformattedString();
        }

        return hintsBuilder.toString() + sqlStatement.toString();
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
}
