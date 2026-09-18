/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumProtocolType;
import com.aliyun.polardbx.binlog.dumper.metrics.DumpClientMetric;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.rpc.DumperRpcClient;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_BACK_PRESSURE_SLEEP_TIME_US;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_CHECK_DELAY_MAX_TIMEOUT_TIMES;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DELAY_THRESHOLD_MILLISECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_PART_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.DumperConfigKeys.CLIENT_TRACE_MARK;
import static com.aliyun.polardbx.binlog.DumperConfigKeys.PARALLELISM;
import static com.aliyun.polardbx.binlog.DumperConfigKeys.PARALLELISM_ID;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.ARCHIVE_IGNORE;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.CLIENT_TYPE;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.IGNORE_BY_FLAG;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.IGNORE_SERVER_IDS;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.INST_ID;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.SERVER_ID_FILTER_DDL;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.MASTER_BINLOG_CHECKSUM;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.MASTER_HEARTBEAT_PERIOD;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.PROCESS_ID;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.ROWS_QUERY_IGNORE;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.TABLE_ALLOW;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.TABLE_IGNORE;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.TRACE_ID;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.DumpUserVariableName.USER;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ShuGuang
 */
@Slf4j
public class LogFileReader {

    private final LogFileManager logFileManager;
    private final StreamMetrics metrics;
    private final boolean masterOrDumperX;
    private String dumperMasterIp;
    private int dumperMasterPort;

    public LogFileReader(LogFileManager logFileManager) {
        this.logFileManager = logFileManager;
        this.metrics = StreamMetrics.getStreamMetrics(logFileManager.getStreamName());
        this.masterOrDumperX =
            RuntimeLeaderElector.isDumperMasterOrX(logFileManager.getExecutionConfig().getRuntimeVersion(),
                logFileManager.getTaskType(), logFileManager.getTaskName());
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
                        List<BinlogEvent> binlogEvents = binlogFileReader.nextBinlogEvent();
                        binlogEvents.forEach(serverCallStreamObserver::onNext);
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

    @SneakyThrows
    public void binlogDump(String fileName, long startPosition, boolean registered, Map<String, String> ext,
                           DumpClientMetric dumpClientMetric,
                           ServerCallStreamObserver<DumpStream> serverCallStreamObserver) {
        BinlogDumpReader dumpReader = null;
        BinlogDumpDownloader dumpDownloader = null;
        try {
            log.info("binlogDump from {}@{}, register parameter value is {}, ext parameter value is {}",
                fileName, startPosition, registered, ext);

            // ===============  handle binlog dump related user variables ===================
            BinlogDumpUserVariables userVars = new BinlogDumpUserVariables();
            int windowSize = getInt(BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE);
            int parallelismPerFile = getInt(BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE);
            long partSize = getLong(BINLOG_DUMP_DOWNLOAD_PART_SIZE);
            for (Map.Entry<String, String> entry : ext.entrySet()) {
                switch (entry.getKey()) {
                case MASTER_BINLOG_CHECKSUM:
                    userVars.slaveChecksumAlg = EnumBinlogChecksumAlg.fromName(entry.getValue());
                    break;
                case MASTER_HEARTBEAT_PERIOD:
                    userVars.masterHeartbeatPeriod = Long.parseLong(entry.getValue());
                    break;
                case CLIENT_TYPE:
                    userVars.clientType = EnumClientType.valueOf(entry.getValue());
                    break;
                case TRACE_ID:
                    userVars.traceId = entry.getValue();
                    break;
                case PROCESS_ID:
                    userVars.processId = Long.parseLong(entry.getValue());
                    break;
                case TABLE_IGNORE:
                    userVars.initTableIgnore(entry.getValue(), "");
                    break;
                case TABLE_ALLOW:
                    userVars.initTableIgnore("", entry.getValue());
                    break;
                case ARCHIVE_IGNORE:
                    userVars.archiveIgnoreEnabled = Boolean.parseBoolean(entry.getValue());
                    break;
                case ROWS_QUERY_IGNORE:
                    userVars.rowsQueryIgnoreEnabled = Boolean.parseBoolean(entry.getValue());
                    break;
                case IGNORE_SERVER_IDS:
                    userVars.ignoreServerIds = entry.getValue();
                    break;
                case SERVER_ID_FILTER_DDL:
                    userVars.serverIdFilterDdlEnabled = Boolean.parseBoolean(entry.getValue());
                    break;
                case USER:
                    userVars.user = entry.getValue();
                    break;
                case IGNORE_BY_FLAG:
                    userVars.ignoreBySetFlag = Boolean.parseBoolean(entry.getValue());
                    break;
                case INST_ID:
                    userVars.instId = entry.getValue();
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

            String trace = UUID.randomUUID().toString();
            dumpReader = new BinlogDumpReader(logFileManager, fileName, startPosition, getInt(BINLOG_DUMP_PACKET_SIZE),
                getInt(BINLOG_DUMP_READ_BUFFER_SIZE), userVars.slaveChecksumAlg, trace);
            dumpReader.setMetric(dumpClientMetric);
            logFileManager.getLogFileLockManager().readLock(fileName);

            // 列存单独配置过滤参数
            if (userVars.clientType == EnumClientType.COLUMNAR) {
                userVars.initVariablesForColumnar();
            }

            // CDC版本高于CN，没有user参数，不支持rows query过滤
            if (StringUtils.isEmpty(userVars.user)) {
                userVars.rowsQueryIgnoreEnabled = false;
            }

            BinlogDumpFilter binlogDumpFilter = new BinlogDumpFilter(userVars);
            dumpReader.setBinlogDumpFilter(binlogDumpFilter);

            DumpClientMetric.startDump(userVars.instId, userVars.clientType, EnumProtocolType.DUMP, userVars.processId,
                userVars.traceId, userVars.user, binlogDumpFilter.getFilterInfo(), dumpClientMetric);
            String downloadStartFileName = BinlogFileUtil.getNextBinlogFileName(fileName);
            if (getBoolean(ConfigKeys.BINLOG_DUMP_DOWNLOAD_FIRST_MODE)) {
                log.info("use download first mode for dump ...");
                // 解决多个dump请求并发的问题，防止互相干扰
                String downloadPath = getString(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH) + "/" + trace;
                // 初始化dumpDownloader，在rotate到一个被清理掉的文件时会发生作用
                dumpDownloader = new BinlogDumpDownloader(logFileManager, downloadPath, windowSize,
                    downloadStartFileName, userVars.masterHeartbeatPeriod, serverCallStreamObserver, dumpReader,
                    parallelismPerFile, partSize, trace);
                dumpReader.setBinlogDumpDownloader(dumpDownloader);
                dumpReader.registerRotateObserver(dumpDownloader);
                dumpDownloader.init();
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
            int checkDelayInterval = getInt(ConfigKeys.BINLOG_DUMP_CHECK_DELAY_INTERVAL_SECOND);
            Timer checkFileStatusTimer = new Timer(checkFileStatusInterval * 1000L);
            Timer heartbeatTimer = new Timer(userVars.masterHeartbeatPeriod / 1000000);
            Timer checkDelayTimer = new Timer(checkDelayInterval * 1000L);
            long backPressureSleepTime = getLong(BINLOG_DUMP_BACK_PRESSURE_SLEEP_TIME_US);
            int checkDelayTimeOutCount = 0;
            int checkDelayTimeOutMaxCount = getInt(BINLOG_DUMP_CHECK_DELAY_MAX_TIMEOUT_TIMES);
            boolean isLabEnv = getBoolean(ConfigKeys.IS_LAB_ENV);
            boolean proactiveDisconnect = getBoolean(ConfigKeys.BINLOG_DUMP_PROACTIVE_DISCONNECT_ENABLED);
            long maxAcceptDelay = getLong(BINLOG_DUMP_DELAY_THRESHOLD_MILLISECOND);
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("binlog dump thread is interrupted, with traceId " + trace);
                }

                if (serverCallStreamObserver.isCancelled()) {
                    log.warn("remote close by cancel...");
                    break;
                }

                if (!masterOrDumperX && checkDelayTimer.isTimeout()) {
                    // 周期性检查从节点延迟是否过大，过大主动断开与下游连接
                    if (isLabEnv || proactiveDisconnect) {
                        if (!checkDumperSlaveDelay(maxAcceptDelay)) {
                            if (++checkDelayTimeOutCount >= checkDelayTimeOutMaxCount) {
                                throw new RuntimeException(
                                    "dumper slave delay timeout " + checkDelayTimeOutMaxCount + "times");
                            }
                        } else {
                            checkDelayTimeOutCount = 0;
                        }
                    }
                }

                // 必须要check file是否存在，否则如果dump过程中文件被删，hasNext方法会一值返回true
                // 但是nextPack是空的，导致线程在while循环中无法退出
                if (checkFileStatusTimer.isTimeout() && !dumpReader.checkFileStatus()) {
                    log.warn("binlog file {} has been deleted, dump thread will exit.", dumpReader.fileName);
                    LabEventManager.logEvent(LabEventType.DUMPER_DUMP_LOCAL_FILE_IS_DELETED);
                    throw new NoSuchFieldException("file has been deleted");
                }

                if (serverCallStreamObserver.isReady()) {
                    if (dumpReader.hasNext()) {
                        ByteString pack = dumpReader.nextDumpPacks(serverCallStreamObserver);
                        if (log.isDebugEnabled()) {
                            DEBUG_INFO("BinlogDump", pack);
                        }
                        DumpClientMetric.recordPosition(dumpReader.fileName, dumpReader.lastPosition,
                            dumpReader.timestamp, dumpClientMetric);
                        if (pack.isEmpty()) {
                            handleNoMoreData(timeout, heartbeatTimer, dumpReader, serverCallStreamObserver);
                        } else {
                            metrics.incrementTotalDumpBytes(pack.size());
                            serverCallStreamObserver.onNext(
                                DumpStream.newBuilder().setPayload(pack).build());
                            DumpClientMetric.addDumpBytes(pack.size(), dumpClientMetric);
                            heartbeatTimer.reset();
                        }
                    } else {
                        handleNoMoreData(timeout, heartbeatTimer, dumpReader, serverCallStreamObserver);
                    }
                } else {
                    TimeUnit.MICROSECONDS.sleep(backPressureSleepTime);
                }
            }
        } catch (InterruptedException e) {
            log.error("remote closed by interrupted.");
        } catch (Throwable th) {
            log.error("BinlogDump fail {},{} {}", fileName, startPosition, th.getMessage(), th);
            //如果是明确error_code的异常信息，可以以json的形式onError出去，否则show slave status可能不显示
            Map<String, Object> map = Maps.newHashMap();
            map.put("error_code", 1236);
            map.put("error_message", "binlog dump error!");
            final String s = JSON.toJSONString(map);
            serverCallStreamObserver.onError(Status.INVALID_ARGUMENT.withDescription(s).asException());
            throw th;
        } finally {
            if (dumpReader != null) {
                dumpReader.close();
                if (dumpReader.getDumpDownloader() != null) {
                    dumpReader.getDumpDownloader().close();
                }
            }
            if (dumpDownloader != null) {
                dumpDownloader.close();
            }
        }
    }

    public void binlogSync(String fileName, long position, EventSplitMode eventSplitMode,
                           DumpClientMetric dumpClientMetric, Map<String, String> ext,
                           TxnOutputStream<DumpStream> outputStream) {
        log.info("binlogSync from {}@{}", fileName, position);
        if (logFileManager.getLatestFileCursor() == null) {
            log.info("binlogSync complete because latest file cursor is null.");
            outputStream.onCompleted();
            return;
        }

        EnumClientType clientType = EnumClientType.SLAVE;
        int parallelism = 1;
        int parallelismId = 0;
        int windowSize = getInt(BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE);
        int parallelismPerFile = getInt(BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE);
        long partSize = getLong(BINLOG_DUMP_DOWNLOAD_PART_SIZE);
        String clientTraceMark = UUID.randomUUID().toString();
        for (Map.Entry<String, String> entry : ext.entrySet()) {
            switch (entry.getKey()) {
            case CLIENT_TYPE:
                clientType = EnumClientType.valueOf(entry.getValue());
                break;
            case PARALLELISM:
                parallelism = Integer.parseInt(entry.getValue());
                break;
            case PARALLELISM_ID:
                parallelismId = Integer.parseInt(entry.getValue());
                break;
            case CLIENT_TRACE_MARK:
                clientTraceMark = entry.getValue();
                break;
            case BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE:
                windowSize = Integer.parseInt(entry.getValue());
                break;
            case BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE:
                parallelismPerFile = Integer.parseInt(entry.getValue());
                break;
            case BINLOG_DUMP_DOWNLOAD_PART_SIZE:
                partSize = Long.parseLong(entry.getValue());
                break;
            default:
                log.warn("unknown binlog dump parameter: {}", entry.getKey());
            }
        }

        BinlogSyncReader binlogSyncReader = null;
        BinlogDumpDownloader binlogDumpDownloader = null;
        try {
            logFileManager.getLogFileLockManager().readLock(fileName);
            DumpClientMetric.startDump("", clientType, EnumProtocolType.SYNC, 0, "", "", "", dumpClientMetric);

            // 解决多个dump请求并发的问题，防止互相干扰
            String downloadPath = getString(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH) + "/" + clientTraceMark;
            EnumBinlogChecksumAlg eventChecksumAlg = EnumBinlogChecksumAlg.fromName(
                DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_M_EVENT_CHECKSUM_ALG));
            if (parallelism > 1) {
                log.info("binlogSync start, parallelism: {}, filename: {}, parallelism id: {}", parallelism, fileName,
                    parallelismId);
                binlogSyncReader = new BinlogParallelSyncReader(logFileManager, fileName, position, eventSplitMode,
                    getInt(BINLOG_SYNC_PACKET_SIZE), getInt(BINLOG_SYNC_READ_BUFFER_SIZE), eventChecksumAlg,
                    parallelism, parallelismId, clientTraceMark, outputStream);
                binlogDumpDownloader =
                    new BinlogParallelSyncDownloader(logFileManager, downloadPath, windowSize, fileName,
                        DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD),
                        outputStream.getObserver(), binlogSyncReader, parallelismPerFile, partSize, parallelism,
                        clientTraceMark);
                binlogSyncReader.setBinlogDumpDownloader(binlogDumpDownloader);
                binlogSyncReader.registerRotateObserver(binlogDumpDownloader);
                binlogDumpDownloader.init();
            } else {
                log.info("binlogSync start, parallelism: {}, filename: {}", parallelism, fileName);
                binlogSyncReader = new BinlogSyncReader(logFileManager, fileName, position, eventSplitMode,
                    getInt(BINLOG_SYNC_PACKET_SIZE), getInt(BINLOG_SYNC_READ_BUFFER_SIZE), eventChecksumAlg,
                    clientTraceMark);
                String downloadStartFileName = BinlogFileUtil.getNextBinlogFileName(fileName);
                if (getBoolean(ConfigKeys.BINLOG_DUMP_DOWNLOAD_FIRST_MODE)) {
                    binlogDumpDownloader =
                        new BinlogDumpDownloader(logFileManager, downloadPath, windowSize, downloadStartFileName,
                            DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD),
                            outputStream.getObserver(), binlogSyncReader, parallelismPerFile, partSize,
                            clientTraceMark);
                    binlogSyncReader.setBinlogDumpDownloader(binlogDumpDownloader);
                    binlogSyncReader.registerRotateObserver(binlogDumpDownloader);
                    binlogDumpDownloader.init();
                }
            }
            binlogSyncReader.start();
            int timeout = 100, noData = 0;
            int checkFileStatusInterval = getInt(BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND);
            long lastCheckTime = System.nanoTime();
            while (true) {
                // 必须要check file是否存在，否则如果sync过程中文件被删，hasNext方法会一致返回true
                // 但是nextPack是空的，导致线程在while循环中无法退出
                if (System.nanoTime() - lastCheckTime > checkFileStatusInterval * 1_000_000L) {
                    if (!binlogSyncReader.checkFileStatus()) {
                        LabEventManager.logEvent(LabEventType.DUMPER_SYNC_LOCAL_FILE_IS_DELETED);
                        log.warn("binlog file {} has been deleted, sync thread will exit.", binlogSyncReader.fileName);
                        throw new NoSuchFieldException("file has been deleted");
                    } else {
                        lastCheckTime = System.nanoTime();
                    }
                }

                // 增加反压控制判断
                if (outputStream.tryWait()) {
                    if (binlogSyncReader.hasNext()) {
                        ByteString pack = binlogSyncReader.nextSyncPacks();
                        metrics.incrementTotalSyncBytes(pack.size());
                        if (log.isDebugEnabled()) {
                            DEBUG_INFO("BinlogSync", pack);
                        }
                        outputStream.onNext(DumpStream.newBuilder().setPayload(pack).build());
                        DumpClientMetric.addDumpBytes(pack.size(), dumpClientMetric);
                        DumpClientMetric.recordPosition(binlogSyncReader.fileName, binlogSyncReader.lastPosition,
                            -1, dumpClientMetric);
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
                if (binlogSyncReader.getDumpDownloader() != null) {
                    binlogSyncReader.getDumpDownloader().close();
                }
            }
            if (binlogDumpDownloader != null) {
                binlogDumpDownloader.close();
            }
        }
    }

    protected ByteString disableChecksum(ByteString pack) {
        byte[] data = pack.substring(0, pack.size() - 4).toByteArray();
        ByteArray ba = new ByteArray(data);
        ba.writeLong(data.length - 4, 3);
        ba.skip(11);
        ba.writeLong(data.length - 4 - 1, 4);
        return ByteString.copyFrom(data);
    }

    protected void DEBUG_INFO(String type, ByteString pack) {
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

    /**
     * 无用方法
     *
     * @return boolean
     */
    protected boolean useDownloadFirstModeForDump(String startFileName) {
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

    /**
     * @return if timeout return false else true
     */
    protected boolean checkDumperSlaveDelay(long maxAcceptDelay) {
        getDumperMasterAddress();
        long slaveLastEventTimeStamp = logFileManager.getLastEventTimestamp();
        DumperRpcClient dumperRpcClient =
            new DumperRpcClient(dumperMasterIp, dumperMasterPort);
        long masterLastEventTimeStamp = Long.MAX_VALUE;
        try {
            dumperRpcClient.connect();
            masterLastEventTimeStamp =
                dumperRpcClient.getDumperInfo(STREAM_NAME_GLOBAL).getRight().getLastEventTimestamp();
        } catch (Exception e) {
            log.error("get master lastEventTimestamp failed", e);
        } finally {
            dumperRpcClient.disconnect();
        }
        long delayMs = (masterLastEventTimeStamp - slaveLastEventTimeStamp) * 1000L;
        return delayMs < maxAcceptDelay;
    }

    protected void getDumperMasterAddress() {
        if (dumperMasterIp == null) {
            DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
            Optional<DumperInfo> dumperMasterInfo =
                dumperInfoMapper.selectOne(s -> s.where(DumperInfoDynamicSqlSupport.role, isEqualTo("M"))
                    .and(DumperInfoDynamicSqlSupport.status, isEqualTo(0))
                    .and(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(getString(ConfigKeys.CLUSTER_ID))));
            if (dumperMasterInfo.isPresent()) {
                dumperMasterIp = dumperMasterInfo.get().getIp();
                dumperMasterPort = dumperMasterInfo.get().getPort();
            } else {
                throw new RuntimeException("No dumper master in metaDB!");
            }
        }
    }

    /**
     * Send heartbeat event to the client when timeout occurs
     *
     * @param dumpReader the binlog dump reader
     * @param serverCallStreamObserver the stream observer to send event
     * @throws Exception if any error occurs when generating heartbeat event
     */
    private void sendHeartbeatEvent(BinlogDumpReader dumpReader,
                                    ServerCallStreamObserver<DumpStream> serverCallStreamObserver) throws Exception {
        ByteString heartbeatEvent = dumpReader.heartbeatEventPacket();
        DEBUG_INFO("HeartbeatEvent", heartbeatEvent);
        serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(heartbeatEvent).build());
    }

    public void handleNoMoreData(int timeout, Timer heartbeatTimer, BinlogDumpReader dumpReader,
                                 ServerCallStreamObserver<DumpStream> serverCallStreamObserver) throws Exception {
        TimeUnit.MILLISECONDS.sleep(timeout);
        if (heartbeatTimer.isTimeout()) {
            sendHeartbeatEvent(dumpReader, serverCallStreamObserver);
        }
    }
}