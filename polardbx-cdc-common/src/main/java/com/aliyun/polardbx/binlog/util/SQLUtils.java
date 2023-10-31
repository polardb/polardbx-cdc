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
package com.aliyun.polardbx.binlog.util;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.parser.SQLParserFeature;
import com.alibaba.polardbx.druid.sql.parser.SQLParserUtils;
import com.alibaba.polardbx.druid.sql.parser.SQLStatementParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Scanner;

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
        SQLParserFeature.DrdsCCL
    };

    public static SQLStatement parseSQLStatement(String sql) {
        try {
            SQLStatementParser parser = SQLParserUtils.createSQLStatementParser(sql, DbType.mysql, SQL_FEATURES);
            List<SQLStatement> statementList = parser.parseStatementList();
            if (statementList.isEmpty()) {
                return null;
            } else {
                return statementList.get(0);
            }
        } catch (Throwable t) {
            log.error("parse sql statement error! {}", sql, t);
            throw t;
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
}
