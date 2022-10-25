/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.com.polardbx.binlog.format;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import org.junit.Test;

public class FieldTest {

    @Test
    public void ddlTest() {
        String ddl =
            "CREATE TABLE `ttt` (\n" + "  `c1` varchar(10) CHARACTER SET latin1 COLLATE latin1_bin DEFAULT NULL,\n"
                + "  `c2` text CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,\n"
                + "  `c3` text CHARACTER SET gbk COLLATE gbk_chinese_ci\n"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
        SQLCreateTableStatement sqlCreateTableStatement = (SQLCreateTableStatement) SQLUtils.parseSingleMysqlStatement(
            ddl);
        SQLColumnDefinition definition = sqlCreateTableStatement.getColumn("c3");
        SQLCharExpr encode = definition.getEncode();
        System.out.println(encode.getText());
    }

    @Test
    public void testString() {
    }
}
