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

package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.BinlogEventParser;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.InitialSinkFunction;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection.BinlogFormat;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection.BinlogImage;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection.ProcessJdbcResult;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkResult;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.SearchPositionParam;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 基于向mysql server复制binlog实现
 *
 * @author agapple 2017年7月20日 下午10:16:21
 * @since 3.2.5
 */
public class MysqlEventParser extends AbstractMysqlEventParser implements BinlogEventParser {

    private static final Logger logger = LoggerFactory.getLogger(MysqlEventParser.class);
    private MysqlConnection metaConnection;                                                             // 查询meta信息的链接

    private int fallbackIntervalInSeconds = 60;                                             // 切换回退时间

    private BinlogFormat[] supportBinlogFormats;
    // 支持的binlogFormat,如果设置会执行强校验
    private BinlogImage[] supportBinlogImages;
    // 支持的binlogImage,如果设置会执行强校验

    // update by yishun.chen,特殊异常处理参数
    private int dumpErrorCount = 0;                                              // binlogDump失败异常计数
    private int dumpTimeoutCount = 0;
    private int dumpErrorCountThreshold = 2;                                              // binlogDump失败异常计数阀值

    public MysqlEventParser() {
        supportBinlogFormats = new BinlogFormat[] {BinlogFormat.ROW};
        supportBinlogImages = new BinlogImage[] {BinlogImage.FULL};
    }

    @Override
    protected ErosaConnection buildErosaConnection() {
        return buildMysqlConnection(this.runningInfo);
    }

    @Override
    protected void preDump(ErosaConnection connection) {
        if (!(connection instanceof MysqlConnection)) {
            throw new CanalParseException("Unsupported connection type : " + connection.getClass().getSimpleName());
        }

        metaConnection = (MysqlConnection) connection.fork();
        try {
            metaConnection.connect();
        } catch (IOException e) {
            throw new CanalParseException(e);
        }

        if (supportBinlogFormats != null && supportBinlogFormats.length > 0) {
            BinlogFormat format = ((MysqlConnection) metaConnection).getBinlogFormat();
            boolean found = false;
            for (BinlogFormat supportFormat : supportBinlogFormats) {
                if (supportFormat != null && format == supportFormat) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new CanalParseException("Unsupported BinlogFormat " + format);
            }
        }

        if (supportBinlogImages != null && supportBinlogImages.length > 0) {
            BinlogImage image = ((MysqlConnection) metaConnection).getBinlogImage();
            boolean found = false;
            for (BinlogImage supportImage : supportBinlogImages) {
                if (supportImage != null && image == supportImage) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new CanalParseException("Unsupported BinlogImage " + image);
            }
        }
        if (serverCharactorSet == null) {
            serverCharactorSet = ((MysqlConnection) metaConnection).getDefaultDatabaseCharset();
            if (serverCharactorSet == null || serverCharactorSet.getCharacterSetClient() == null
                || serverCharactorSet.getCharacterSetConnection() == null
                || serverCharactorSet.getCharacterSetDatabase() == null
                || serverCharactorSet.getCharacterSetServer() == null) {
                throw new CanalParseException("not found default database charset");
            }
        }
        ((MysqlConnection) connection).setServerCharactorset(serverCharactorSet);

        lowerCaseTableNames = metaConnection.getLowerCaseTableNames();
    }

    // =================== helper method =================

    @Override
    protected void afterDump(ErosaConnection connection) {
        super.afterDump(connection);

        if (!(connection instanceof MysqlConnection)) {
            throw new CanalParseException("Unsupported connection type : " + connection.getClass().getSimpleName());
        }

        if (metaConnection != null) {
            try {
                metaConnection.disconnect();
            } catch (IOException e) {
                logger.error("ERROR # disconnect meta connection for address:" + metaConnection.getAddress(), e);
            }
        }
    }

    @Override
    public void stop() {
        if (metaConnection != null) {
            try {
                metaConnection.disconnect();
            } catch (IOException e) {
                logger.error("ERROR # disconnect meta connection for address:" + metaConnection.getAddress(), e);
            }
        }
        eventHandler = null;
        super.stop();
    }

    private MysqlConnection buildMysqlConnection(AuthenticationInfo runningInfo) {
        MysqlConnection connection = new MysqlConnection(runningInfo);
        return connection;
    }

    @Override
    protected BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) throws IOException {
        return findStartPositionInternal(connection, position);
    }

    protected BinlogPosition findStartPositionInternal(ErosaConnection connection, BinlogPosition entryPosition) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        long currentServerId = findServerId(mysqlConnection);
        this.currentServerId = currentServerId;
        logger.warn("prepare to find start position by last position ::" + entryPosition.getTimestamp());
        BinlogPosition findPosition = findByStartTimeStamp(mysqlConnection, entryPosition.getTso());
        // 重新置为一下
        dumpErrorCount = 0;
        return findPosition;
    }

    // 根据时间查找binlog位置
    protected BinlogPosition findByStartTimeStamp(MysqlConnection mysqlConnection, Long startTso) {
        BinlogPosition endPosition = findEndPosition(mysqlConnection);
        BinlogPosition startPosition = findStartPosition(mysqlConnection);
        String maxBinlogFileName = endPosition.getFileName();
        String minBinlogFileName = startPosition.getFileName();
        logger.info("show master status to set search end condition: " + endPosition);
        String startSearchBinlogFile = endPosition.getFileName();
        boolean shouldBreak = false;
        while (running && !shouldBreak) {
            try {
                BinlogPosition entryPosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                    new SearchPositionParam(startTso),
                    endPosition,
                    startSearchBinlogFile);
                if (entryPosition == null) {
                    if (StringUtils.equalsIgnoreCase(minBinlogFileName, startSearchBinlogFile)) {
                        // 已经找到最早的一个binlog，没必要往前找了
                        shouldBreak = true;
                        logger.warn("Didn't find the corresponding binlog files from " + minBinlogFileName + " to "
                            + maxBinlogFileName);
                    } else {
                        // 继续往前找
                        int binlogSeqNum = Integer
                            .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                        if (binlogSeqNum <= 1) {
                            logger.warn("Didn't find the corresponding binlog files");
                            shouldBreak = true;
                        } else {
                            int nextBinlogSeqNum = binlogSeqNum - 1;
                            String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                                startSearchBinlogFile.indexOf(".") + 1);
                            String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                            startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                        }
                    }
                } else {
                    logger.info("found and return:" + entryPosition + " in findByStartTimeStamp operation.");
                    return entryPosition;
                }
            } catch (Exception e) {
                logger.warn("the binlogfile:" + startSearchBinlogFile
                    + " doesn't exist, to continue to search the next binlogfile , caused by "
                    + ExceptionUtils.getStackTrace(e));
                int binlogSeqNum = Integer
                    .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                if (binlogSeqNum <= 1) {
                    logger.warn("Didn't find the corresponding binlog files");
                    shouldBreak = true;
                } else {
                    int nextBinlogSeqNum = binlogSeqNum - 1;
                    String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                        startSearchBinlogFile.indexOf(".") + 1);
                    String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                    startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                }
            }
        }
        // 找不到
        return null;
    }

    /**
     * 查询当前db的serverId信息
     */
    protected Long findServerId(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show variables like 'server_id'", new ProcessJdbcResult<Long>() {

            @Override
            public Long process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    return rs.getLong(2);
                } else {
                    throw new CanalParseException(
                        "command : show variables like 'server_id' has an error! pls check. you need (at least one "
                            + "of) the SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    /**
     * 查询当前的binlog位置
     */
    protected BinlogPosition findEndPosition(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show master status", new ProcessJdbcResult<BinlogPosition>() {

            @Override
            public BinlogPosition process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    String fileName = rs.getString(1);
                    String position = rs.getString(2);
                    String str = fileName + ':' + position + "#-2.0";
                    return BinlogPosition.parseFromString(str);
                } else {
                    throw new CanalParseException(
                        "command : 'show master status' has an error! pls check. you need (at least one of) the "
                            + "SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    /**
     * 查询当前的binlog位置
     */
    protected BinlogPosition findStartPosition(MysqlConnection mysqlConnection) {
        return mysqlConnection.query("show binlog events limit 1", new ProcessJdbcResult<BinlogPosition>() {

            @Override
            public BinlogPosition process(ResultSet rs) throws SQLException {
                if (rs.next()) {
                    String fileName = rs.getString(1);
                    String position = rs.getString(2);
                    String str = fileName + ':' + position + "#-2.0";
                    return BinlogPosition.parseFromString(str);
                } else {
                    throw new CanalParseException(
                        "command : 'show binlog events limit 1' has an error! pls check. you need (at least one of) "
                            + "the SUPER,REPLICATION CLIENT privilege(s) for this operation");
                }
            }
        });
    }

    private long seekBinlogFile(String searchFileName, MysqlConnection connection) throws IOException {
        if (StringUtils.isBlank(searchFileName)) {
            return -1;
        }
        connection.reconnect();
        return connection.query("show binary logs", new ProcessJdbcResult<Long>() {

            @Override
            public Long process(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    String fileName = rs.getString(1);
                    if (fileName.equalsIgnoreCase(searchFileName)) {
                        return rs.getLong(2);
                    }
                }
                return -1L;
            }
        });
    }

    /**
     * 根据给定的时间戳，在指定的binlog中找到最接近于该时间戳(必须是小于时间戳)的一个事务起始位置。
     * 针对最后一个binlog会给定endPosition，避免无尽的查询
     */
    private BinlogPosition findAsPerTimestampInSpecificLogFile(MysqlConnection mysqlConnection,
                                                               final SearchPositionParam positionParam,
                                                               final BinlogPosition endPosition,
                                                               final String searchBinlogFile) {

        try {
            long totalFileSize = seekBinlogFile(searchBinlogFile, mysqlConnection);
            mysqlConnection.reconnect();
            logger.info("seek file : " + searchBinlogFile + " for pos " + positionParam);

            long startPos = 4L;
            if (searchFunction instanceof InitialSinkFunction) {
                ((InitialSinkFunction) searchFunction).setEndPosition(endPosition);
                ((InitialSinkFunction) searchFunction).setTotalSize(totalFileSize);
                ((InitialSinkFunction) searchFunction).setCurrentFile(searchBinlogFile);
                ((InitialSinkFunction) searchFunction).reset();
            }
            // 开始遍历文件
            mysqlConnection.seek(searchBinlogFile, startPos, searchFunction);
            if (searchFunction instanceof SinkResult) {
                return ((SinkResult) searchFunction).searchResult();
            }
        } catch (Exception e) {
            logger.error("ERROR ## findAsPerTimestampInSpecificLogFile has an error", e);
        }

        return null;
    }

    @Override
    protected void processDumpError(Throwable e) {
        if (e instanceof IOException) {
            String message = e.getMessage();
            if (StringUtils.contains(message, "errno = 1236")) {
                // 1236 errorCode代表ER_MASTER_FATAL_ERROR_READING_BINLOG
                dumpErrorCount++;
            }
        } else if (e instanceof SocketTimeoutException) {
            dumpTimeoutCount++;
        } else {
            dumpTimeoutCount = 0;
            dumpErrorCount = 0;
        }
    }

    public void setSearchFunction(SinkFunction searchFunction) {
        this.searchFunction = searchFunction;
    }

    private class TailLogEventFilter implements LogEventFilter {

        @Override
        public void handle(HandlerEvent event, HandlerContext context) throws Exception {
            dumpTimeoutCount = 0;
            dumpErrorCount = 0;
            MysqlEventParser.this.eventHandler.handle(event);
        }

        @Override
        public void onStart(HandlerContext context) {

        }

        @Override
        public void onStop() {

        }

        @Override
        public void onStartConsume(HandlerContext context) {

        }
    }

}
