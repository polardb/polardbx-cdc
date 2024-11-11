/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base.canal;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.alibaba.otter.canal.protocol.position.LogPosition;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;
import static com.aliyun.polardbx.cdc.qatest.base.canal.CanalParameterBuilder.buildCanalParameter;
import static com.aliyun.polardbx.cdc.qatest.base.canal.CanalReaderMetaManager.loadDataFromDB;

@Slf4j
public class CanalBaseTest extends RplBaseTestCase {
    public static final String DESTINATION_1 = "canal_dest_1";
    public static final String DESTINATION_2 = "canal_dest_2";

    private long lastPrintTime;
    private final Set<String> dumpFiles = new HashSet<>();

    public void dump(String destination) throws InterruptedException, SQLException {
        if (usingBinlogX) {
            return;
        }
        tryDropMysqlDatabase();
        sendTokenAndWait(CheckParameter.builder().build());

        CanalReaderMetaManager.MetaInstanceData lastStopPosition = loadDataFromDB(destination);
        log.info("last stop position is {}, for destination {}.",
            JSONObject.toJSONString(lastStopPosition), destination);
        CanalReader canalReader = new CanalReader(destination, buildCanalParameter());
        canalReader.start();

        sendTokenAndWait(CheckParameter.builder().build());
        String position = showMasterStatus();
        log.info("before canal dump, master position is {}, destination {}.", position, destination);
        sendTokenAndWait(CheckParameter.builder().build());

        long start = System.currentTimeMillis();
        String latestPosition = "";
        String latestFileName = "";
        while (true) {
            Message message = canalReader.getAndAck();
            if (message != null && message.getEntries() != null && !message.getEntries().isEmpty()) {
                message.getEntries().forEach(e -> dumpFiles.add(e.getHeader().getLogfileName()));

                CanalEntry.Entry entry = message.getEntries().get(message.getEntries().size() - 1);
                latestPosition = entry.getHeader().getLogfileName() + ":" + entry.getHeader().getLogfileOffset();
                latestFileName = entry.getHeader().getLogfileName();

                if (System.currentTimeMillis() - lastPrintTime > 1000 * 10) {
                    log.info("latest dump position for destination {} is {}", destination, latestPosition);
                    lastPrintTime = System.currentTimeMillis();
                }
            } else {
                Thread.sleep(100);
            }

            if (latestPosition.compareTo(position) > 0 && loadDataFromDB(destination) != null) {
                log.info("end canal dump, end position is {}, destination {}.", latestPosition, destination);
                break;
            }

            if (System.currentTimeMillis() - start > 1000 * 1200) {
                throw new RuntimeException("canal dump timeout!");
            }
        }

        if (lastStopPosition != null) {
            LogPosition beginPos = (LogPosition) lastStopPosition.getClientDatas().get(0).getCursor();
            Integer beginIndex = Integer.parseInt(
                StringUtils.substringAfter(beginPos.getPostion().getJournalName(), "."));
            Integer endIndex = Integer.parseInt(
                StringUtils.substringAfter(latestFileName, "."));
            Assert.assertTrue(dumpFiles.size() >= (endIndex - beginIndex));
        }
    }

    private String showMasterStatus() throws SQLException {
        try (Connection connection = getPolardbxConnection()) {
            ResultSet resultSet = JdbcUtil.executeQuery("show master status", connection);
            if (resultSet.next()) {
                return resultSet.getString("FILE") + ":" + resultSet.getString("POSITION");
            }
        }

        throw new RuntimeException("show master status is null");
    }

    private void tryDropMysqlDatabase() throws SQLException {
        // 执行show tables from mysql会报NPE的错误，而mysql库不应该存在，是CN的一个bug，bug未修复前，先采取强制删除的策略
        try (Connection connection = getPolardbxConnection()) {
            JdbcUtil.executeUpdate(connection, "drop database if exists `mysql`");
        }
    }

}
