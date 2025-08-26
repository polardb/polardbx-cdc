/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.CdcClient;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.amazonaws.util.StringUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

public class CdcClientTest extends RplBaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(CdcClientTest.class);

    private static final long timeoutInMinute = 30;

    private Throwable t;
    DBMSTransactionBegin begin = null;
    boolean find = false;
    private String currentBinlogFile;
    private static String TABLE_NAME = "t1_";
    private static String SCHEMA_NAME = "cdc_client_test_";
    private static final String QUERY_LAB_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";

    private void enableHiddenPk(String value) throws SQLException {
        if (usingBinlogX) {
            return;
        }
        enableCdcConfig(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, value);
    }

    @After
    public void doAfter() throws SQLException {
        enableHiddenPk("false");
    }

    @Test
    public void testCdcClient() throws Throwable {
        if (usingBinlogX) {
            return;
        }
        // 检查是否开启archive事件过滤， 开启过滤则忽略该测试（一定不会通过）
        try (Connection metaConnection = getMetaConnection()) {
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_LAB_EVENT, LabEventType.TASK_FILTER_ARCHIVE_ENABLED.ordinal()),
                    metaConnection);
            if (rs.next()) {
                return;
            }
        }
        TABLE_NAME = TABLE_NAME + StringUtils.lowerCase(RandomStringUtils.randomAlphabetic(10));
        SCHEMA_NAME = SCHEMA_NAME + StringUtils.lowerCase(RandomStringUtils.randomAlphabetic(10));
        sendTokenAndWait(CheckParameter.builder().build());
        enableHiddenPk("true");
        try (Connection polarxConnection = getPolardbxConnection()) {
            JdbcUtil.executeSuccess(polarxConnection,
                String.format("create database if not exists `%s`", SCHEMA_NAME));
            JdbcUtil.useDb(polarxConnection, SCHEMA_NAME);
            JdbcUtil.executeSuccess(polarxConnection,
                String.format("create table if not exists `%s`(name varchar(20))", TABLE_NAME));
            JdbcUtil.executeSuccess(polarxConnection, "set transaction_policy = archive");
            JdbcUtil.executeSuccess(polarxConnection, "begin");
            JdbcUtil.executeSuccess(polarxConnection,
                String.format("insert into `%s` values('archive_test')", TABLE_NAME));
            JdbcUtil.executeSuccess(polarxConnection, "commit");

            sendTokenAndWait(CheckParameter.builder().build());
            CdcClient cdcClient = new CdcClient(this::getMetaConnection);

            CountDownLatch countDownLatch = new CountDownLatch(1);
            cdcClient.setExceptionHandler(t -> {
                CdcClientTest.this.t = t;
                logger.error("cdc client dumper error!", t);
                countDownLatch.countDown();
            });

            cdcClient.setBinaryData();
            BinlogPosition endPosition = getMasterBinlogPosition();
            String endFile = endPosition.getFileName();
            long endPos = endPosition.getPosition();
            logger.info(String.format("record end offset %s:%s", endFile, endPos
                + ""));
            String startFile = resetBinlogFileName(endFile);
            cdcClient.startAsync(startFile, 4L, cdcEventData -> {
                String binlogFileName = cdcEventData.getBinlogFileName();
                if (!org.apache.commons.lang3.StringUtils.equals(binlogFileName, currentBinlogFile)) {
                    currentBinlogFile = binlogFileName;
                    logger.info("begin to process file : {}", currentBinlogFile);
                }
                if (cdcEventData.getEvent() instanceof DBMSTransactionBegin) {
                    begin = (DBMSTransactionBegin) cdcEventData.getEvent();
                } else if (cdcEventData.getEvent() instanceof DefaultRowChange) {
                    DefaultRowChange rowChange = (DefaultRowChange) cdcEventData.getEvent();
                    if (rowChange.getSchema().contains(SCHEMA_NAME) && rowChange.getTable().contains(TABLE_NAME)) {
                        Assert.assertTrue(String.format("%s should be archive event!", SCHEMA_NAME), begin.isArchive());
                        Assert.assertTrue(String.format("%s.%s should attach hidden pk flag!", SCHEMA_NAME, TABLE_NAME),
                            rowChange.isHasHiddenPk());
                        find = true;
                    }
                }

                int fileOffset = BinlogFileUtil.compareBinlogFileName(binlogFileName, endFile);
                if ((fileOffset == 0 && cdcEventData.getPosition() >= endPos) || fileOffset > 0) {
                    countDownLatch.countDown();
                }
            });
            logger.info(String.format("cdc client start @ pos %s:%s", startFile, 4L));
            countDownLatch.await(timeoutInMinute, TimeUnit.MINUTES);
            Assert.assertTrue(String.format("do not find %s.%s any event!", SCHEMA_NAME, TABLE_NAME),
                find);
            if (t != null) {
                throw t;
            }
        }

    }

    public static String resetBinlogFileName(String fileName) {
        String[] fn = fileName.split("\\.");
        return fn[0] + "." + "000001";
    }

    public static int getFileNum(String fileName) {
        String[] fn = fileName.split("\\.");
        return Integer.parseInt(fn[1]);
    }
}
