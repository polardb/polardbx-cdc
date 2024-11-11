/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.TDDLHint;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableAddColumn;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableItem;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateProcedureStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableChangeColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlAlterTableModifyColumn;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserFeature;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import com.alibaba.polardbx.druid.sql.visitor.VisitorFeature;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class SQLUtils {
    public static final SQLParserFeature[] SQL_FEATURES = {
        SQLParserFeature.EnableSQLBinaryOpExprGroup,
        SQLParserFeature.UseInsertColumnsCache,
        SQLParserFeature.OptimizedForParameterized,
        SQLParserFeature.TDDLHint,
        SQLParserFeature.EnableCurrentUserExpr,
        SQLParserFeature.DRDSAsyncDDL,
        SQLParserFeature.DRDSBaseline,
        SQLParserFeature.DrdsMisc,
        SQLParserFeature.DrdsGSI,
        SQLParserFeature.DrdsCCL,
        SQLParserFeature.EnableFillKeyName
    };

    static {
        com.alibaba.polardbx.druid.sql.SQLUtils.DEFAULT_FORMAT_OPTION
            .config(VisitorFeature.OutputHashPartitionsByRange, true);
    }

    static {
        com.alibaba.polardbx.druid.sql.SQLUtils.DEFAULT_FORMAT_OPTION
            .config(VisitorFeature.OutputHashPartitionsByRange, true);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SQLStatement> T parseSQLStatement(String sql) {
        try {
            SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, SQL_FEATURES);
            List<SQLStatement> statementList = parser.parseStatementList();
            try {
                if (SpringContextHolder.isInitialize() && Boolean.parseBoolean(
                    SpringContextHolder.getPropertiesValue(ConfigKeys.IS_LAB_ENV))) {
                    checkDbType(statementList);
                }
            } catch (Throwable t) {
                // ignore exception
            }

            if (statementList.isEmpty()) {
                return null;
            } else {
                return (T) statementList.get(0);
            }
        } catch (Throwable t) {
            log.error("parse sql statement error! {}", sql, t);
            throw t;
        }
    }

    private static void checkDbType(List<SQLStatement> statementList) {
        for (SQLStatement st : statementList) {
            if (st.getDbType() != DbType.mysql) {
                log.error("check SQLStatement db type not mysql , target dbType is " + st.getDbType() + ", sql : "
                    + st);
                LabEventManager.logEvent(LabEventType.SQL_STATMENT_DB_TYPE_NOT_MYSQL, st.toString());
            }
        }
    }

    public static List<SQLStatement> parseSQLStatementList(String sql) {
        try {
            SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, SQL_FEATURES);
            return parser.parseStatementList();
        } catch (Throwable t) {
            log.error("parse sql statement list error! {}", sql, t);
            throw t;
        }
    }

    public static String toSQLStringWithTrueUcase(SQLStatement sqlStatement) {
        if (sqlStatement instanceof SQLCreateProcedureStatement) {
            return sqlStatement.toString();
        }
        if (sqlStatement.hasBeforeComment()) {
            // 对于before comment，只有当prettyFormat为true时，parser才支持打印
            return com.alibaba.polardbx.druid.sql.SQLUtils.toSQLString(sqlStatement, DbType.mysql);
        } else {
            com.alibaba.polardbx.druid.sql.SQLUtils.FormatOption formatOption =
                new com.alibaba.polardbx.druid.sql.SQLUtils.FormatOption(true, false);
            formatOption.config(VisitorFeature.OutputHashPartitionsByRange, true);
            return com.alibaba.polardbx.druid.sql.SQLUtils.toSQLString(sqlStatement, DbType.mysql, formatOption);
        }
    }

    public static String removeSomeHints(String sql) {
        SQLStatement stmt = SQLUtils.parseSQLStatement(sql);
        if (stmt == null) {
            return sql;
        }
        boolean removed = removeSomeHints(stmt);
        return removed ? stmt.toString() : sql;
    }

    public static boolean removeSomeHints(SQLStatement stmt) {
        if (stmt == null) {
            return false;
        }

        String searchSeed = "/*DDL_ID";
        String searchSeed2 = "/*DDL_SUBMIT_TOKEN";
        AtomicBoolean removed = new AtomicBoolean(false);

        if (stmt.hasBeforeComment()) {
            removed.set(removed.get() | stmt.getBeforeCommentsDirect().removeIf(c ->
                StringUtils.containsAny(c, searchSeed, searchSeed2)));
        }

        if (stmt.getHeadHintsDirect() != null) {
            stmt.getHeadHintsDirect().forEach(hint -> {
                if (hint instanceof TDDLHint) {
                    TDDLHint tddlHint = (TDDLHint) hint;
                    if (tddlHint.hasBeforeComment()) {
                        removed.set(removed.get() | tddlHint.getBeforeCommentsDirect()
                            .removeIf(c -> StringUtils.containsAny(c, searchSeed, searchSeed2)));
                    }
                }
            });
        }

        return removed.get();
    }

    public static String buildCreateLikeSql(String tableName, String baseSchemaName, String baseTableName) {
        return "create table `" + escape(tableName) + "` like `" +
            escape(baseSchemaName) + "`.`" + escape(baseTableName) + "`";
    }

    /**
     * 重写异常sql, 重写失败返回null
     * 1、 去掉 add key index {index_name} 重复key 和 index
     */
    public static String reWriteWrongDdl(String sql) {
        Scanner scanner = new Scanner(sql);
        StringBuilder sb = new StringBuilder();
        int keyCount = 0;
        boolean reWrite = false;
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            Scanner lineScanner = new Scanner(line);
            while (lineScanner.hasNext()) {
                String key = lineScanner.next();
                String lowerKey = key.toLowerCase();
                if (StringUtils.equalsAny(lowerKey, "key", "index")) {
                    keyCount++;
                    if (keyCount > 1) {
                        reWrite = true;
                        continue;
                    }
                } else {
                    keyCount = 0;
                }
                sb.append(key).append(" ");
            }
            sb.append("\n");
        }
        if (reWrite) {
            return sb.toString().trim();
        }
        return null;
    }

    public static boolean isLeaderByDdl(DataSource metaDbDataSource) throws SQLException {
        try (Connection conn = metaDbDataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TEMPORARY TABLE IF NOT EXISTS binlog_leader_test(id int)");
            stmt.execute("DROP TEMPORARY TABLE IF EXISTS binlog_leader_test");
            return true;
        }
    }

    public static boolean isLeaderBySqlQuery(DataSource metaDbDataSource) throws SQLException {
        try (Connection conn = metaDbDataSource.getConnection();
            Statement stmt = conn.createStatement();
            // 这个sql 如果当前节点不是leader，不会有任何结果返回；如果是leader，返回结果ROLE = Leader
            ResultSet resultSet = stmt.executeQuery("select * from information_schema.alisql_cluster_local")) {
            while (resultSet.next()) {
                String roleName = resultSet.getString("ROLE");
                if ("Leader".equalsIgnoreCase(roleName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean reWriteRealTypeBySqlMode(SQLStatement statement){
        boolean modify = false;
        List<SQLColumnDefinition> definitionList = new ArrayList<>();
        if (statement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) statement;
            List<SQLTableElement> elements = createTableStatement.getTableElementList();
            for (SQLTableElement el : elements) {
                if (el instanceof SQLColumnDefinition) {
                    definitionList.add((SQLColumnDefinition) el);
                }
            }
        } else if (statement instanceof SQLAlterTableStatement) {
            SQLAlterTableStatement alterTableModifyColumn = (SQLAlterTableStatement) statement;
            List<SQLAlterTableItem> itemList = alterTableModifyColumn.getItems();
            for (SQLAlterTableItem item : itemList) {
                if (item instanceof SQLAlterTableAddColumn) {
                    SQLAlterTableAddColumn addColumn = (SQLAlterTableAddColumn) item;
                    List<SQLColumnDefinition> addDefinitionList = addColumn.getColumns();
                    if (CollectionUtils.isNotEmpty(addDefinitionList)) {
                        definitionList.addAll(addDefinitionList);
                    }
                } else if (item instanceof MySqlAlterTableChangeColumn) {
                    MySqlAlterTableChangeColumn changeColumn = (MySqlAlterTableChangeColumn) item;
                    definitionList.add(changeColumn.getNewColumnDefinition());
                } else if (item instanceof MySqlAlterTableModifyColumn) {
                    MySqlAlterTableModifyColumn modifyColumn = (MySqlAlterTableModifyColumn) item;
                    definitionList.add(modifyColumn.getNewColumnDefinition());
                }
            }
        }
        for (SQLColumnDefinition definition : definitionList) {
            if (definition == null){
                continue;
            }
            SQLDataType dataType = definition.getDataType();
            if (dataType != null && StringUtils.equalsIgnoreCase(dataType.getName(), "real")) {
                dataType.setName("float");
                modify = true;
            }
        }
        return modify;
    }
}
