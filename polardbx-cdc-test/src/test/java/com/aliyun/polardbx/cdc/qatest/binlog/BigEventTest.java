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
import com.aliyun.polardbx.cdc.qatest.base.ConfigConstant;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.mysql.jdbc.PacketTooBigException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

/**
 * created by ziyang.lb
 */
@Slf4j
public class BigEventTest extends RplBaseTestCase {
    private static final String DB_NAME = "cdc_big_event";
    private static final String TABLE_NAME = "t_big_event";

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

    @Test
    public void testBigEvent() {
        JdbcUtil.executeUpdate(polardbxConnection, "CREATE TABLE `" + DB_NAME + "`.`" + TABLE_NAME + "` ( "
            + " `id` int unsigned NOT NULL AUTO_INCREMENT,"
            + " `content` longtext,"
            + " PRIMARY KEY (`id`))"
            + " dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (1,'a')");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (2, repeat('2',16*1024*1024))");
        JdbcUtil.executeSuccess(polardbxConnection,
            "insert into " + DB_NAME + "." + TABLE_NAME + " values (3, repeat('2',3*16*1024*1024))");

        waitAndCheck(CheckParameter.builder().dbName(DB_NAME).tbName(TABLE_NAME).build());
    }

    @Test
    public void testPacketTooBig() throws SQLException {
        Properties configProp = PropertiesUtil.configProp;
        String polardbxUser = configProp.getProperty(ConfigConstant.POLARDBX_USER);
        String polardbxPassword = configProp.getProperty(ConfigConstant.POLARDBX_PASSWORD);
        String polardbxPort = configProp.getProperty(ConfigConstant.POLARDBX_PORT);
        String polardbxAddress = configProp.getProperty(ConfigConstant.POLARDBX_ADDRESS);
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&maxAllowedPacket=1048576",
            polardbxAddress, polardbxPort, DB_NAME);

        try (Connection connection = JdbcUtil.createConnection(url, polardbxUser, polardbxPassword)) {
            String value = RandomStringUtils.randomAlphabetic(1024 * 1024 * 10);
            PreparedStatement ps = connection.prepareStatement(String.format("insert into %s values(?,?)", TABLE_NAME));
            ps.setInt(1, 4);
            ps.setString(2, value);
            ps.execute();
            Assert.fail("should trigger Error: Packet for query is too large (10485793 > 1048576). "
                + "You can change this value on the server by setting the max_allowed_packet' variable");
        } catch (PacketTooBigException ignored) {
        }
    }
}
