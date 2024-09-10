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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TimelineEnvConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.dumper.dump.client.DumpClient;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_CLIENT_ASYNC_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_CLIENT_RECEIVE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_EVENT_SPLIT_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_FLOW_CONTROL_WINDOW_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_INJECT_TROUBLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_BUFFER_DIRECT_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_DRY_RUN_ENABLE;

/**
 * Created by ziyang.lb
 **/
public class LogFileCopier {

    private static final Logger logger = LoggerFactory.getLogger(LogFileCopier.class);

    private final LogFileManager logFileManager;
    private final LogDecoder logDecoder;
    private final LogContext logContext;
    private final int writeBufferSize;
    private final int seekBufferSize;
    private final boolean useDirectByteBuffer;
    private final boolean dryRun;
    private final boolean rpcUseAsyncMode;
    private final int asyncQueueSize;
    private final ByteBuffer contactBuffer;
    private final int flowControlWindowSize;
    private final boolean injectTrouble;
    private final StreamMetrics metrics;

    private ExecutorService executor;
    private BinlogFile binlogFile;
    private String leaderHost;
    private int leaderPort;
    private byte lastEventType;
    private ContactContext currentContactContext;
    private EventSplitMode splitMode;
    private long lastInjectTroubleTime;
    private TimelineEnvConfig timelineEnvConfig;
    @Setter
    private ExecutionConfig executionConfig;
    private volatile boolean running;

    public LogFileCopier(LogFileManager logFileManager, int writeBufferSize, int seekBufferSize,
                         ExecutionConfig executionConfig) {
        this.logFileManager = logFileManager;
        this.writeBufferSize = writeBufferSize;
        this.seekBufferSize = seekBufferSize;
        this.executionConfig = executionConfig;
        this.logDecoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        this.logContext = new LogContext();
        this.logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        this.useDirectByteBuffer = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_BUFFER_DIRECT_ENABLE);
        this.dryRun = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_DRY_RUN_ENABLE);
        this.rpcUseAsyncMode = DynamicApplicationConfig.getBoolean(BINLOG_SYNC_CLIENT_ASYNC_ENABLE);
        this.asyncQueueSize = DynamicApplicationConfig.getInt(BINLOG_SYNC_CLIENT_RECEIVE_QUEUE_SIZE);
        this.flowControlWindowSize = calcFlowControlWindowSize();
        this.injectTrouble = DynamicApplicationConfig.getBoolean(BINLOG_SYNC_INJECT_TROUBLE);
        this.lastInjectTroubleTime = System.currentTimeMillis();
        this.contactBuffer = ByteBuffer.allocate(65536);
        this.metrics = StreamMetrics.getStreamMetrics(STREAM_NAME_GLOBAL);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor = Executors.newFixedThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("log-file-copier-%d").build());
        executor.execute(() -> {
            DumpClient dumpClient = null;
            long sleepInterval = 1000L;
            while (running) {
                try {
                    prepare();
                    dumpClient =
                        new DumpClient(leaderHost, leaderPort, rpcUseAsyncMode, asyncQueueSize, flowControlWindowSize);
                    dumpClient.connect();
                    logContext.setLogPosition(new LogPosition(binlogFile.getFileName(), binlogFile.filePointer()));
                    dumpClient
                        .dump(binlogFile.getFileName(), binlogFile.filePointer(), buildSplitMode(), this::consume);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    logger.error("process message event error", t);
                    try {
                        CommonUtils.sleep(sleepInterval);
                    } catch (InterruptedException e) {
                        break;
                    }
                    MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_FILE_SYNC_ERROR,
                        ExceptionUtils.getStackTrace(t));
                    // 重试间隔随重试次数增大，最大重试间隔10S
                    if (sleepInterval < 10 * 1000) {
                        sleepInterval += 1000;
                    }
                } finally {
                    if (dumpClient != null) {
                        dumpClient.disconnect();
                    }
                    if (binlogFile != null) {
                        try {
                            binlogFile.close();
                        } catch (IOException e) {
                            logger.error("binlog file close failed {}", binlogFile.getFileName(), e);
                        }
                    }
                }
            }
        });
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        if (executor != null) {
            try {
                executor.shutdownNow();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    private void prepare() throws IOException {
        clearContactInfo();
        buildTarget();
        buildBinlogFile();
        String lastTso = seekLastTso();
        if (StringUtils.isNotBlank(lastTso)) {
            timelineEnvConfig = new TimelineEnvConfig();
            timelineEnvConfig.initConfigByTso(lastTso);
        }
        metrics.setLatestDelayTimeOnCommit(0);
    }

    private void buildTarget() {
        DumperInfoMapper mapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        RetryTemplate template = RetryTemplate.builder()
            .maxAttempts(120)
            .fixedBackoff(1000)
            .retryOn(RetryableException.class)
            .build();

        try {
            DumperInfo info = template.execute((RetryCallback<DumperInfo, Throwable>) retryContext -> {
                Optional<DumperInfo> dumperInfo = mapper.selectOne(c -> c
                    .where(DumperInfoDynamicSqlSupport.clusterId,
                        SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                    .and(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
                if (!dumperInfo.isPresent()) {
                    throw new RetryableException("dumper leader is not ready");
                }
                return dumperInfo.get();
            }, retryContext -> null);
            leaderHost = info.getIp();
            leaderPort = info.getPort();
            logger.info("dumper leader {}:{} is ready now!", leaderHost, leaderPort);
        } catch (Throwable throwable) {
            logger.warn("{} dumper leader is not ready...", DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
            throw new RuntimeException("dumper leader is not ready...");
        }
    }

    private void clearContactInfo() {
        currentContactContext = null;
        contactBuffer.clear();
    }

    public void consume(byte[] packet, boolean isHeartBeat) throws IOException {
        metrics.setLatestDataReceiveTime(System.currentTimeMillis());

        if (dryRun) {
            metrics.incrementTotalWriteBytes(packet.length);
            return;
        }

        if (splitMode == EventSplitMode.CLIENT && isHeartBeat) {
            processHeartBeat();
            return;
        }

        int offset = 0;
        if (contactBuffer.position() > 0 || currentContactContext != null) {
            if (splitMode == EventSplitMode.SERVER) {
                throw new PolardbxException("event contact is not support when split mode is SERVER, contact buffer " +
                    contactBuffer + " , contact context " + currentContactContext);
            }

            if (currentContactContext != null) {
                int remaining = currentContactContext.eventLength - currentContactContext.currentWriteLength;
                if (remaining > packet.length) {
                    binlogFile.writeData(packet, 0, packet.length);
                    currentContactContext.currentWriteLength = currentContactContext.currentWriteLength + packet.length;
                    offset += packet.length;
                } else {
                    //先check，再write，避免数据不一致
                    binlogFile.checkPosition(currentContactContext.eventPos, binlogFile.writePointer() + remaining);
                    binlogFile.writeData(packet, 0, remaining);
                    offset += remaining;
                    currentContactContext = null;
                }
            } else {
                if (contactBuffer.position() < 19) {
                    int readSize = 19 - contactBuffer.position();
                    contactBuffer.put(packet, 0, readSize);
                    offset += readSize;
                }

                ByteArray array = new ByteArray(contactBuffer.array());
                array.skip(9);
                int eventLength = array.readInteger(4);
                long eventPos = array.readLong(4);
                if (contactBuffer.position() + packet.length - offset >= eventLength) {
                    int readSize1 = contactBuffer.position();
                    int readSize2 = eventLength - readSize1;
                    byte[] bytes = new byte[readSize1 + readSize2];
                    System.arraycopy(contactBuffer.array(), 0, bytes, 0, readSize1);
                    System.arraycopy(packet, offset, bytes, readSize1, readSize2);
                    consumeSinglePacket(bytes, 0, bytes.length);
                    offset += readSize2;
                } else {
                    currentContactContext = new ContactContext();
                    currentContactContext.eventLength = eventLength;
                    currentContactContext.eventPos = eventPos;
                    currentContactContext.currentWriteLength = contactBuffer.position() + packet.length - offset;
                    binlogFile.writeData(contactBuffer.array(), 0, contactBuffer.position());
                    binlogFile.writeData(packet, offset, packet.length - offset);
                    offset = packet.length;
                }
            }
        }

        int eventLength = 0;
        long eventPos = 0;
        while (offset < packet.length) {
            //check event header
            if (packet.length - offset < 19) {
                break;
            }
            //get event size
            ByteArray array = new ByteArray(packet, offset, 19);
            array.skip(9);
            eventLength = array.readInteger(4);
            eventPos = array.readLong(4);
            //check event size
            if (packet.length - offset < eventLength) {
                break;
            }
            //consume
            consumeSinglePacket(packet, offset, eventLength);
            offset += eventLength;
        }

        contactBuffer.clear();
        if (offset < packet.length) {
            checkOffset(offset, packet.length);
            if (packet.length - offset <= contactBuffer.capacity()) {
                contactBuffer.put(packet, offset, packet.length - offset);
            } else {
                currentContactContext = new ContactContext();
                currentContactContext.eventLength = eventLength;
                currentContactContext.eventPos = eventPos;
                currentContactContext.currentWriteLength = packet.length - offset;
                binlogFile.writeData(packet, offset, packet.length - offset);
            }
        }

        logFileManager.setLatestFileCursor(new BinlogCursor(binlogFile.getFileName(), binlogFile.filePointer()));
    }

    private void checkOffset(int offset, int length) {
        if (currentContactContext != null) {
            throw new PolardbxException(String.format("current contact context is not null , but offset is not equal"
                + " to packet length, %s,%s,%s", currentContactContext, offset, length));
        }
        if (splitMode == EventSplitMode.SERVER) {
            throw new PolardbxException("offset must equal to packet length when split mode is SERVER, "
                + "offset " + offset + " ,packet length " + length);
        }
    }

    public void consumeSinglePacket(byte[] data, int offset, int length) throws IOException {
        byte eventType = data[offset + 4];
        if (eventType == LogEvent.ROTATE_EVENT) {
            RotateLogEvent event = (RotateLogEvent) logDecoder.decode(new LogBuffer(data, offset, length), logContext);
            logger.info("Receive a rotate event, file name {}, pos {}",
                Objects.requireNonNull(event).getFilename(), event.getPosition());
            if (!StringUtils.equals(Objects.requireNonNull(event).getFilename(), binlogFile.getFileName())) {
                binlogFile.writeEventForSync(data, offset, length);
                rotate(event.getFilename());
            }
        } else if (eventType == LogEvent.FORMAT_DESCRIPTION_EVENT) {
            logger.info("Receive a format desc event.");
            logDecoder.decode(new LogBuffer(data, offset, length), logContext);
            if (binlogFile.fileSize() == 4) {
                binlogFile.writeEventForSync(data, offset, length);
                binlogFile.flush();
            }
        } else if (eventType == LogEvent.HEARTBEAT_LOG_EVENT) {
            processHeartBeat();
        } else {
            checkDelay(eventType, data, offset);
            binlogFile.writeEventForSync(data, offset, length);
        }

        if (eventType == LogEvent.XID_EVENT) {
            metrics.incrementTotalWriteTxnCount();
        }

        if (injectTrouble && System.currentTimeMillis() - lastInjectTroubleTime > 5 * 60 * 1000) {
            lastInjectTroubleTime = System.currentTimeMillis();
            throw new PolardbxException("inject trouble");
        }

        lastEventType = eventType;
    }

    private void processHeartBeat() throws IOException {
        if (binlogFile.hasBufferedData() && binlogFile.getLastFlushTime() < System.currentTimeMillis() - 5000) {
            binlogFile.flush();
            logFileManager.setLatestFileCursor(new BinlogCursor(binlogFile.getFileName(), binlogFile.filePointer()));
        }
    }

    public void buildBinlogFile() throws IOException {
        CdcFile maxLocalCdcFile = logFileManager.getLocalMaxBinlogFile();
        File maxLocalFile;
        if (maxLocalCdcFile == null) {
            maxLocalFile = findFirstFile();
            logFileManager.createLocalFileHelper(maxLocalFile);
            binlogFile =
                new BinlogFile(maxLocalFile, "rw", writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            binlogFile.writeHeader();
        } else {
            maxLocalFile = maxLocalCdcFile.newFile();
            binlogFile =
                new BinlogFile(maxLocalFile, "rw", writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            if (binlogFile.fileSize() == 0) {
                binlogFile.writeHeader();
            } else {
                BinlogFile.SeekResult seekResult = binlogFile.seekLastTso();
                logger.info("seek result is " + seekResult);

                if (binlogFile.filePointer() == 0) {
                    binlogFile.writeHeader();
                } else if (seekResult.getLastEventType() == LogEvent.ROTATE_EVENT) {
                    String oldFileName = binlogFile.getFileName();
                    File newFile = logFileManager.rotateFile(binlogFile.getFile(), null);
                    binlogFile =
                        new BinlogFile(newFile, "rw", writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
                    binlogFile.writeHeader();
                    logger.info("Last event in file [{}] is a rotate event, will start sync from next file [{}].",
                        oldFileName, newFile.getName());
                }
            }
        }

        binlogFile.tryTruncate();
        logger.info("buildBinlogFile {}#{}", binlogFile.getFileName(), binlogFile.filePointer());
    }

    public String seekLastTso() throws IOException {
        List<String> binlogFileNames = logFileManager.getAllLocalBinlogFileNamesOrdered();
        for (int i = binlogFileNames.size() - 1; i >= 0; i--) {
            String fileName = binlogFileNames.get(i);
            String fullPath =
                BinlogFileUtil.getFullPath(logFileManager.getBinlogRootPath(), logFileManager.getGroupName(),
                    logFileManager.getStreamName());
            BinlogFile binlogFile =
                new BinlogFile(new File(fullPath, fileName), "rw",
                    writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            BinlogFile.SeekResult result = binlogFile.seekLastTso();
            binlogFile.close();
            if (StringUtils.isNotBlank(result.getLastTso())) {
                return result.getLastTso();
            }
        }
        return null;
    }

    private void rotate(String rotateFileName) throws IOException {
        String nextFileName = BinlogFileUtil.getNextBinlogFileName(binlogFile.getFileName());
        if (!StringUtils.equals(nextFileName, rotateFileName)) {
            throw new PolardbxException("invalid rotate file name :" + rotateFileName);
        }

        binlogFile.close();
        File newFile = logFileManager.rotateFile(binlogFile.getFile(), null);
        binlogFile = new BinlogFile(newFile, "rw", writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
        binlogFile.writeHeader();
    }

    private File findFirstFile() {
        DumpClient dumpClient = null;
        try {
            dumpClient = new DumpClient(leaderHost, leaderPort, rpcUseAsyncMode, asyncQueueSize, flowControlWindowSize);
            dumpClient.connect();
            String firstLogFileName;
            do {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }

                firstLogFileName = dumpClient.findMasterFirstLogFile();
            } while (firstLogFileName == null);
            return new File(logFileManager.getFullNameOfLocalBinlogFile(firstLogFileName));
        } finally {
            if (dumpClient != null) {
                dumpClient.disconnect();
            }
        }
    }

    private void checkDelay(byte eventType, byte[] data, int offset) throws IOException {
        if (eventType == LogEvent.XID_EVENT || (eventType == LogEvent.ROWS_QUERY_LOG_EVENT
            && (lastEventType == LogEvent.XID_EVENT || lastEventType == LogEvent.ROWS_QUERY_LOG_EVENT))) {
            long timestamp = 0;
            for (int i = 0; i < 4; ++i) {
                int seed = data[i + offset] & 0xff;
                timestamp |= (seed << (i << 3));
            }

            long delayTime = System.currentTimeMillis() - timestamp * 1000;//Binlog中时间戳的单位是秒，需要转化为毫秒再计算
            metrics.setLatestDelayTimeOnCommit(delayTime);
        }
    }

    private EventSplitMode buildSplitMode() {
        String configValue = DynamicApplicationConfig.getString(BINLOG_SYNC_EVENT_SPLIT_MODE);
        EventSplitMode mode = EventSplitMode.valueOf(configValue);
        if (mode == EventSplitMode.RANDOM) {
            List<EventSplitMode> list = Lists.newArrayList(EventSplitMode.SERVER, EventSplitMode.CLIENT);
            Collections.shuffle(list);
            this.splitMode = list.get(0);
            logger.info("random split mode is " + splitMode);
        } else {
            this.splitMode = mode;
        }

        return splitMode;
    }

    int calcFlowControlWindowSize() {
        int flowControlWindowSize = DynamicApplicationConfig.getInt(BINLOG_SYNC_FLOW_CONTROL_WINDOW_SIZE);
        int reserved = Double.valueOf(executionConfig.getReservedMemMb() * 0.8).intValue();
        return reserved == 0 ? flowControlWindowSize : Math.min(flowControlWindowSize, reserved);
    }

    @ToString
    private static class ContactContext {
        int eventLength;
        int currentWriteLength;
        long eventPos;
    }
}
