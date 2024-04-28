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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.dumper.metrics.DumpClientMetric;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.binlog.util.Timer;
import com.aliyun.polardbx.rpc.cdc.BinlogEvent;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_BACK_PRESSURE_SLEEP_TIME_US;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.MASTER_BINLOG_CHECKSUM;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.MASTER_HEARTBEAT_PERIOD;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_UNDEF;

/**
 * Created by ShuGuang
 */
@Slf4j
public class LogFileReader {

    private final LogFileManager logFileManager;
    private final StreamMetrics metrics;

    public LogFileReader(LogFileManager logFileManager) {
        this.logFileManager = logFileManager;
        this.metrics = StreamMetrics.getStreamMetrics(logFileManager.getStreamName());
    }

    /**
     * show binlog events in `log_file` from `pos` limit [`offset`,] `row_count`
     * log_file: binlog file's name
     * pos: start position to read
     * offset: number of events to skip
     * row_count: number of events to read
     */
    public void showBinlogEvent(CdcFile cdcFile, long position, long offset, long rowCount,
                                ServerCallStreamObserver<BinlogEvent> serverCallStreamObserver) {
        log.info("show binlog events in {} from {} limit {}, {}", cdcFile.getName(), position, offset, rowCount);
        if (logFileManager.getLatestFileCursor() == null) {
            serverCallStreamObserver.onCompleted();
        }
        BinlogEventReader binlogFileReader = null;
        try {
            binlogFileReader = new BinlogEventReader(cdcFile, position, offset, rowCount);
            binlogFileReader.valid();
            binlogFileReader.skipPos();
            binlogFileReader.skipOffset();
            while (true) {
                if (serverCallStreamObserver.isCancelled()) {
                    serverCallStreamObserver.onCompleted();
                    break;
                }
                if (serverCallStreamObserver.isReady()) {
                    if (binlogFileReader.hasNext()) {
                        serverCallStreamObserver.onNext(binlogFileReader.nextBinlogEvent());
                    } else {
                        log.info("show binlog events in {} from {} limit {}, {} complete", cdcFile.getName(),
                            position,
                            offset,
                            rowCount);
                        serverCallStreamObserver.onCompleted();
                        break;
                    }
                } else {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }
        } catch (Throwable th) {
            log.error("show binlog events in {} from {} limit {}, {} fail", cdcFile.getName(), position, offset,
                rowCount,
                th);
            serverCallStreamObserver.onError(
                Status.INVALID_ARGUMENT.withDescription("show binlog events error!").asException());
        } finally {
            if (binlogFileReader != null) {
                binlogFileReader.close();
            }
        }
    }

    public void binlogDump(String fileName, long startPosition, boolean registered, Map<String, String> ext,
                           ServerCallStreamObserver<DumpStream> serverCallStreamObserver) {
        BinlogDumpReader dumpReader = null;
        BinlogDumpDownloader dumpDownloader = null;
        try {
            log.info("binlogDump from {}@{}, register parameter value is {}, ext parameter value is {}",
                fileName, startPosition, registered, ext);

            // ===============  handle binlog dump related user variables ===================
            EnumBinlogChecksumAlg slaveChecksumAlg = BINLOG_CHECKSUM_ALG_UNDEF;
            long masterHeartbeatPeriod = DynamicApplicationConfig.getLong(BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD);
            for (Map.Entry<String, String> entry : ext.entrySet()) {
                switch (entry.getKey()) {
                case MASTER_BINLOG_CHECKSUM:
                    slaveChecksumAlg = EnumBinlogChecksumAlg.fromName(entry.getValue());
                    break;
                case MASTER_HEARTBEAT_PERIOD:
                    masterHeartbeatPeriod = Long.parseLong(entry.getValue());
                    break;
                default:
                    log.warn("unknown binlog dump parameter: {}", entry.getKey());
                }
            }
            // ===============  handle binlog dump related user variables ===================

            long retryInterval = DynamicApplicationConfig.getLong(
                ConfigKeys.BINLOG_DUMP_WAIT_CURSOR_READY_RETRY_INTERVAL_SECOND);
            int retryTimesLimit = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_WAIT_CURSOR_READY_TIMES_LIMIT);
            RetryerBuilder.newBuilder().
                withWaitStrategy(WaitStrategies.fixedWait(retryInterval, TimeUnit.SECONDS)).
                withStopStrategy(StopStrategies.stopAfterAttempt(retryTimesLimit)).
                retryIfResult(Objects::isNull).
                build().
                call(logFileManager::getLatestFileCursor);

            dumpReader = new BinlogDumpReader(logFileManager, fileName, startPosition, getInt(BINLOG_DUMP_PACKET_SIZE),
                getInt(BINLOG_DUMP_READ_BUFFER_SIZE), slaveChecksumAlg);

            DumpClientMetric.startDump();
            if (useDownloadFirstModeForDump(fileName)) {
                Integer windowSize = getInt(ConfigKeys.BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE);
                // 解决多个dump请求并发的问题，防止互相干扰
                String downloadPath = getString(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH) + "/" + UUID.randomUUID();
                dumpDownloader =
                    new BinlogDumpDownloader(logFileManager, downloadPath, windowSize, fileName, masterHeartbeatPeriod,
                        serverCallStreamObserver, dumpReader);
                dumpDownloader.start();
                dumpReader.setDumpMode(BinlogDumpReader.DumpMode.QUICK);
                dumpReader.setBinlogDumpDownloader(dumpDownloader);
                dumpReader.registerRotateObserver(dumpDownloader);
            }

            // send a fake rotate
            ByteString fakeRotatePacket = dumpReader.fakeRotateEventPacket();
            serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(fakeRotatePacket).build());
            DEBUG_INFO("FakeRotateEvent", fakeRotatePacket);

            // get file from local or wait for file download from remote
            dumpReader.init();

            dumpReader.valid();

            // send a format desc event first
            ByteString fakeFormatEventPack = dumpReader.formatDescriptionPacket();
            // ByteString fakeFormatEventPack = dumpReader.fakeFormatEvent();
            serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(fakeFormatEventPack).build());
            DEBUG_INFO("FakeFormatEvent", fakeFormatEventPack);

            dumpReader.start();
            int timeout = 10, noData = 0;
            int checkFileStatusInterval = getInt(BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND);
            Timer checkFileStatusTimer = new Timer(checkFileStatusInterval * 1000L);
            Timer heartbeatTimer = new Timer(masterHeartbeatPeriod / 1000);
            long backPressureSleepTime = getLong(BINLOG_DUMP_BACK_PRESSURE_SLEEP_TIME_US);
            while (true) {
                if (serverCallStreamObserver.isCancelled()) {
                    log.warn("remote close...");
                    break;
                }

                // 必须要check file是否存在，否则如果dump过程中文件被删，hasNext方法会一值返回true
                // 但是nextPack是空的，导致线程在while循环中无法退出
                if (checkFileStatusTimer.isTimeout() && !dumpReader.checkFileStatus()) {
                    log.warn("binlog file {} has been deleted, dump thread will exit.", dumpReader.fileName);
                    LabEventManager.logEvent(LabEventType.DUMPER_DUMP_LOCAL_FILE_IS_DELETED);
                    break;
                }

                if (serverCallStreamObserver.isReady()) {
                    if (dumpReader.hasNext()) {
                        ByteString pack = dumpReader.nextDumpPacks();
                        metrics.incrementTotalDumpBytes(pack.size());
                        if (log.isDebugEnabled()) {
                            DEBUG_INFO("BinlogDump", pack);
                        }

                        serverCallStreamObserver.onNext(
                            DumpStream.newBuilder().setPayload(pack).build());
                        DumpClientMetric.addDumpBytes(pack.size());
                        DumpClientMetric.recordPosition(dumpReader.fileName, dumpReader.lastPosition,
                            dumpReader.timestamp);
                        heartbeatTimer.reset();
                    } else {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                        if (heartbeatTimer.isTimeout()) {
                            ByteString heartbeatEvent = dumpReader.heartbeatEventPacket();
                            DEBUG_INFO("HeartbeatEvent", heartbeatEvent);
                            serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(heartbeatEvent).build());
                        }
                    }
                } else {
                    TimeUnit.MICROSECONDS.sleep(backPressureSleepTime);
                }
            }
        } catch (InterruptedException e) {
            log.info("remote closed.");
        } catch (Throwable th) {
            log.error("BinlogDump fail {},{} {}", fileName, startPosition, th.getMessage(), th);
            //如果是明确error_code的异常信息，可以以json的形式onError出去，否则show slave status可能不显示
            Map map = Maps.newHashMap();
            map.put("error_code", 1236);
            map.put("error_message", "binlog dump error!");
            final String s = JSON.toJSONString(map);
            serverCallStreamObserver.onError(Status.INVALID_ARGUMENT.withDescription(s).asException());
        } finally {
            if (dumpReader != null) {
                dumpReader.close();
            }
            if (dumpDownloader != null) {
                dumpDownloader.close();
            }
        }
    }

    public void binlogSync(String fileName, long position, EventSplitMode eventSplitMode,
                           TxnOutputStream<DumpStream> outputStream) {
        log.info("binlogSync from {}@{}", fileName, position);
        if (logFileManager.getLatestFileCursor() == null) {
            log.info("binlogSync complete because latest file cursor is null.");
            outputStream.onCompleted();
            return;
        }
        BinlogSyncReader binlogSyncReader = null;
        try {
            EnumBinlogChecksumAlg eventChecksumAlg = EnumBinlogChecksumAlg.fromName(
                DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_M_EVENT_CHECKSUM_ALG));
            binlogSyncReader = new BinlogSyncReader(logFileManager, fileName, position, eventSplitMode,
                getInt(BINLOG_SYNC_PACKET_SIZE), getInt(BINLOG_SYNC_READ_BUFFER_SIZE), eventChecksumAlg);
            binlogSyncReader.start();
            int timeout = 100, noData = 0;
            int checkFileStatusInterval = getInt(BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND);
            long lastCheckTime = System.currentTimeMillis();
            while (true) {
                // 必须要check file是否存在，否则如果sync过程中文件被删，hasNext方法会一致返回true
                // 但是nextPack是空的，导致线程在while循环中无法退出
                if (System.currentTimeMillis() - lastCheckTime > checkFileStatusInterval * 1000) {
                    if (!binlogSyncReader.checkFileStatus()) {
                        LabEventManager.logEvent(LabEventType.DUMPER_SYNC_LOCAL_FILE_IS_DELETED);
                        log.warn("binlog file {} has been deleted, sync thread will exit.", binlogSyncReader.fileName);
                        break;
                    } else {
                        lastCheckTime = System.currentTimeMillis();
                    }
                }

                // 增加反压控制判断
                if (outputStream.tryWait()) {
                    if (binlogSyncReader.hasNext()) {
                        ByteString pack = binlogSyncReader.nextSyncPacks();
                        if (log.isDebugEnabled()) {
                            DEBUG_INFO("BinlogSync", pack);
                        }
                        outputStream.onNext(DumpStream.newBuilder().setPayload(pack).build());
                    } else {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                        noData += timeout;
                        if (noData > 2000) {
                            outputStream.onNext(DumpStream.newBuilder()
                                .setPayload(binlogSyncReader.heartbeatEventPacket())
                                .setIsHeartBeat(true)
                                .build());
                            noData = 0;
                        }
                    }
                }
            }
        } catch (Throwable th) {
            log.error("BinlogSync fail {},{} {}", fileName, position, th.getMessage(), th);
            outputStream.onError(Status.fromThrowable(th).asException());
        } finally {
            if (binlogSyncReader != null) {
                binlogSyncReader.close();
            }
        }
    }

    private ByteString disableChecksum(ByteString pack) {
        byte[] data = pack.substring(0, pack.size() - 4).toByteArray();
        ByteArray ba = new ByteArray(data);
        ba.writeLong(data.length - 4, 3);
        ba.skip(11);
        ba.writeLong(data.length - 4 - 1, 4);
        return ByteString.copyFrom(data);
    }

    private void DEBUG_INFO(String type, ByteString pack) {
        if (log.isDebugEnabled()) {
            byte[] data = pack.toByteArray();
            ByteArray ba = new ByteArray(data);
            if (!type.equals("BinlogSync")) {
                ba.skip(5);
            }
            ba.skip(4);
            int eventType = ba.read();
            long serverId = ba.readLong(4);
            long eventSize = ba.readLong(4);
            int endPos = ba.readInteger(4);

            log.debug("{} serverId={} payload {}[{}->{}]", type, serverId,
                LogEvent.getTypeName(eventType), endPos - eventSize, endPos);
        }
    }

    private boolean useDownloadFirstModeForDump(String startFileName) {
        if (!getBoolean(ConfigKeys.BINLOG_DUMP_DOWNLOAD_FIRST_MODE)) {
            return false;
        }

        BinlogOssRecordService service = getObject(BinlogOssRecordService.class);
        Optional<BinlogOssRecord> record =
            service.getRecordByName(logFileManager.getGroupName(), logFileManager.getStreamName(),
                getString(ConfigKeys.CLUSTER_ID), startFileName);
        if (!record.isPresent()) {
            log.info("will not use download first mode because file related record not exist, file:{}", startFileName);
            return false;
        }
        BinlogOssRecord r = record.get();
        if (r.getUploadStatus() != BinlogUploadStatus.SUCCESS.getValue()) {
            log.info("will not use download first mode because file not upload to oss success, file:{}", startFileName);
            return false;
        }
        if (r.getPurgeStatus() == BinlogPurgeStatus.COMPLETE.getValue()) {
            log.info("will not use download first mode because file is purged, file:{}", startFileName);
            return false;
        }
        File f = new File(logFileManager.getBinlogFullPath(), startFileName);
        if (f.exists()) {
            log.info("will not use download first mode because file exists in local, file:{}", startFileName);
            return false;
        }

        return true;
    }

}
