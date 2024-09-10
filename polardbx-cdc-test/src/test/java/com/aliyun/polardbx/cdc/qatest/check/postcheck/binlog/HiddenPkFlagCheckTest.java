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
package com.aliyun.polardbx.cdc.qatest.check.postcheck.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.amazonaws.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

@Slf4j
public class HiddenPkFlagCheckTest extends RplBaseTestCase {

    private static String DB_NAME = "test_hidden_pk_flag_db";
    private static String TABLE_NAME = "test_hidden_pk_flag_tb";

    public void enableAttachPk() throws SQLException {
        enableCdcConfig(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "true");
    }

    public void disableAttachPk() throws SQLException {
        enableCdcConfig(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "false");
    }

    @Test
    public void afterCheck() throws SQLException {
        if (usingBinlogX) {
            return;
        }
        disableAttachPk();
    }

    @Test(timeout = 1000 * 60 * 10)
    public void checkHiddenPkFlag() throws Exception {
        if (usingBinlogX) {
            return;
        }
        DB_NAME = DB_NAME + StringUtils.lowerCase(RandomStringUtils.randomAlphabetic(5));
        TABLE_NAME = TABLE_NAME + StringUtils.lowerCase(RandomStringUtils.randomAlphabetic(5));
        try (Connection conn = getPolardbxConnection()) {
            JdbcUtil.executeSuccess(conn, String.format("create database if not exists `%s`", DB_NAME));
            JdbcUtil.useDb(conn, DB_NAME);
            JdbcUtil.executeSuccess(conn,
                String.format("create table if not exists `%s`(`name` varchar(20))", TABLE_NAME));
        }
        testFlag(0);
        testFlag(1);
        disableAttachPk();
    }

    private void testFlag(int attachFlag) throws Exception {
        sendTokenAndWait(CheckParameter.builder().build());
        int flag;
        if (attachFlag == 1) {
            enableAttachPk();
            flag = RowsLogEvent.HIDDEN_PK_FLAG;
        } else {
            disableAttachPk();
            flag = 0;
        }
        BinlogPosition start = getMasterBinlogPosition();
        log.info("record start pos ({}:{})", start.getFileName(), start.getPosition());
        String insertSql1 = String.format("insert into `%s`.`%s` values('%s'),('%s'),('%s')", DB_NAME, TABLE_NAME,
            RandomStringUtils.randomAlphabetic(10),
            RandomStringUtils.randomAlphabetic(10),
            RandomStringUtils.randomAlphabetic(10));
        JdbcUtil.executeSuccess(polardbxConnection, insertSql1);
        log.info("insertSql1 : {}", insertSql1);

        String insertSql2 = String.format("insert into `%s`.`%s` values('%s')", DB_NAME, TABLE_NAME,
            RandomStringUtils.randomAlphabetic(10));
        JdbcUtil.executeSuccess(polardbxConnection, insertSql2);
        log.info("insertSql2 : {}", insertSql2);
        sendTokenAndWait(CheckParameter.builder().build());
        AtomicInteger countObject = new AtomicInteger(0);
        checkBinlogCallback(start, (event, context) -> {
            if (event instanceof RowsLogEvent) {
                RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
                if (rowsLogEvent.getTable().getTableName().equalsIgnoreCase(TABLE_NAME)) {
                    log.info("pos : {}, col len : {}", rowsLogEvent.getHeader().getLogPos(),
                        rowsLogEvent.getColumnLen());
                    Assert.assertEquals(flag, rowsLogEvent.getFlags(RowsLogEvent.HIDDEN_PK_FLAG));
                    countObject.incrementAndGet();
                }
            }
        });
        Assert.assertEquals(2, countObject.get());
    }
}
