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
package com.aliyun.polardbx.cdc.qatest.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * created by ziyang.lb
 **/
public class DdlTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_ddl_qatest";
    private static final String TB_NAME = "t_with_line_wrap";

    @BeforeClass
    public static void bootStrap() throws SQLException {
        prepareTestDatabase(DB_NAME);
    }

    @AfterClass
    public static void after() throws SQLException {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeSuccess(polardbxConnection, "DROP DATABASE IF EXISTS `" + DB_NAME + "`");
        }
    }

    // see https://aone.alibaba-inc.com/issue/49900554
    @Test
    public void testBigEvent() {
        String sql = "CREATE TABLE `" + DB_NAME + "`.`" + TB_NAME + "` (\n"
            + "  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '人群包配置id',\n"
            + "  `name` varchar(40) NOT NULL DEFAULT '' COMMENT '人群包名称',\n"
            + "  `pack_comment` varchar(40) NOT NULL DEFAULT '' COMMENT '备注\\n',\n"
            + "  `original_count` bigint(11) NOT NULL DEFAULT '0' COMMENT '原始人数',\n"
            + "  `effective_count` bigint(11) NOT NULL DEFAULT '0' COMMENT '有效人数',\n"
            + "  `orginal_url` varchar(100) NOT NULL DEFAULT '' COMMENT '原始人群包下载地址',\n"
            + "  `effective_url` varchar(100) NOT NULL DEFAULT '' COMMENT '有效人群包',\n"
            + "  PRIMARY KEY (`id`) USING BTREE\n"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='云挂机人群包配置';";
        JdbcUtil.executeUpdate(polardbxConnection, sql);
        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TB_NAME).build());
    }
}
