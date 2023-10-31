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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.RegexUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX;

public class RegexUtilTest extends BaseTest {

    private static final String REGEX_PATTERNS = "pattern";
    private static final String REGEX_STRING = "regex_string";
    private static final String IF_EXPRESS = "if_express";

    static void printSplitLine() {
        System.out.println("---------------------------------------");
    }

    @Test
    public void testAlterUser() {
        String config = DynamicApplicationConfig.getString(META_BUILD_PHYSICAL_DDL_SQL_BLACKLIST_REGEX);
        String sql1 = "alter user ";
        String sql2 = "alter \n user";
        String sql3 = "alter       user ";
        String sql4 = "Alter UsEr";
        Assert.assertTrue(RegexUtil.match(config, sql1));
        Assert.assertTrue(RegexUtil.match(config, sql2));
        Assert.assertTrue(RegexUtil.match(config, sql3));
        Assert.assertTrue(RegexUtil.match(config, sql4));
    }

    @Test
    public void testRegex() {
        String regexString = "^grant\\s+\\w+\\s+on\\s+.*,^savepoint.*,.*__drds_global_tx_log.*,^create sequence.*";

        String grant = "Grant xa_recover_admin on *.* to 'rds_polardb_x'@'%'";
        String savePoint = "Savepoint xxx";
        String empty = "";
        String createTable = "create table";
        String drds = "test__Drds_global_tx_log__test";
        String drds2 = "alter table `__drds_global_tx_log`\n"
            + "drop partition `p_1461439929572655104`";
        String drds3 = "alter table `__drds_global_tx_log`\r\n"
            + "drop partition `p_1461439929572655104`";

        String drds4 = "create sequence pxc_seq_64056c9e413d6f79544c4938f86c8d6e start with 1 cache 100000";

        Assert.assertTrue(RegexUtil.match(regexString, grant));
        Assert.assertTrue(RegexUtil.match(regexString, savePoint));
        Assert.assertTrue(RegexUtil.match(regexString, drds));
        Assert.assertFalse(RegexUtil.match(regexString, empty));
        Assert.assertFalse(RegexUtil.match(regexString, createTable));
        Assert.assertTrue(RegexUtil.match(regexString, drds2));
        Assert.assertTrue(RegexUtil.match(regexString, drds3));
        Assert.assertTrue(RegexUtil.match(regexString, drds4));
    }

    @Test
    public void testPerformanceAllMatch() {
        String regexString = "^grant\\s+\\w+\\s+on\\s+.*,^savepoint.*,.*__drds_global_tx_log.*";
//        String regexString = "^grant\\s+\\w+\\s+on\\s+.*,^savepoint.*";

        String[] targets = new String[] {
            "Savepoint xxx", "test__Drds_global_tx_log__test", "Grant xa_recover_admin on *.* to 'rds_polardb_x'@'%'"};

        execute(10, 10000, targets, regexString, REGEX_STRING);
        printSplitLine();
        execute(10, 10000, targets, regexString, REGEX_PATTERNS);
        printSplitLine();
        execute(10, 10000, targets, regexString, IF_EXPRESS);
    }

    @Test
    public void testPerformanceBigDdl() {

        String regexString = "^grant\\s+\\w+\\s+on\\s+.*,^savepoint.*,.*__drds_global_tx_log.*";
//        String regexString = "^grant\\s+\\w+\\s+on\\s+.*,^savepoint.*";
        String[] targets = new String[] {
            "CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name\n"
                + "    (create_definition,...)\n"
                + "    [table_options]\n"
                + "    [partition_options]\n"
                + "\n"
                + "CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name\n"
                + "    [(create_definition,...)]\n"
                + "    [table_options]\n"
                + "    [partition_options]\n"
                + "    [IGNORE | REPLACE]\n"
                + "    [AS] query_expression\n"
                + "\n"
                + "CREATE [TEMPORARY] TABLE [IF NOT EXISTS] tbl_name\n"
                + "    { LIKE old_tbl_name | (LIKE old_tbl_name) }\n"
                + "\n"
                + "create_definition: {\n"
                + "    col_name column_definition\n"
                + "  | {INDEX | KEY} [index_name] [index_type] (key_part,...)\n"
                + "      [index_option] ...\n"
                + "  | {FULLTEXT | SPATIAL} [INDEX | KEY] [index_name] (key_part,...)\n"
                + "      [index_option] ...\n"
                + "  | [CONSTRAINT [symbol]] PRIMARY KEY\n"
                + "      [index_type] (key_part,...)\n"
                + "      [index_option] ...\n"
                + "  | [CONSTRAINT [symbol]] UNIQUE [INDEX | KEY]\n"
                + "      [index_name] [index_type] (key_part,...)\n"
                + "      [index_option] ...\n"
                + "  | [CONSTRAINT [symbol]] FOREIGN KEY\n"
                + "      [index_name] (col_name,...)\n"
                + "      reference_definition\n"
                + "  | check_constraint_definition\n"
                + "}\n"
                + "\n"
                + "column_definition: {\n"
                + "    data_type [NOT NULL | NULL] [DEFAULT {literal | (expr)} ]\n"
                + "      [VISIBLE | INVISIBLE]\n"
                + "      [AUTO_INCREMENT] [UNIQUE [KEY]] [[PRIMARY] KEY]\n"
                + "      [COMMENT 'string']\n"
                + "      [COLLATE collation_name]\n"
                + "      [COLUMN_FORMAT {FIXED | DYNAMIC | DEFAULT}]\n"
                + "      [ENGINE_ATTRIBUTE [=] 'string']\n"
                + "      [SECONDARY_ENGINE_ATTRIBUTE [=] 'string']\n"
                + "      [STORAGE {DISK | MEMORY}]\n"
                + "      [reference_definition]\n"
                + "      [check_constraint_definition]\n"
                + "  | data_type\n"
                + "      [COLLATE collation_name]\n"
                + "      [GENERATED ALWAYS] AS (expr)\n"
                + "      [VIRTUAL | STORED] [NOT NULL | NULL]\n"
                + "      [VISIBLE | INVISIBLE]\n"
                + "      [UNIQUE [KEY]] [[PRIMARY] KEY]\n"
                + "      [COMMENT 'string']\n"
                + "      [reference_definition]\n"
                + "      [check_constraint_definition]\n"
                + "}\n"
                + "\n"
                + "data_type:\n"
                + "    (see Chapter 11, Data Types)\n"
                + "\n"
                + "key_part: {col_name [(length)] | (expr)} [ASC | DESC]\n"
                + "\n"
                + "index_type:\n"
                + "    USING {BTREE | HASH}\n"
                + "\n"
                + "index_option: {\n"
                + "    KEY_BLOCK_SIZE [=] value\n"
                + "  | index_type\n"
                + "  | WITH PARSER parser_name\n"
                + "  | COMMENT 'string'\n"
                + "  | {VISIBLE | INVISIBLE}\n"
                + "  |ENGINE_ATTRIBUTE [=] 'string'\n"
                + "  |SECONDARY_ENGINE_ATTRIBUTE [=] 'string'\n"
                + "}\n"
                + "\n"
                + "check_constraint_definition:\n"
                + "    [CONSTRAINT [symbol]] CHECK (expr) [[NOT] ENFORCED]\n"
                + "\n"
                + "reference_definition:\n"
                + "    REFERENCES tbl_name (key_part,...)\n"
                + "      [MATCH FULL | MATCH PARTIAL | MATCH SIMPLE]\n"
                + "      [ON DELETE reference_option]\n"
                + "      [ON UPDATE reference_option]\n"
                + "\n"
                + "reference_option:\n"
                + "    RESTRICT | CASCADE | SET NULL | NO ACTION | SET DEFAULT\n"
                + "\n"
                + "table_options:\n"
                + "    table_option [[,] table_option] ...\n"
                + "\n"
                + "table_option: {\n"
                + "    AUTOEXTEND_SIZE [=] value\n"
                + "  | AUTO_INCREMENT [=] value\n"
                + "  | AVG_ROW_LENGTH [=] value\n"
                + "  | [DEFAULT] CHARACTER SET [=] charset_name\n"
                + "  | CHECKSUM [=] {0 | 1}\n"
                + "  | [DEFAULT] COLLATE [=] collation_name\n"
                + "  | COMMENT [=] 'string'\n"
                + "  | COMPRESSION [=] {'ZLIB' | 'LZ4' | 'NONE'}\n"
                + "  | CONNECTION [=] 'connect_string'\n"
                + "  | {DATA | INDEX} DIRECTORY [=] 'absolute path to directory'\n"
                + "  | DELAY_KEY_WRITE [=] {0 | 1}\n"
                + "  | ENCRYPTION [=] {'Y' | 'N'}\n"
                + "  | ENGINE [=] engine_name\n"
                + "  | ENGINE_ATTRIBUTE [=] 'string'\n"
                + "  | INSERT_METHOD [=] { NO | FIRST | LAST }\n"
                + "  | KEY_BLOCK_SIZE [=] value\n"
                + "  | MAX_ROWS [=] value\n"
                + "  | MIN_ROWS [=] value\n"
                + "  | PACK_KEYS [=] {0 | 1 | DEFAULT}\n"
                + "  | PASSWORD [=] 'string'\n"
                + "  | ROW_FORMAT [=] {DEFAULT | DYNAMIC | FIXED | COMPRESSED | REDUNDANT | COMPACT}\n"
                + "  | SECONDARY_ENGINE_ATTRIBUTE [=] 'string'\n"
                + "  | STATS_AUTO_RECALC [=] {DEFAULT | 0 | 1}\n"
                + "  | STATS_PERSISTENT [=] {DEFAULT | 0 | 1}\n"
                + "  | STATS_SAMPLE_PAGES [=] value\n"
                + "  | TABLESPACE tablespace_name [STORAGE {DISK | MEMORY}]\n"
                + "  | UNION [=] (tbl_name[,tbl_name]...)\n"
                + "}", "partition_options:\n"
            + "    PARTITION BY\n"
            + "        { [LINEAR] HASH(expr)\n"
            + "        | [LINEAR] KEY [ALGORITHM={1 | 2}] (column_list)\n"
            + "        | RANGE{(expr) | COLUMNS(column_list)}\n"
            + "        | LIST{(expr) | COLUMNS(column_list)} }\n"
            + "    [PARTITIONS num]\n"
            + "    [SUBPARTITION BY\n"
            + "        { [LINEAR] HASH(expr)\n"
            + "        | [LINEAR] KEY [ALGORITHM={1 | 2}] (column_list) }\n"
            + "      [SUBPARTITIONS num]\n"
            + "    ]\n"
            + "    [(partition_definition [, partition_definition] ...)]\n"
            + "\n"
            + "partition_definition:\n"
            + "    PARTITION partition_name\n"
            + "        [VALUES\n"
            + "            {LESS THAN {(expr | value_list) | MAXVALUE}\n"
            + "            |\n"
            + "            IN (value_list)}]\n"
            + "        [[STORAGE] ENGINE [=] engine_name]\n"
            + "        [COMMENT [=] 'string' ]\n"
            + "        [DATA DIRECTORY [=] 'data_dir']\n"
            + "        [INDEX DIRECTORY [=] 'index_dir']\n"
            + "        [MAX_ROWS [=] max_number_of_rows]\n"
            + "        [MIN_ROWS [=] min_number_of_rows]\n"
            + "        [TABLESPACE [=] tablespace_name]\n"
            + "        [(subpartition_definition [, subpartition_definition] ...)]\n"
            + "\n"
            + "subpartition_definition:\n"
            + "    SUBPARTITION logical_name\n"
            + "        [[STORAGE] ENGINE [=] engine_name]\n"
            + "        [COMMENT [=] 'string' ]\n"
            + "        [DATA DIRECTORY [=] 'data_dir']\n"
            + "        [INDEX DIRECTORY [=] 'index_dir']\n"
            + "        [MAX_ROWS [=] max_number_of_rows]\n"
            + "        __drds_global_tx_log\n"
            + "        [TABLESPACE [=] tablespace_name]\n"
            + "\n"
            + "query_expression:\n"
            + "    SELECT ...   (Some valid select or union statement)",
            "Grant xa_recover_admin on *.* to 'rds_polardb_x'@'%'"};

        Assert.assertFalse(RegexUtil.match(regexString, targets[0]));
        Assert.assertTrue(RegexUtil.match(regexString, targets[1]));

        execute(10, 10000, targets, regexString, REGEX_STRING);
        printSplitLine();
        execute(10, 10000, targets, regexString, REGEX_PATTERNS);
        printSplitLine();
        execute(10, 10000, targets, regexString, IF_EXPRESS);
    }

    private void execute(int threadCount, int dataCount, String[] targets, String regexString, String mode) {
        List<String> mockData = new ArrayList<>();

        for (int i = 0; i < dataCount; i++) {
            mockData.addAll(Arrays.asList(targets));
        }

        ConsumeThread[] threads = new ConsumeThread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new ConsumeThread(mockData, regexString, mode);
        }

        for (Thread thread : threads) {
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class ConsumeThread extends Thread {
        private final List<String> mockData;
        private final String regexString;
        private final String mode;
        List<Pattern> regexPatterns;

        public ConsumeThread(List<String> mockData, String regexString, String mode) {
            this.mockData = mockData;
            this.mode = mode;
            this.regexString = regexString;
            this.regexPatterns = RegexUtil.convert2Patterns(regexString);

        }

        @Override
        public void run() {
            Long start = System.currentTimeMillis();
            if (REGEX_PATTERNS.equals(mode)) {
                for (String target : mockData) {
                    RegexUtil.match(regexPatterns, target);
                }
            } else if (REGEX_STRING.equals(mode)) {
                for (String target : mockData) {
                    RegexUtil.match(regexString, target);
                }
            } else if (IF_EXPRESS.equals(mode)) {
                for (String target : mockData) {
                    if (target.toLowerCase().startsWith("savepoint")) {
                        continue;
                    }
                    if (target.toLowerCase().startsWith("grant")) {
                        continue;
                    }
                    target.toLowerCase().contains("__drds_global_tx_log");
                }
            }

            Long end = System.currentTimeMillis();
            double avg = (end - start) / (double) mockData.size();
            System.out.printf("mode:%s total: %d ms, avg: %f ms%n", mode, (end - start), avg);

        }
    }
}
