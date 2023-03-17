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
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
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
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_PACKET_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_READ_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

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
                        log.info("show binlog events in {} from {} limit {}, {} complete", cdcFile.getName(), position,
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
            serverCallStreamObserver.onError(Status.INVALID_ARGUMENT.withDescription(th.getMessage()).asException());
        } finally {
            if (binlogFileReader != null) {
                binlogFileReader.close();
            }
        }
    }

    public void binlogDump(String fileName, long position, boolean registered, Map<String, String> ext,
                           ServerCallStreamObserver<DumpStream> serverCallStreamObserver) {
        BinlogDumpReader dumpReader = null;
        try {
            log.info("binlogDump from {}@{}, register parameter value is {}, ext parameter value is {}",
                fileName, position, registered, ext);
            long retryInterval = DynamicApplicationConfig.getLong(
                ConfigKeys.BINLOG_DUMP_WAIT_CURSOR_READY_RETRY_INTERVAL_SECOND);
            int retryTimesLimit = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_WAIT_CURSOR_READY_TIMES_LIMIT);
            RetryerBuilder.newBuilder().
                withWaitStrategy(WaitStrategies.fixedWait(retryInterval, TimeUnit.SECONDS)).
                withStopStrategy(StopStrategies.stopAfterAttempt(retryTimesLimit)).
                retryIfResult(Objects::isNull).
                build().
                call(() -> logFileManager.getLatestFileCursor());

            dumpReader = new BinlogDumpReader(logFileManager, fileName, position, getInt(BINLOG_DUMP_PACKET_SIZE),
                getInt(BINLOG_DUMP_READ_BUFFER_SIZE));
            dumpReader.valid();

            ByteString fakeRotateEvent = dumpReader.fakeRotateEvent();
            if (StringUtils.equalsIgnoreCase(ext.get("master_binlog_checksum"), "NONE") && StringUtils.equalsIgnoreCase(
                ext.get("source_binlog_checksum"), "NONE")) {
                fakeRotateEvent = disableChecksum(fakeRotateEvent);
                dumpReader.setRotateNext(false);
            }
            ByteString fakeFormatEvent = dumpReader.fakeFormatEvent();
            serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(fakeRotateEvent).build());
            show("FakeRotateEvent", fakeRotateEvent);
            serverCallStreamObserver.onNext(DumpStream.newBuilder().setPayload(fakeFormatEvent).build());
            show("FakeFormatEvent", fakeFormatEvent);
            dumpReader.start();
            int timeout = 10, noData = 0;
            while (true) {
                if (serverCallStreamObserver.isCancelled()) {
                    log.warn("remote close...");
                    break;
                }
                if (serverCallStreamObserver.isReady()) {
                    if (dumpReader.hasNext()) {
                        ByteString pack = dumpReader.nextDumpPacks();
                        metrics.incrementTotalDumpBytes(pack.size());
                        if (log.isDebugEnabled()) {
                            show("BinlogDump", pack);
                        }
                        serverCallStreamObserver.onNext(
                            DumpStream.newBuilder().setPayload(pack).build());
                    } else {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                        noData += timeout;
                        //默认30s一次心跳(mysql 默认 SELECT Heartbeat FROM MYSQL.SLAVE_MASTER_INFO)
                        //减少到1s
                        int interval = DynamicApplicationConfig.getInt(BINLOG_DUMP_HEARTBEAT_INTERVAL_MS);
                        if (noData > interval) {
                            ByteString heartbeatEvent = dumpReader.heartbeatEvent();
                            show("HeartbeatEvent", heartbeatEvent);
                            serverCallStreamObserver.onNext(
                                DumpStream.newBuilder()
                                    .setPayload(heartbeatEvent)
                                    .build());
                            noData = 0;
                        }
                    }
                } else {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }
        } catch (Throwable th) {
            log.error("BinlogDump fail {},{} {}", fileName, position, th.getMessage(), th);
            //如果是明确error_code的异常信息，可以以json的形式onError出去，否则show slave status可能不显示
            Map map = Maps.newHashMap();
            map.put("error_code", 1236);
            map.put("error_message", th.getMessage());
            final String s = JSON.toJSONString(map);
            serverCallStreamObserver.onError(Status.INVALID_ARGUMENT.withDescription(s).asException());
        } finally {
            if (dumpReader != null) {
                dumpReader.close();
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
            binlogSyncReader = new BinlogSyncReader(logFileManager, fileName, position, eventSplitMode,
                getInt(BINLOG_SYNC_PACKET_SIZE), getInt(BINLOG_SYNC_READ_BUFFER_SIZE));
            binlogSyncReader.start();
            int timeout = 100, noData = 0;
            while (true) {
                // 增加反压控制判断
                if (outputStream.tryWait()) {
                    if (binlogSyncReader.hasNext()) {
                        ByteString pack = binlogSyncReader.nextSyncPacks();
                        if (log.isDebugEnabled()) {
                            show("BinlogSync", pack);
                        }
                        outputStream.onNext(DumpStream.newBuilder().setPayload(pack).build());
                    } else {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                        noData += timeout;
                        if (noData > 2000) {
                            outputStream.onNext(DumpStream.newBuilder()
                                .setPayload(binlogSyncReader.heartbeatEvent())
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

    private void show(String type, ByteString pack) {
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
        if (log.isDebugEnabled()) {
            log.debug("{} serverId={} payload {}[{}->{}]", type, serverId,
                LogEvent.getTypeName(eventType), endPos - eventSize, endPos);
        }
    }
}
