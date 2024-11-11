/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.parser;

import com.alibaba.polardbx.druid.sql.ast.SQLExpr;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropIndex;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableDropKey;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableRename;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLConstraint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLUnique;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateRoleStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateUserStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement.Item;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;

import java.util.List;

import static com.aliyun.polardbx.binlog.canal.LogEventUtil.SYNC_POINT_PROCEDURE_NAME;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatementList;

/**
 * @author agapple 2017年7月27日 下午4:05:34
 * @since 3.2.5
 */
public class DruidDdlParser {

    public static DdlResult parse(String queryString, String schemaName) {
        DdlResult ddlResult = null;
        List<SQLStatement> stmtList = parseSQLStatementList(queryString);

        for (SQLStatement statement : stmtList) {
            if (statement instanceof SQLCreateTableStatement) {
                ddlResult = new DdlResult();
                SQLCreateTableStatement createTable = (SQLCreateTableStatement) statement;
                processName(ddlResult, schemaName, createTable.getName());
                ddlResult.setType(DBMSAction.CREATE);
                ddlResult.setHasIfExistsOrNotExists(createTable.isIfNotExists());
            } else if (statement instanceof SQLAlterTableStatement) {
                SQLAlterTableStatement alterTable = (SQLAlterTableStatement) statement;
                if (!alterTable.getTableOptions().isEmpty()) {
                    ddlResult = new DdlResult();
                    processName(ddlResult, schemaName, alterTable.getName());
                    ddlResult.setType(DBMSAction.ALTER);
                }

                for (SQLAlterTableItem item : alterTable.getItems()) {
                    if (item instanceof SQLAlterTableRename) {
                        ddlResult = new DdlResult();
                        processName(ddlResult, schemaName, alterTable.getName());
                        processName(ddlResult, schemaName, ((SQLAlterTableRename) item).getToName());
                        // alter table `seller_table2` rename `seller_table3` , add key
                        // `idx_create`(`gmt_create`)
                        ddlResult.setType(DBMSAction.RENAME);
                    } else if (item instanceof SQLAlterTableAddIndex) {
                        ddlResult = new DdlResult();
                        processName(ddlResult, schemaName, alterTable.getName());
                        ddlResult.setType(DBMSAction.CINDEX);
                    } else if (item instanceof SQLAlterTableDropIndex || item instanceof SQLAlterTableDropKey) {
                        ddlResult = new DdlResult();
                        processName(ddlResult, schemaName, alterTable.getName());
                        ddlResult.setType(DBMSAction.DINDEX);
                    } else if (item instanceof SQLAlterTableAddConstraint) {
                        ddlResult = new DdlResult();
                        processName(ddlResult, schemaName, alterTable.getName());
                        SQLConstraint constraint = ((SQLAlterTableAddConstraint) item).getConstraint();
                        if (constraint instanceof SQLUnique) {
                            ddlResult.setType(DBMSAction.CINDEX);
                        }
                    } else if (item instanceof SQLAlterTableDropConstraint) {
                        ddlResult = new DdlResult();
                        processName(ddlResult, schemaName, alterTable.getName());
                        ddlResult.setType(DBMSAction.DINDEX);
                    }
                }

                if (ddlResult == null) {
                    ddlResult = new DdlResult();
                    processName(ddlResult, schemaName, alterTable.getName());
                }
                if (ddlResult.getType() == null) {
                    ddlResult.setType(DBMSAction.ALTER);
                }
            } else if (statement instanceof SQLDropTableStatement) {
                SQLDropTableStatement dropTable = (SQLDropTableStatement) statement;
                for (SQLExprTableSource tableSource : dropTable.getTableSources()) {
                    ddlResult = new DdlResult();
                    processName(ddlResult, schemaName, tableSource.getExpr());
                    ddlResult.setType(DBMSAction.ERASE);
                    ddlResult.setHasIfExistsOrNotExists(dropTable.isIfExists());
                }
            } else if (statement instanceof SQLCreateIndexStatement) {
                SQLCreateIndexStatement createIndex = (SQLCreateIndexStatement) statement;
                SQLTableSource tableSource = createIndex.getTable();
                ddlResult = new DdlResult();
                processName(ddlResult, schemaName, ((SQLExprTableSource) tableSource).getExpr());
                ddlResult.setType(DBMSAction.CINDEX);
            } else if (statement instanceof SQLDropIndexStatement) {
                SQLDropIndexStatement dropIndex = (SQLDropIndexStatement) statement;
                SQLExprTableSource tableSource = dropIndex.getTableName();
                ddlResult = new DdlResult();
                processName(ddlResult, schemaName, tableSource.getExpr());
                ddlResult.setType(DBMSAction.DINDEX);
            } else if (statement instanceof SQLTruncateStatement) {
                SQLTruncateStatement truncate = (SQLTruncateStatement) statement;
                for (SQLExprTableSource tableSource : truncate.getTableSources()) {
                    ddlResult = new DdlResult();
                    processName(ddlResult, schemaName, tableSource.getExpr());
                    ddlResult.setType(DBMSAction.TRUNCATE);
                }
            } else if (statement instanceof MySqlRenameTableStatement) {
                MySqlRenameTableStatement rename = (MySqlRenameTableStatement) statement;
                for (Item item : rename.getItems()) {
                    ddlResult = new DdlResult();
                    processName(ddlResult, schemaName, item.getName());
                    processName(ddlResult, schemaName, item.getTo());
                    ddlResult.setType(DBMSAction.RENAME);
                }
            } else if (statement instanceof SQLDropDatabaseStatement) {
                ddlResult = new DdlResult();
                SQLDropDatabaseStatement dropDataBase = (SQLDropDatabaseStatement) statement;
                processName(ddlResult, schemaName, dropDataBase.getDatabase());
                ddlResult.setType(DBMSAction.DROPDB);
                ddlResult.setHasIfExistsOrNotExists(dropDataBase.isIfExists());
            } else if (statement instanceof SQLCreateDatabaseStatement) {
                ddlResult = new DdlResult();
                SQLCreateDatabaseStatement createDataBase = (SQLCreateDatabaseStatement) statement;
                processName(ddlResult, schemaName, createDataBase.getName());
                ddlResult.setType(DBMSAction.CREATEDB);
                ddlResult.setHasIfExistsOrNotExists(createDataBase.isIfNotExists());
            } else if (statement instanceof MySqlCreateUserStatement) {
                ddlResult = new DdlResult();
                MySqlCreateUserStatement createUser = (MySqlCreateUserStatement) statement;
                ddlResult.setType(DBMSAction.OTHER);
                ddlResult.setHasIfExistsOrNotExists(createUser.isIfNotExists());
            } else if (statement instanceof MySqlCreateRoleStatement) {
                ddlResult = new DdlResult();
                MySqlCreateRoleStatement createRole = (MySqlCreateRoleStatement) statement;
                ddlResult.setType(DBMSAction.OTHER);
                ddlResult.setHasIfExistsOrNotExists(createRole.isIfNotExists());
            } else if (statement instanceof SQLCallStatement) {
                SQLCallStatement stmt = (SQLCallStatement) statement;
                if (SYNC_POINT_PROCEDURE_NAME.equals(stmt.getProcedureName().getSimpleName())) {
                    ddlResult = new DdlResult();
                    ddlResult.setType(DBMSAction.OTHER);
                }
            }

            if (ddlResult != null) {
                ddlResult.setSqlStatement(statement);
            }
        }

        return ddlResult;
    }

    private static void processName(DdlResult ddlResult, String schema, SQLExpr sqlName) {
        if (sqlName == null) {
            return;
        }

        String table = null;
        if (sqlName instanceof SQLPropertyExpr) {
            SQLExpr owner = ((SQLPropertyExpr) sqlName).getOwner();
            if (owner instanceof SQLPropertyExpr) {
                // see https://aone.alibaba-inc.com/v2/project/860366/bug/55137024
                owner = ((SQLPropertyExpr) owner).getOwner();
            }

            schema = com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim(((SQLIdentifierExpr) owner).getName());
            table = com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim(((SQLPropertyExpr) sqlName).getName());
        } else if (sqlName instanceof SQLIdentifierExpr) {
            table = com.alibaba.polardbx.druid.sql.SQLUtils.normalizeNoTrim(((SQLIdentifierExpr) sqlName).getName());
        }

        ddlResult.setSchemaName(schema);
        ddlResult.setTableName(table);
    }
}
