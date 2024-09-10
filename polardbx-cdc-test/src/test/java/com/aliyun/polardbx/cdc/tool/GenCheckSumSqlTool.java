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
package com.aliyun.polardbx.cdc.tool;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GenCheckSumSqlTool extends BaseTestCase {
    private static final String DATABASE_NAME = "da";
    private static final String TABLES = "t1\n" + "t2\n" + "t3\n";

    private static final String CONCAT_COLUMNS_TEMPLATE = "SELECT GROUP_CONCAT(CONCAT('', column_name, '') "
        + "ORDER BY ordinal_position SEPARATOR ',') AS columns FROM  information_schema.columns "
        + "WHERE table_schema = '" + DATABASE_NAME + "' AND table_name = '%s';";

    private static final String CALCULATE_CHECKSUM_SQL_FORMAT =
        "/*+TDDL:SOCKET_TIMEOUT=0*/SELECT "
            + "BIT_XOR( "
            + "CAST( "
            + "CRC32( "
            + "CONCAT_WS("
            + " %s) ) "
            + "AS UNSIGNED )) "
            + "AS checksum FROM %s.%s ";

    // 1. 修改TABLES的内容，然后执行方法 genTablesColumns()，并将返回结果保存到文件，如input.sql
    // 2. 登录CN节点，执行mysql -h127.1 -uxxx < input.sql > out.txt
    // 3. 执行：grep -v '^columns$' out.txt > result.txt
    // 4. 执行方法 genChecksumSql()，获取相应的checksum sql
    @Test
    public void genTablesColumns() {
        String[] tablesArray = StringUtils.split(TABLES, "\n");
        for (String str : tablesArray) {
            System.out.printf((CONCAT_COLUMNS_TEMPLATE) + "%n", str);
        }
    }

    @Test
    public void genChecksumSql() throws FileNotFoundException {
        List<String> tables = Lists.newArrayList(StringUtils.split(TABLES, "\n"));
        List<String> columnLines = buildColumnLines();
        Assert.assertEquals(tables.size(), columnLines.size());

        for (int i = 0; i < tables.size(); i++) {
            List<String> columns = Lists.newArrayList(StringUtils.split(columnLines.get(i), ","));
            String colConcatStr = buildConcatString(columns);
            String s = String.format(CALCULATE_CHECKSUM_SQL_FORMAT, colConcatStr, DATABASE_NAME, escape(tables.get(i)));
            System.out.println(s);
        }
    }

    private static String buildConcatString(List<String> columns) {
        StringBuilder concatWsSb = new StringBuilder();
        concatWsSb.append("',', ");
        for (String column : columns) {
            concatWsSb.append(String.format("`%s`, ", escape(column)));
        }

        StringBuilder concatSb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", escape(columns.get(i))));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", escape(columns.get(i))));
            }
        }

        concatWsSb.append(concatSb);
        return concatWsSb.toString();
    }

    private List<String> buildColumnLines() throws FileNotFoundException {
        List<String> columns = new ArrayList<>();
        File resultFile = new File("/Users/lubiao/Downloads/result.txt");
        Scanner scanner = new Scanner(resultFile);
        while (scanner.hasNextLine()) {
            columns.add(scanner.nextLine());
        }
        return columns;
    }
}

