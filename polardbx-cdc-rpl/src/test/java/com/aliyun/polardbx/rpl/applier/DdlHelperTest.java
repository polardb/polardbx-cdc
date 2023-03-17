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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;

/**
 * @author shicai.xsc 2021/4/19 11:18
 * @since 5.0.0.0
 */
public class DdlHelperTest {

    @Test
    public void getOriginalSql() {
        String sql = "# POLARX_ORIGIN_SQL=CREATE DATABASE BalancerTestBase MODE 'auto'\n"
            + "# POLARX_TSO=699138551269084371215224507282353070080000000000000000\n"
            + "CREATE DATABASE BalancerTestBase CHARACTER SET utf8mb4";
        String originSql = DdlHelper.getOriginSql(sql);
        Assert.assertEquals("CREATE DATABASE BalancerTestBase MODE 'auto'",
            originSql);
    }

    @Test
    public void getDdlSqlContext() {
        String sql = "# POLARX_ORIGIN_SQL=CREATE DATABASE BalancerTestBase MODE 'auto'\n"
            + "# POLARX_TSO=699138551269084371215224507282353070080000000000000000\n"
            + "CREATE DATABASE BalancerTestBase CHARACTER SET utf8mb4";
        DefaultQueryLog queryLog = new DefaultQueryLog("", sql, new Timestamp(12345), 0);
        SqlContext context = ApplyHelper.getDdlSqlContext(queryLog, "699138551269084371215224507282353070080000000000000000", true);
        Assert.assertEquals(context.getSql(), "CREATE DATABASE BalancerTestBase MODE 'auto'");
    }

    @Test
    public void getTso_1() {
        String sql = "# POLARX_ORIGIN_SQL=ALTER TABLE `cdc_datatype`.`numeric` DROP COLUMN _NUMERIC_\n"
            + "# POLARX_TSO=699124861471450732815223138302086389760000000000000000\n"
            + "ALTER TABLE `cdc_datatype`.`numeric`\n"
            + "  DROP COLUMN _NUMERIC_";
        String tso = DdlHelper.getTso(sql, new Timestamp(1666843560), "binlog.000004:0000021718#1769892875.1666843560");
        Assert.assertEquals(tso, "678700134612901433613180665615960145920000000000000000");
    }

    @Test
    public void getTso_2() {
        String sql = "/*POLARX_ORIGIN_SQL=CREATE TABLE aaaaaa (\n" + "    id int,\n" + "    value int,\n"
            + "    INDEX `auto_shard_key_id` USING BTREE(`ID`),\n"
            + "    _drds_implicit_id_ bigint AUTO_INCREMENT,\n" + "    PRIMARY KEY (_drds_implicit_id_)\n"
            + ")\n" + "DBPARTITION BY hash(id)\n" + "TBPARTITION BY hash(id) TBPARTITIONS 2*/ "
            + "CREATE TABLE aaaaaa ( id int, value "
            + "int, INDEX `auto_shard_key_id` USING BTREE(`ID`) ) DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = "
            + "utf8mb4_general_ci";
        String tso = DdlHelper.getTso(sql, new Timestamp(1618802638), "");
        Assert.assertEquals("-3519920771618802638", tso);
    }
}
