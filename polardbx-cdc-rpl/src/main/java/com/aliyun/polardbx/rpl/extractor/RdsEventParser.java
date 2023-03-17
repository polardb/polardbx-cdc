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
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author shicai.xsc 2020/11/27 17:37
 * @since 5.0.0.0
 * Difference between RdsEventParser and MysqlEventParser: whether need to care about server id
 */
@Slf4j
public class RdsEventParser extends MysqlEventParser {

    public RdsEventParser(int bufferSize, EventRepository eventRepository) {
        super(bufferSize, eventRepository);
    }

    @Override
    protected boolean consumeTheEventAndProfilingIfNecessary(List<MySQLDBMSEvent> entrys) throws CanalParseException {
        // 收到数据了,重置一下
        dumpTimeoutCount = 0;
        dumpErrorCount = 0;

        long startTs = -1;
        boolean enabled = getProfilingEnabled();
        if (enabled) {
            startTs = System.currentTimeMillis();
        }

        boolean result = eventSink.sink(entrys);
        if (enabled) {
            this.processingInterval = System.currentTimeMillis() - startTs;
        }

        if (consumedEventCount.incrementAndGet() < 0) {
            consumedEventCount.set(0);
        }

        return result;
    }

    @Override
    protected BinlogPosition findStartPositionInternal(ErosaConnection connection, BinlogPosition entryPosition) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        this.currentServerId = findServerId(mysqlConnection);
        if (entryPosition == null) {// 找不到历史成功记录
            return findEndPositionWithMasterIdAndTimestamp(mysqlConnection); // 默认从当前最后一个位置进行消费
        }

        // binlog定位位点失败,可能有两个原因:
        // 1. binlog位点被删除
        // 2. vip模式的mysql,发生了主备切换,判断一下serverId是否变化,针对这种模式可以发起一次基于时间戳查找合适的binlog位点
        // 3. 各种模式下的初始位点时，binlog filename无效
        // 此时需要清除下游写入侧的table position
        boolean case1 = (dumpErrorCountThreshold >= 0 && dumpErrorCount > dumpErrorCountThreshold);
        boolean case2 = (entryPosition.getMasterId() != currentServerId);
        boolean case3 = CommonUtil.isMeaninglessBinlogFileName(entryPosition);
        if (case1 || case2 || case3) {
            log.info("current server id : {}, need server id : {}", currentServerId, entryPosition.getMasterId());
            long timestamp = entryPosition.getTimestamp();
            BinlogPosition findPosition;
            // 当timestamp > 0 且未找到位点时返回null，此时可能该时间戳对应的位点处于oss binlog上
            if (timestamp > 0L) {
                long newStartTimestamp = timestamp - RplConstants.FALLBACK_INTERVAL_SECONDS;
                log.warn("prepare to find start position by last position ::" + entryPosition.getTimestamp());
                findPosition = findByStartTimeStamp(mysqlConnection, newStartTimestamp);
            } else {
                // entryPosition.getTimestamp() == 0 -> replica or just man made meaningless position
                log.warn("prepare to find start position just show master status");
                findPosition = findEndPositionWithMasterIdAndTimestamp(mysqlConnection); // 默认从当前最后一个位置进行消费
            }
            // 重新置位一下
            dumpErrorCount = 0;
            return findPosition;
        } else {
            if (entryPosition.getPosition() >= 0L) {
                BinlogPosition position = findStartPosition(mysqlConnection);
                // 对应的binlog被备份至oss
                if (entryPosition.getFileName().compareTo(position.getFileName()) < 0) {
                    return null;
                }
                // 如果指定binlogName + offest，直接返回
                log.warn("prepare to find start position just last position " + entryPosition.getFileName() + ":"
                    + entryPosition.getPosition() + ":");
                return entryPosition;
            } else {
                BinlogPosition specificLogFilePosition = null;
                if (entryPosition.getTimestamp() > 0L) {
                    // 如果指定binlogName +
                    // timestamp，但没有指定对应的offest，尝试根据时间找一下offest
                    BinlogPosition endPosition = findEndPosition(mysqlConnection);
                    if (endPosition != null) {
                        log.warn("prepare to find start position " + entryPosition.getFileName() + "::"
                            + entryPosition.getTimestamp());
                        specificLogFilePosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                            entryPosition.getTimestamp(),
                            endPosition,
                            entryPosition.getFileName());
                    }
                }

                if (specificLogFilePosition == null) {
                    // position不存在，从文件头开始
                    entryPosition = new BinlogPosition(entryPosition.getFileName(),
                        BINLOG_START_OFFEST,
                        entryPosition.getMasterId(),
                        entryPosition.getTimestamp());
                    return entryPosition;
                } else {
                    return specificLogFilePosition;
                }
            }
        }
    }

    @Override
    protected BinlogPosition findPositionWithMasterIdAndTimestamp(MysqlConnection connection,
                                                                  BinlogPosition fixedPosition) {
        if (fixedPosition.getTimestamp() > 0) {
            return fixedPosition;
        }

        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        long startTimestamp = TimeUnit.MILLISECONDS
            .toSeconds(System.currentTimeMillis() + 102L * 365 * 24 * 3600 * 1000); // 当前时间的未来102年
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            fixedPosition,
            fixedPosition.getFileName());
    }

    @Override
    protected BinlogPosition findEndPositionWithMasterIdAndTimestamp(MysqlConnection connection) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        final BinlogPosition endPosition = findEndPosition(mysqlConnection);
        long startTimestamp = System.currentTimeMillis();
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            endPosition,
            endPosition.getFileName());
    }

    // 根据时间查找binlog位置
    @Override
    protected BinlogPosition findByStartTimeStamp(MysqlConnection mysqlConnection, Long startTimestamp) {
        BinlogPosition endPosition = findEndPosition(mysqlConnection);
        BinlogPosition startPosition = findStartPosition(mysqlConnection);
        String maxBinlogFileName = endPosition.getFileName();
        String minBinlogFileName = startPosition.getFileName();
        log.info("show master status to set search end position: " + endPosition);
        log.info("show master status to set search start position: " + startPosition);
        String startSearchBinlogFile = endPosition.getFileName();
        boolean shouldBreak = false;
        while (running && !shouldBreak) {
            try {
                BinlogPosition entryPosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                    startTimestamp,
                    endPosition,
                    startSearchBinlogFile);
                if (entryPosition == null) {
                    if (StringUtils.equalsIgnoreCase(minBinlogFileName, startSearchBinlogFile)) {
                        // 已经找到最早的一个binlog，没必要往前找了
                        shouldBreak = true;
                        log.warn("Didn't find the corresponding binlog files from " + minBinlogFileName + " to "
                            + maxBinlogFileName);
                    } else {
                        // 继续往前找
                        int binlogSeqNum = Integer
                            .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                        if (binlogSeqNum <= 1) {
                            log.warn("Didn't find the corresponding binlog files");
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
                    log.info("found and return:" + endPosition + " in findByStartTimeStamp operation.");
                    return entryPosition;
                }
            } catch (Exception e) {
                log.warn("the binlogfile:" + startSearchBinlogFile
                        + " doesn't exist, to continue to search the next binlogfile , caused by ",
                    e);
                int binlogSeqNum = Integer
                    .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                if (binlogSeqNum <= 1) {
                    log.warn("Didn't find the corresponding binlog files");
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

}
