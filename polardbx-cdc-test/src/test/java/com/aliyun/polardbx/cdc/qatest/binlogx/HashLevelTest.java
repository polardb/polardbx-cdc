/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlogx;

import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.StreamHashUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * created by ziyang.lb
 **/
public class HashLevelTest extends RplBaseTestCase {
    private static final HashLevel DEFAULT_HASH_LEVEL = StreamHashUtil.getDefaultHashLevel();

    private static final String DB_LEVEL_HASH_DB = "db_level_hash_db";
    private static final String DB_LEVEL_HASH_TB = "db_level_hash_tb";
    private static final String TABLE_LEVEL_HASH_DB = "table_level_hash_db";
    private static final String TABLE_LEVEL_HASH_TB = "table_level_hash_tb";

    private static final String CREATE_TABLE_SQL =
        "create table if not exists %s("
            + "id bigint(20) NOT NULL auto_increment,"
            + "`name` varchar(20)  COLLATE utf8mb4_bin DEFAULT NULL, "
            + "`a_score_1` int DEFAULT NULL,"
            + "`a_score_2` int DEFAULT 1,"
            + "`age` int DEFAULT 1 ,"
            + "`message` varchar(200)  COLLATE utf8mb4_bin , "
            + "`extra` text  COLLATE utf8mb4_bin , "
            + "`update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
            + "primary key(id)"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 DEFAULT COLLATE = utf8mb4_bin";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_LEVEL_HASH_DB);
        prepareTestDatabase(DB_LEVEL_HASH_TB);
        prepareTestDatabase(TABLE_LEVEL_HASH_DB);
        prepareTestDatabase(TABLE_LEVEL_HASH_TB);
    }

    @Test
    public void testDbLevelHashDb() {
        //t1
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, "t_1"));
        buildInsertSql("t_1").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_LEVEL_HASH_DB).tbName("t_1").expectHashLevel(HashLevel.DATABASE).build());

        //t2
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, "t_2"));
        buildInsertSql("t_2").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_LEVEL_HASH_DB).tbName("t_2").expectHashLevel(HashLevel.DATABASE).build());
    }

    @Test
    public void testDbLevelHashTb() {
        //t1
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_TB), String.format(CREATE_TABLE_SQL, "t_1"));
        buildInsertSql("t_1").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_TB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(DB_LEVEL_HASH_TB).tbName("t_1").expectHashLevel(HashLevel.DATABASE).build());

        //t2
        JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_TB), String.format(CREATE_TABLE_SQL, "t_2"));
        buildInsertSql("t_2").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(DB_LEVEL_HASH_TB), s));
        waitAndCheck(CheckParameter.builder().
            dbName(DB_LEVEL_HASH_TB).tbName("t_2").expectHashLevel(DEFAULT_HASH_LEVEL).build());
    }

    @Test
    public void testTableLevelHashDb() {
        //t1
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, "t_1"));
        buildInsertSql("t_1").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName("t_1").expectHashLevel(HashLevel.TABLE).build());

        //t2
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, "t_2"));
        buildInsertSql("t_2").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder().
            dbName(TABLE_LEVEL_HASH_DB).tbName("t_2").expectHashLevel(HashLevel.TABLE).build());
    }

    @Test
    public void testTableLevelHashTb() {
        //t1
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_TB), String.format(CREATE_TABLE_SQL, "t_1"));
        buildInsertSql("t_1").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_TB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_TB).tbName("t_1").expectHashLevel(HashLevel.TABLE).build());

        //t2
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_TB), String.format(CREATE_TABLE_SQL, "t_2"));
        buildInsertSql("t_2").forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_TB), s));
        waitAndCheck(CheckParameter.builder().
            dbName(TABLE_LEVEL_HASH_TB).tbName("t_2").expectHashLevel(DEFAULT_HASH_LEVEL).build());
    }

    @Test
    public void testRename() {
        //create t_a
        String t_a = "t_a";
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, t_a));
        buildInsertSql(t_a).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_a).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq1 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_a);

        //rename t_a to t_b
        String t_b = "t_b";
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB),
            String.format("rename table %s to %s", t_a, t_b));
        buildInsertSql(t_b).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_b).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq2 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_b);

        //rename t_b to t_c
        String t_c = "t_c";
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB),
            String.format("rename table %s to %s", t_b, t_c));
        buildInsertSql(t_c).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_c).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq3 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_c);

        //rename t_c to t_a
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB),
            String.format("rename table %s to %s", t_c, t_a));
        buildInsertSql(t_a).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_a).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq4 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_a);

        //drop t_a
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), "drop table " + t_a);

        //recreate t_a
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, t_a));
        buildInsertSql(t_a).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_a).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq5 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_a);

        //recreate t_b
        JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), String.format(CREATE_TABLE_SQL, t_b));
        buildInsertSql(t_b).forEach(s -> JdbcUtil.executeUpdate(getPolardbxConnection(TABLE_LEVEL_HASH_DB), s));
        waitAndCheck(CheckParameter.builder()
            .dbName(TABLE_LEVEL_HASH_DB).tbName(t_b).expectHashLevel(HashLevel.TABLE).build());
        int streamSeq6 = StreamHashUtil.getHashStreamSeq(TABLE_LEVEL_HASH_DB, t_b);

        Assert.assertEquals(streamSeq1, streamSeq2);
        Assert.assertEquals(streamSeq2, streamSeq3);
        Assert.assertEquals(streamSeq3, streamSeq4);
        Assert.assertEquals(streamSeq4, streamSeq5);
        Assert.assertEquals(streamSeq5, streamSeq6);
    }

    private List<String> buildInsertSql(String table) {
        Random random = new Random();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String sql = String.format("insert into %s(`name`, age, message, extra) values('%s', %s, '%s', '%s')",
                table,
                "name_xxx",
                random.nextInt(10) + "",
                RandomStringUtils.randomAlphabetic(100),
                RandomStringUtils.randomAlphabetic(100));
            list.add(sql);
        }
        return list;
    }
}
