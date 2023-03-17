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
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableRename;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.util.JdbcConstants;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author shicai.xsc 2021/4/5 23:22
 * @since 5.0.0.0
 */
@Slf4j
public class DdlHelper {

    private static Pattern tsoPattern = Pattern.compile("# POLARX_TSO=([\\d]+)\n");
    private static Pattern originSqlPattern = Pattern.compile("# POLARX_ORIGIN_SQL=([\\W\\w]+)\n#");

    public static boolean checkDdlDone(String sql, String schema, DbMetaCache dbMetaCache) {
        try {

            List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL, false);
            for (SQLStatement statement : stmtList) {
                if (statement instanceof SQLCreateTableStatement) {
                    SQLCreateTableStatement createTable = (SQLCreateTableStatement) statement;
                    schema = StringUtils.isBlank(createTable.getSchema()) ? schema : createTable.getSchema();
                    return dbMetaCache.getTables(schema).contains(createTable.getTableName());
                } else if (statement instanceof SQLDropTableStatement) {
                    SQLDropTableStatement dropTable = (SQLDropTableStatement) statement;
                    for (SQLExprTableSource tableSource : dropTable.getTableSources()) {
                        schema = StringUtils.isBlank(tableSource.getSchema()) ? schema : tableSource.getSchema();
                        if (dbMetaCache.getTables(schema).contains(tableSource.getTableName())) {
                            return false;
                        }
                    }
                    return true;
                } else if (statement instanceof SQLCreateIndexStatement) {
                    SQLCreateIndexStatement createIndex = (SQLCreateIndexStatement) statement;
                    TableInfo tableInfo = dbMetaCache.getTableInfo(createIndex.getSchema(), createIndex.getTableName());
                    SQLCreateTableStatement createTable = (SQLCreateTableStatement) SQLUtils
                        .parseSingleMysqlStatement(tableInfo.getCreateTable());
                    for (SQLSelectOrderByItem item : createIndex.getItems()) {

                    }
                } else if (statement instanceof SQLDropIndexStatement) {
                    SQLDropIndexStatement dropIndex = (SQLDropIndexStatement) statement;
                    dropIndex.getIndexName();
                } else if (statement instanceof SQLAlterTableStatement) {
                    SQLAlterTableStatement alterTable = (SQLAlterTableStatement) statement;
                    for (SQLAlterTableItem item : alterTable.getItems()) {
                        if (item instanceof SQLAlterTableRename) {
                        } else if (item instanceof SQLAlterTableAddIndex) {

                        } else if (item instanceof SQLAlterTableDropIndex || item instanceof SQLAlterTableDropKey) {

                        } else if (item instanceof SQLAlterTableAddConstraint) {

                        } else if (item instanceof SQLAlterTableDropConstraint) {

                        } else {

                        }
                    }
                }
            }
        } catch (Throwable e) {
        }

        return true;
    }

    public static String getTso(String sql, Timestamp timestamp, String pos) {
        Matcher matcher = tsoPattern.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf(sql.hashCode()) + timestamp.hashCode() + pos.hashCode();
    }

    public static String getOriginSql(String sql) {
        Matcher matcher = originSqlPattern.matcher(sql);
        if (!matcher.find()) {
            return "";
        }

        String originSql = matcher.group(1);
        List<SQLStatement> stmtList = SQLUtils.parseStatements(originSql, JdbcConstants.MYSQL, false);
        SQLStatement statement = stmtList.get(0);
        if (!(statement instanceof SQLCreateTableStatement)) {
            return originSql;
        }

        // remove _drds_implicit_id_
        SQLCreateTableStatement createTable = (SQLCreateTableStatement) statement;
        Iterator<SQLTableElement> iter = createTable.getTableElementList().iterator();
        while (iter.hasNext()) {
            SQLTableElement cur = iter.next();
            if (cur instanceof SQLColumnDefinition) {
                SQLColumnDefinition column = (SQLColumnDefinition) cur;
                if (StringUtils.equalsIgnoreCase(column.getColumnName(), RplConstants.POLARX_IMPLICIT_ID)
                    || StringUtils.equalsIgnoreCase(column.getColumnName(), RplConstants.RDS_IMPLICIT_ID)) {
                    iter.remove();
                }
            }

            // remove rds/_drds_implicit_id_ primary key
            if (cur instanceof MySqlPrimaryKey) {
                MySqlPrimaryKey pk = (MySqlPrimaryKey) cur;
                String columnName = pk.getColumns().get(0).getExpr().toString();
                if (StringUtils.equalsIgnoreCase(columnName, RplConstants.POLARX_IMPLICIT_ID)
                    || StringUtils.equalsIgnoreCase(columnName, RplConstants.RDS_IMPLICIT_ID)) {
                    iter.remove();
                }
            }
        }

        return createTable.toString();
    }

    public static boolean getDdlLock(String ddlTso, String sql, RplDdl outputDdl) {
        // get ddl lock
        RplDdl existDdl = DbTaskMetaManager.getDdl(ddlTso);
        if (existDdl == null) {
            try {
                // record ddl in DB
                RplDdl ddl = new RplDdl();
                ddl.setDdlTso(ddlTso);
                ddl.setStateMachineId(TaskContext.getInstance().getStateMachineId());
                ddl.setServiceId(TaskContext.getInstance().getServiceId());
                ddl.setTaskId(TaskContext.getInstance().getTaskId());
                ddl.setJobId(0L);
                ddl.setState(DdlState.NOT_START.getValue());
                ddl.setDdlStmt(sql);
                existDdl = DbTaskMetaManager.addDdl(ddl);
                outputDdl.setId(existDdl.getId());
                outputDdl.setState(existDdl.getState());
                return true;
            } catch (Throwable e) {
                log.error("failed to get ddl lock", e);
                return false;
            }
        } else {
            outputDdl.setId(existDdl.getId());
            outputDdl.setState(existDdl.getState());
            return TaskContext.getInstance().getTaskId() == existDdl.getTaskId();
        }
    }
}
