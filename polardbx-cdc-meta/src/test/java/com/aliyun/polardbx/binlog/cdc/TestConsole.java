/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.cdc;

import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.repository.Schema;
import com.alibaba.polardbx.druid.sql.repository.SchemaObject;
import com.alibaba.polardbx.druid.sql.repository.SchemaRepository;
import com.alibaba.polardbx.druid.util.JdbcConstants;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 *
 */
@Slf4j
public class TestConsole {

    @Test
    public void t1() {
        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log);
        System.out.println(memoryTableMeta.snapshot());
        memoryTableMeta.apply(null, "d1", "create table t1 (id int)", null);
        memoryTableMeta.apply(null, "d1", "create table t1 (id int,name varchar(16))", null);
        System.out.println(memoryTableMeta.find("d1", "t1"));
        System.out.println(memoryTableMeta.snapshot());
        memoryTableMeta.snapshot().forEach((k, v) -> memoryTableMeta.apply(null, k, v, null));
    }

    @Test
    public void t2() {
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");
        repository.console("CREATE TABLE `t_ddl_test_JaV1_00` (\n"
            + "  `ID` bigint(20) NOT NULL AUTO_INCREMENT BY GROUP,\n"
            + "  `JOB_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `EXT_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `TV_ID` bigint(20) NOT NULL DEFAULT '0',\n"
            + "  `SCHEMA_NAME` varchar(200) NOT NULL,\n"
            + "  `TABLE_NAME` varchar(200) NOT NULL,\n"
            + "  `GMT_CREATED` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n"
            + "  `DDL_SQL` text NOT NULL,\n"
            + "  PRIMARY KEY (`ID`),\n"
            + "  KEY `idx1` (`SCHEMA_NAME`),\n"
            + "  KEY `auto_shard_key_job_id` USING BTREE (`JOB_ID`),\n"
            + "  GLOBAL INDEX `g_i_ext`(`EXT_ID`) COVERING (`ID`, `JOB_ID`) DBPARTITION BY HASH(`EXT_ID`),\n"
            + "  GLOBAL INDEX `g_i_tv`(`TV_ID`) COVERING (`ID`, `JOB_ID`, `GMT_CREATED`) DBPARTITION BY HASH(`TV_ID`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4  dbpartition by hash(`ID`) tbpartition by hash(`JOB_ID`) "
            + "tbpartitions 32");

        //repository.console("create index idx_gmt on t_ddl_test_JaV1_00(`gmt_created`)");
        repository.console("ALTER TABLE t_ddl_test_JaV1_00 ADD INDEX idx_gmt (`gmt_created`)");

        final SchemaObject t_ddl_test_jaV1_00 = repository.findTable("t_ddl_test_JaV1_00");
        System.out.println(t_ddl_test_jaV1_00);

        //repository.console("/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");
        //repository.console("/*DRDS /127.0.0.1/11d66d5292400000/ */DROP INDEX idx_gmt ON `t_ddl_test_JaV1_00`");

    }

    @Test
    public void t3() {
        final String create =
            "CREATE TABLE all_type ( type_bit bit(8) DEFAULT 0, type_tinyint tinyint DEFAULT 0, type_smallint "
                + "smallint DEFAULT 0, type_mediumint mediumint DEFAULT 0, type_int int DEFAULT 0, type_integer "
                + "integer DEFAULT 0, type_bigint bigint DEFAULT 0, type_decimal decimal(3, 2) DEFAULT 1.1, "
                + "type_float float(3, 2) DEFAULT 1.1, type_double double(3, 2) DEFAULT 1.1, type_date date DEFAULT "
                + "'2021-1-11 14:12:00', type_datetime datetime(3) DEFAULT '2021-1-11 14:12:00', type_timestamp "
                + "timestamp(3) DEFAULT '2021-1-11 14:12:00', type_time time(3) DEFAULT '14:12:00', type_year year "
                + "DEFAULT '2021', type_char char(10) CHARACTER SET utf8 DEFAULT '你好', type_varchar varchar(10) "
                + "CHARACTER SET gbk DEFAULT '你好', type_binary binary(10) DEFAULT '你好', type_varbinary varbinary(10) "
                + "DEFAULT '你好', type_blob blob(10) DEFAULT NULL, type_text text DEFAULT NULL, type_enum enum('a', "
                + "'b', 'c') DEFAULT 'a', type_set set('a', 'b', 'c', 'd') DEFAULT 'b', type_pt POINT DEFAULT NULL )";

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(log);
        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));
        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));
        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));
        memoryTableMeta.apply(new BinlogPosition("", ""), "d1", create, null);
        System.out.println(memoryTableMeta.find("d1", "all_type").getFieldMetaByName("type_char"));
    }

    @Test
    public void t4() {
        System.out.println("==============");
        SchemaRepository repository = new SchemaRepository(JdbcConstants.MYSQL);
        repository.setDefaultSchema("d1");
        repository.console("create table d1.t1 (id int,name varchar(16))");

        Schema schema = repository.findSchema("d1");
        System.out.println("schema=" + schema);
        SchemaObject table = schema.findTable("t1");
        System.out.println("table=" + table);

        StringBuffer buffer = new StringBuffer();
        table.getStatement().output(buffer);
        System.out.println(buffer);

        repository.console("ALTER TABLE d1.t1 ALGORITHM=INPLACE, LOCK=NONE, ADD INDEX `idx_id`(id)");

        buffer = new StringBuffer();
        table.getStatement().output(buffer);
        System.out.println(buffer);
        repository.console("ALTER TABLE d1.t1 ALGORITHM=INPLACE, LOCK=NONE, ADD INDEX `idx_id`(id)");
        buffer = new StringBuffer();
        if (table.getStatement() instanceof MySqlCreateTableStatement) {
            ((MySqlCreateTableStatement) table.getStatement()).normalizeTableOptions();
        }
        table.getStatement().output(buffer);
        System.out.println(buffer);

    }

}
