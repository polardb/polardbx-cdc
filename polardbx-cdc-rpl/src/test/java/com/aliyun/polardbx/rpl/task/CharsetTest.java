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
package com.aliyun.polardbx.rpl.task;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.aliyun.polardbx.rpl.TestBase;

/**
 * @author shicai.xsc 2021/4/21 16:15
 * @since 5.0.0.0
 */
public class CharsetTest extends TestBase {

    private final String NONE_CHARSET = "none";
    private final String LATIN1 = "latin1";
    private final String GBK = "gbk";
    private final String UTF8 = "utf8";
    private final String UTF8MB4 = "utf8mb4";

    private final String ID = "id";
    private final String VALUE = "value";

    private List<String> allCharsets = Arrays.asList(NONE_CHARSET, LATIN1, GBK, UTF8, UTF8MB4);
    private List<String> chineseCharsets = Arrays.asList(GBK, UTF8, UTF8MB4);
    private List<String> emojiCharsets = Arrays.asList(UTF8MB4);

    private final List<Serializable> english = Arrays.asList(1, "english");
    private final List<Serializable> chinese = Arrays.asList(2, "中文");
    private final List<Serializable> emoji = Arrays.asList(3, "中文😀");

    // 为了加快速度，第一次建库建表之后，就不用再创建
    private boolean doDddl = true;

    @Before
    public void before() throws Exception {
        channel = "charsetTest";
        super.before();
    }

    @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void charset_Test() throws Exception {
        runnerThread.start();
        wait(WAIT_TASK_SECOND);
        List<String> dbs = createDbs();

        for (String db : dbs) {
            List<String> tables = createTables(db);

            // wait all DDL finish
            List<String> dstTables = new ArrayList<>();
            while (dstTables.size() < tables.size()) {
                dstTables = execQuery(dstConn, "show tables in " + db, 1);
                wait(1);
            }

            for (String table : tables) {
                executeDmlAndCheck(db, table);
            }
        }
    }

    /**
     * 创建数据库 charsetTest_latin1，charsetTest_gbk，charsetTest_utf8，charsetTest_utf8mb4
     */
    private List<String> createDbs() throws Exception {
        List<String> dbs = new ArrayList<>();
        for (int i = 1; i < allCharsets.size(); i++) {
            String db = "charsetTest_" + allCharsets.get(i);
            if (doDddl) {
                execUpdate(srcConn, "drop database if exists " + db, null);

                wait(WAIT_DDL_SECOND);
                List<String> dstDbs = execQuery(dstConn, "show databases", 1);
                Assert.assertFalse(dstDbs.stream().anyMatch(db::equalsIgnoreCase));

                execUpdate(srcConn,
                    String.format("create database if not exists %s DEFAULT CHARSET=%s", db, allCharsets.get(i)),
                    null);
                wait(WAIT_DDL_SECOND);
                dstDbs = execQuery(dstConn, "show databases", 1);
                Assert.assertTrue(dstDbs.stream().anyMatch(db::equalsIgnoreCase));
            }
            dbs.add(db);
        }

        return dbs;
    }

    /**
     * @param db 创建以下表 | tb_gbk_col_gbk | | tb_gbk_col_latin1 | | tb_gbk_col_none |
     * | tb_gbk_col_utf8 | | tb_gbk_col_utf8mb4 | | tb_latin1_col_gbk | |
     * tb_latin1_col_latin1 | | tb_latin1_col_none | | tb_latin1_col_utf8 | |
     * tb_latin1_col_utf8mb4 | | tb_none_col_gbk | | tb_none_col_latin1 | |
     * tb_none_col_none | | tb_none_col_utf8 | | tb_none_col_utf8mb4 | |
     * tb_utf8_col_gbk | | tb_utf8_col_latin1 | | tb_utf8_col_none | |
     * tb_utf8_col_utf8 | | tb_utf8_col_utf8mb4 | | tb_utf8mb4_col_gbk | |
     * tb_utf8mb4_col_latin1 | | tb_utf8mb4_col_none | | tb_utf8mb4_col_utf8 | |
     * tb_utf8mb4_col_utf8mb4
     */
    private List<String> createTables(String db) {
        Assert.assertTrue(execUpdate(srcConn, "use " + db, null));

        List<String> tables = new ArrayList<>();
        for (String tb : allCharsets) {
            for (String col : allCharsets) {
                String tbName = String.format("tb_%s_col_%s", tb, col);
                String colCharset = NONE_CHARSET.equals(col) ? "" : "charset " + col;
                String tbCharset = NONE_CHARSET.equals(tb) ? "" : "default charset=" + tb;
                String createSql = String.format(
                    "CREATE TABLE IF NOT EXISTS `%s`(`id` int(11), `value` mediumtext %s, PRIMARY KEY (`id`)) ENGINE=InnoDB %s",
                    tbName,
                    colCharset,
                    tbCharset);
                if (doDddl) {
                    Assert.assertTrue(execUpdate(srcConn, createSql, null));
                }
                tables.add(tbName);
            }
        }

        return tables;
    }

    private void executeDmlAndCheck(String db, String table) throws Exception {
        Assert.assertTrue(execUpdate(srcConn, "use " + db, null));
        Assert.assertTrue(execUpdate(srcConn, String.format("delete from %s where id >= 0", table), null));

        String dbCharset = db.split("_")[1];
        String tbCharset = table.split("_")[1];
        String colCharset = table.split("_")[3];

        String sql = String.format("insert into %s values(?, ?);", table);
        System.out.println("sql: " + sql);

        int recordCount = 1;
        Assert.assertTrue(execUpdate(srcConn, sql, english));

        boolean canWriteChinese = chineseCharsets.contains(colCharset);
        canWriteChinese |= colCharset.equals(NONE_CHARSET) && chineseCharsets.contains(tbCharset);
        canWriteChinese |= colCharset.equals(NONE_CHARSET) && tbCharset.equals(NONE_CHARSET)
            && chineseCharsets.contains(dbCharset);
        if (canWriteChinese) {
            Assert.assertTrue(execUpdate(srcConn, sql, chinese));
            recordCount += 1;
        }

        boolean canWriteEmoji = emojiCharsets.contains(colCharset);
        canWriteEmoji |= colCharset.equals(NONE_CHARSET) && emojiCharsets.contains(tbCharset);
        canWriteEmoji |= colCharset.equals(NONE_CHARSET) && tbCharset.equals(NONE_CHARSET)
            && emojiCharsets.contains(dbCharset);
        if (canWriteEmoji) {
            Assert.assertTrue(execUpdate(srcConn, sql, emoji));
            recordCount += 1;
        }

        checkDmlResult(db, table, recordCount);
    }

    private void checkDmlResult(String db, String table, int recordCount) throws Exception {
        wait(1);

        String sql = String.format("select * from %s.%s order by id", db, table);
        List<String> fields = Arrays.asList(ID, VALUE);
        List<Map<String, String>> srcRes = execQuery(srcConn, sql, fields);
        List<Map<String, String>> dstRes = execQuery(srcConn, sql, fields);
        List<Map<String, String>> mysqlRes = execQuery(srcConn, sql, fields);

        Assert.assertEquals(srcRes.size(), recordCount);
        Assert.assertEquals(dstRes.size(), recordCount);
        Assert.assertEquals(mysqlRes.size(), recordCount);

        for (int i = 0; i < srcRes.size(); i++) {
            Map<String, String> srcRecord = srcRes.get(i);
            Map<String, String> dstRecord = dstRes.get(i);
            Map<String, String> mysqlRecord = mysqlRes.get(i);
            Assert.assertEquals(srcRecord.get(VALUE), dstRecord.get(VALUE));
            Assert.assertEquals(srcRecord.get(VALUE), mysqlRecord.get(VALUE));
        }

        System.out.println("************************************************");
        System.out.println(String.format("%s.%s checked, record count: %d", db, table, recordCount));
    }
}
