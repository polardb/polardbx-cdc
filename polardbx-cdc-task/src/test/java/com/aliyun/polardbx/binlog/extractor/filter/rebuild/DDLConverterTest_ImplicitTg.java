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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-11-21 15:34
 **/
public class DDLConverterTest_ImplicitTg extends BaseTest {

    @Test
    public void test() {
        /*
         create table with implicit table group
         */
        String resultSql = process("create table t1(a int) "
            + "partition by key(a) partitions 3 with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE TABLE t1 ( a int ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci", resultSql);

        resultSql = process("create table t1(a int) single with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE TABLE t1 ( a int ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci", resultSql);

        resultSql = process("create table t1(a int) broadcast with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE TABLE t1 ( a int ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci", resultSql);

        resultSql = process("create table t1("
            + " a int primary key,"
            + " b int,"
            + " c int,"
            + " d varchar(10),"
            + " global index (b) partition by key(b) partitions 2 with tablegroup='tgi2' implicit, "
            + " unique global index g2(c) partition by key(c) partitions 2 with tablegroup='tgi2' implicit, "
            + " unique global key g3(c) partition by key(c) partitions 2 with tablegroup='tgi2' implicit,"
            + " global unique index g4(c) partition by key(c) partitions 2 with tablegroup='tgi2' implicit,"
            + " global unique key g5(c) partition by key(c) partitions 2 with tablegroup='tgi2' implicit)"
            + " partition by key(a) partitions 3 with tablegroup='tgi1' implicit");
        Assert.assertEquals("CREATE TABLE t1 ( "
            + "a int PRIMARY KEY, "
            + "b int, "
            + "c int, "
            + "d varchar(10), "
            + "INDEX b(b), "
            + "UNIQUE KEY g2 (c), "
            + "UNIQUE KEY g3 (c), "
            + "UNIQUE KEY g4 (c), "
            + "UNIQUE KEY g5 (c) ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci", resultSql);

        resultSql = process("create table t1("
            + " a int primary key,"
            + " b int,"
            + " c int,"
            + " d varchar(10) unique,"
            + " index g1(b) with tablegroup='tgi2' implicit,"
            + " key g2(b) with tablegroup='tgi2' implicit, "
            + " unique key g3(b) with tablegroup='tgi2' implicit, "
            + " unique index g4(b) with tablegroup='tgi2' implicit) with tablegroup='tgi1' implicit");
        Assert.assertEquals("CREATE TABLE t1 ( "
            + "a int PRIMARY KEY, "
            + "b int, "
            + "c int, "
            + "d varchar(10) UNIQUE, "
            + "INDEX g1(b), "
            + "KEY g2 (b), "
            + "UNIQUE KEY g3 (b), "
            + "UNIQUE KEY g4 (b) ) "
            + "DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_unicode_520_ci", resultSql);

        /*
           create index with implicit table group
         */
        resultSql = process("create global index g1 on t1(a) "
            + "partition by key(a) partitions 2 with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE INDEX g1 ON t1 (a)", resultSql);

        resultSql = process("create unique global index g1 on t1(a) "
            + "partition by key(a) partitions 2 with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE UNIQUE INDEX g1 ON t1 (a)", resultSql);

        resultSql = process("create global unique index g1 on t1(a) "
            + "partition by key(a) partitions 2 with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE UNIQUE INDEX g1 ON t1 (a)", resultSql);

        resultSql = process("create index g1 on t1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE INDEX g1 ON t1 (a)", resultSql);

        resultSql = process("create unique index g1 on t1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE UNIQUE INDEX g1 ON t1 (a)", resultSql);

        resultSql = process("create unique key g1 on t1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("CREATE UNIQUE INDEX g1 ON t1 (a)", resultSql);

        /*
            alter table add index with implicit table group
         */
        resultSql = process("alter table t1 add global index g1(a) partition by key(a) partitions 2 "
            + "with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD INDEX g1 (a)", resultSql);

        resultSql = process("alter table t1 add global unique index g1(a) partition by key(a) partitions 2 "
            + "with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD UNIQUE INDEX g1 (a)", resultSql);

        resultSql = process("alter table t1 add unique global index g1(a) partition by key(a) partitions 2 "
            + "with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD UNIQUE INDEX g1 (a)", resultSql);

        resultSql = process("alter table t1 add index idx1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD INDEX idx1 (a)", resultSql);

        resultSql = process("alter table t1 add unique index idx1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD UNIQUE INDEX idx1 (a)", resultSql);

        resultSql = process("alter table t1 add key idx1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD KEY idx1 (a)", resultSql);

        resultSql = process("alter table t1 add unique key idx1(a) with tablegroup='tgi2' implicit");
        Assert.assertEquals("ALTER TABLE t1 ADD UNIQUE KEY idx1 (a)", resultSql);

    }

    private String process(String sql) {
        StringBuilder sb = new StringBuilder();
        DDLConverter.buildDdlEventSqlForMysqlPart(sb, null, "utf8mb4", "utf8mb4_unicode_520_ci", sql);
        System.out.println(sb);
        return sb.toString();
    }
}
