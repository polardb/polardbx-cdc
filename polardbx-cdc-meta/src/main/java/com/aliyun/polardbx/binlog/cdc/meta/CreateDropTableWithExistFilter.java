package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;

import static com.aliyun.polardbx.binlog.util.FastSQLConstant.FEATURES;

/**
 * created by ziyang.lb
 **/
public class CreateDropTableWithExistFilter {

    //@see Aone，ID:39665786
    public static boolean shouldIgnore(String sql, Long ddlRecordId, Long ddlJobId) {
        if (ddlRecordId != null && ddlJobId == null && isCreateTableWithIfNotExist(sql)) {
            return true;
        }
        if (ddlRecordId != null && ddlJobId == null && isDropTableWithIfExist(sql)) {
            return true;
        }
        return false;
    }

    private static boolean isCreateTableWithIfNotExist(String sql) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        if (stmt instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) stmt;
            return createTableStatement.isIfNotExists();
        }
        return false;
    }

    private static boolean isDropTableWithIfExist(String sql) {
        SQLStatementParser parser =
            SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, FEATURES);
        SQLStatement stmt = parser.parseStatementList().get(0);

        if (stmt instanceof SQLDropTableStatement) {
            SQLDropTableStatement dropTableStatement = (SQLDropTableStatement) stmt;
            return dropTableStatement.isIfExists();
        }
        return false;
    }
}
