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

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.DynamicApplicationVersionConfig;
import com.aliyun.polardbx.binlog.MetaCoopCommandEnum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.dumper.dump.client.DumpClientV2;
import com.aliyun.polardbx.binlog.dumper.dump.util.ByteArray;
import com.aliyun.polardbx.binlog.dumper.metrics.Metrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private ExecutorService executor;
    private BinlogFile binlogFile;
    private String leaderHost;
    private int leaderPort;
    private byte lastEventType;
    private volatile boolean running;

    public LogFileCopier(LogFileManager logFileManager, int writeBufferSize, int seekBufferSize) {
        this.logFileManager = logFileManager;
        this.writeBufferSize = writeBufferSize;
        this.seekBufferSize = seekBufferSize;
        this.logDecoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        this.logContext = new LogContext();
        this.logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor = Executors.newFixedThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("log-file-copier-%d").build());
        executor.execute(() -> {
            DumpClientV2 dumpClient = null;
            long sleepInterval = 1000L;
            while (running) {
                try {
                    prepare();
                    dumpClient = new DumpClientV2(leaderHost, leaderPort);
                    dumpClient.connect();
                    logContext.setLogPosition(new LogPosition(binlogFile.getFileName(), binlogFile.position()));
                    dumpClient.dump(binlogFile.getFileName(), binlogFile.position(), this::consume);
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
        buildTarget();
        buildBinlogFile();
        String lastTso = seekLastTso();
        if (StringUtils.isNotBlank(lastTso)) {
            DynamicApplicationVersionConfig.applyConfigByTso(lastTso);
        }
        Metrics.get().setLatestDelayTimeOnCommit(0);
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

    public void consume(byte[] packet) throws IOException {
        Metrics.get().setLatestDataReceiveTime(System.currentTimeMillis());

        int offset = 0;
        while (offset < packet.length) {
            ByteArray array = new ByteArray(packet, offset, 19);
            array.skip(9);
            int size = array.readInteger(4);
            consumeSinglePacket(packet, offset, size);
            offset += size;
        }
        logFileManager.setLatestFileCursor(new Cursor(binlogFile.getFileName(), binlogFile.position()));
    }

    public void consumeSinglePacket(byte[] data, int offset, int length) throws IOException {
        byte eventType = data[offset + 4];
        if (eventType == LogEvent.ROTATE_EVENT) {
            logger.info("Receive a rotate event.");
            RotateLogEvent event = (RotateLogEvent) logDecoder.decode(new LogBuffer(data, offset, length), logContext);
            if (!StringUtils.equals(Objects.requireNonNull(event).getFilename(), binlogFile.getFileName())) {
                binlogFile.writeEventWithoutUpdate(data, offset, length);
                rotate(event.getFilename());
            }
        } else if (eventType == LogEvent.FORMAT_DESCRIPTION_EVENT) {
            logger.info("Receive a format desc event.");
            logDecoder.decode(new LogBuffer(data, offset, length), logContext);
            if (binlogFile.length() == 4) {
                binlogFile.writeEventWithoutUpdate(data, offset, length);
            }
        } else if (eventType == LogEvent.HEARTBEAT_LOG_EVENT) {
            if (binlogFile.hasBufferedData() && binlogFile.getLastFlushTime() < System.currentTimeMillis() - 5000) {
                binlogFile.flush();
            }
        } else {
            checkDelay(eventType, data, offset);
            binlogFile.writeEventWithoutUpdate(data, offset, length);
        }

        if (eventType == LogEvent.XID_EVENT) {
            Metrics.get().incrementTotalWriteTxnCount();
        }

        if (eventType == LogEvent.ROWS_QUERY_LOG_EVENT) {
            RowsQueryLogEvent rowsQueryLogEvent =
                (RowsQueryLogEvent) logDecoder.decode(new LogBuffer(data, offset, length), logContext);
            MarkInfo markInfo = CommonUtils.getCommand(rowsQueryLogEvent.getRowsQuery());
            if (markInfo.isValid() && markInfo.getCommand() == MetaCoopCommandEnum.ConfigChange) {
                DynamicApplicationVersionConfig.setConfigByTso(markInfo.getTso());
            }
        }

        lastEventType = eventType;
    }

    public void buildBinlogFile() throws IOException {
        File maxFile = logFileManager.getMaxBinlogFile();
        logger.info("maxFile {}", maxFile);
        if (maxFile == null) {
            maxFile = findFirstFile();
            logFileManager.createFile(maxFile);
            binlogFile = new BinlogFile(maxFile, "rw", writeBufferSize, seekBufferSize);
            binlogFile.writeHeader();
        } else {
            binlogFile = new BinlogFile(maxFile, "rw", writeBufferSize, seekBufferSize);
            if (binlogFile.length() == 0) {
                binlogFile.writeHeader();
            } else {
                BinlogFile.SeekResult seekResult = binlogFile.seekLastTso();
                if (seekResult.getLastEventType() == LogEvent.ROTATE_EVENT) {
                    String oldFileName = binlogFile.getFileName();
                    File newFile = logFileManager.rotateFile(binlogFile.getFile());
                    binlogFile = new BinlogFile(newFile, "rw", writeBufferSize, seekBufferSize);
                    binlogFile.writeHeader();
                    logger.info("Last event in file [{}] is a rotate event, will start sync from next file [{}].",
                        oldFileName, newFile.getName());
                }
                binlogFile.seekLast();
            }
        }
        logger.info("buildBinlogFile {}#{}", binlogFile.getFileName(), binlogFile.position());
    }

    public String seekLastTso() throws IOException {
        List<String> binlogFileNames = logFileManager.getAllLogFileNamesOrdered();
        for (int i = binlogFileNames.size() - 1; i >= 0; i--) {
            String fileName = binlogFileNames.get(i);
            BinlogFile binlogFile =
                new BinlogFile(new File(logFileManager.getBinlogFileDirPath() + File.separator + fileName), "r",
                    writeBufferSize, seekBufferSize);
            BinlogFile.SeekResult result = binlogFile.seekLastTso();
            binlogFile.close();
            if (StringUtils.isNotBlank(result.getLastTso())) {
                return result.getLastTso();
            }
        }
        return null;
    }

    private void rotate(String rotateFileName) throws IOException {
        String nextFileName = logFileManager.nextBinlogFileName(binlogFile.getFileName());
        if (!StringUtils.equals(nextFileName, rotateFileName)) {
            throw new PolardbxException("invalid rotate file name :" + rotateFileName);
        }

        binlogFile.close();
        File newFile = logFileManager.rotateFile(binlogFile.getFile());
        binlogFile = new BinlogFile(newFile, "rw", writeBufferSize, seekBufferSize);
        binlogFile.writeHeader();
    }

    private File findFirstFile() {
        DumpClientV2 dumpClient = null;
        try {
            dumpClient = new DumpClientV2(leaderHost, leaderPort);
            dumpClient.connect();
            File file = new File(logFileManager.getFullName(dumpClient.findMasterFirstLogFile()));
            return file;
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
            Metrics.get().setLatestDelayTimeOnCommit(delayTime);
        }
    }
}
