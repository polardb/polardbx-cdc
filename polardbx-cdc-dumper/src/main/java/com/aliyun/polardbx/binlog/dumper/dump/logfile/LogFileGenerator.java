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

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.DynamicApplicationVersionConfig;
import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.MetaCoopCommandEnum;
import com.aliyun.polardbx.binlog.ServerConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.gtid.ByteHelper;
import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.ParallelWriter;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken;
import com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.event.source.LatestFileCursorChangeEvent;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_RECOVERY_START_TSO_OVERWRITE_CONFIG;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_CLIENT_USE_ASYNC_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_ROWS_QUERY;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_TSO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_DRYRUN_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_PARALLEL_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_TABLE_ID_BASE_VALUE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_USE_PARALLEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_FILE_SEEK_BUFFER_MAX_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_TXN_STREAM_FLOW_CONTROL_WINDOW_MAX_SIZE;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.BEGIN;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.COMMIT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.DML;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.HEARTBEAT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.ROWSQUERY;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TSO;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.isBinlogXStream;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.isMetaScaleTso;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.recordStorageHistory;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.waitTaskConfigReady;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.waitUploadComplete;
import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.containsTableId;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.BEGIN_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.COMMIT_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.ROWS_QUERY_FIXED_LENGTH;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeBegin;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeCommit;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeMarkEvent;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.makeRowsQuery;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 */
@SuppressWarnings("rawtypes")
public class LogFileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(LogFileGenerator.class);
    private static final String MODE = "rw";
    private static final Gson GSON = new GsonBuilder().create();
    private static final AtomicLong XID_SEQ = new AtomicLong(0L);

    // 缓存formatDesc数据，binlog文件滚动需要
    private final LogFileManager logFileManager;
    private final Integer binlogFileSize;
    private final boolean dryRun;
    private final int dryRunMode;
    private final FlushPolicy initFlushPolicy;
    private final int flushInterval;
    private final int writeBufferSize;
    private final int seekBufferSize;
    private final boolean rpcUseAsyncMode;
    private final int rpcReceiveQueueSize;
    private final boolean supportWriteRowQueryLogEvent;
    private final String taskName;
    private final TaskType taskType;
    private final String groupName;
    private final String streamName;
    private final ExecutionConfig executionConfig;
    private final boolean checkRowsQuery;
    private final boolean checkTso;
    private final boolean useDirectByteBuffer;
    private final int flowControlWindowSize;
    private final StreamMetrics metrics;

    //并行写入
    private final boolean useParallelWrite;
    private long nextWritePosition = 0;
    private ParallelWriter parallelWriter;

    private byte[] formatDescData;
    private ExecutorService executor;
    private String startTso;
    private TxnMergedToken currentToken;
    private long currentTsoTimeSecond;//为了性能优化，将物理时间保存到该实例变量，避免每次实时解析
    private long currentTsoTimeMillSecond;
    private Long currentServerId;
    private volatile BinlogFile binlogFile;
    private volatile FlushPolicy currentFlushPolicy;
    private TableIdManager tableIdManager;
    private BinlogFile.SeekResult latestSeekResult;
    private volatile boolean running;

    public LogFileGenerator(LogFileManager logFileManager, int binlogFileSize, boolean dryRun, FlushPolicy flushPolicy,
                            int flushInterval, int writeBufferSize, String taskName, TaskType taskType,
                            String groupName, String streamName, ExecutionConfig executionConfig) {
        this.logFileManager = logFileManager;
        this.binlogFileSize = binlogFileSize;
        this.dryRun = dryRun;
        this.initFlushPolicy = flushPolicy;
        this.currentFlushPolicy = initFlushPolicy;
        this.flushInterval = flushInterval;
        this.writeBufferSize = writeBufferSize;
        this.seekBufferSize = calcSeekBufferSize();
        this.startTso = "";
        this.supportWriteRowQueryLogEvent = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG);
        this.taskName = taskName;
        this.taskType = taskType;
        this.groupName = groupName;
        this.streamName = streamName;
        this.executionConfig = executionConfig;
        this.checkRowsQuery = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_CHECK_ROWS_QUERY);
        this.checkTso = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_CHECK_TSO);
        this.dryRunMode = DynamicApplicationConfig.getInt(BINLOG_WRITE_DRYRUN_MODE);
        this.useParallelWrite = buildParallelWriteSwitch();
        this.rpcUseAsyncMode = DynamicApplicationConfig.getBoolean(BINLOG_TXN_STREAM_CLIENT_USE_ASYNC_MODE);
        this.rpcReceiveQueueSize = DynamicApplicationConfig.getInt(BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE);
        this.useDirectByteBuffer = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER);
        this.flowControlWindowSize = calcFlowControlWindowSize();
        this.metrics = StreamMetrics.getStreamMetrics(streamName);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor = Executors.newFixedThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("log-file-generator-" + streamName).build());
        executor.execute(() -> {
            UpstreamBinlogFetcher binlogFetcher = null;
            long sleepInterval = 1000L;
            while (running) {
                try {
                    prepare();
                    AtomicBoolean isFirst = new AtomicBoolean(true);
                    binlogFetcher = new UpstreamBinlogFetcher(taskName, taskType, streamName, executionConfig,
                        messages -> {
                            try {
                                metrics.setLatestDataReceiveTime(System.currentTimeMillis());
                                for (TxnMessage message : messages) {
                                    if (isFirst.compareAndSet(true, false)) {
                                        currentToken = null;
                                        if (message.getTxnTag().getTxnMergedToken().getType() != TxnType.FORMAT_DESC) {
                                            throw new PolardbxException(
                                                "The first txn token must be FORMAT_DESC, but actual received is "
                                                    + message);
                                        } else {
                                            formatDescData = message.getTxnTag()
                                                .getTxnMergedToken()
                                                .getPayload()
                                                .toByteArray();
                                            tryWriteFileHeader(
                                                latestSeekResult != null ? latestSeekResult.getLastEventTimestamp() :
                                                    null);
                                            nextWritePosition = binlogFile.writePointer();
                                            logger.info("received format desc event size is " + formatDescData.length);
                                        }
                                    }

                                    if (dryRun && dryRunMode == 0) {
                                        dryRun(message);
                                        continue;
                                    }

                                    if (message.getType() == MessageType.WHOLE) {
                                        consume(message, MessageType.BEGIN);
                                        consume(message, MessageType.DATA);
                                        consume(message, MessageType.END);
                                    } else {
                                        consume(message, message.getType());
                                    }
                                }
                            } catch (IOException e) {
                                throw new PolardbxException("error occurred when consuming txn message.", e);
                            }
                        }, rpcUseAsyncMode, rpcReceiveQueueSize, flowControlWindowSize);
                    binlogFetcher.setMetrics(metrics);
                    binlogFetcher.connect();
                    binlogFetcher.dump(startTso);
                } catch (InterruptedException e) {
                    break;
                } catch (RetryableException re) {
                    logger.warn(re.getMessage());
                } catch (Throwable t) {
                    logger.error("process message event error", t);
                    try {
                        CommonUtils.sleep(sleepInterval);
                    } catch (InterruptedException e) {
                        break;
                    }
                    MonitorManager.getInstance().triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_FILE_GENERATE_ERROR,
                        ExceptionUtils.getStackTrace(t));
                } finally {
                    if (binlogFetcher != null) {
                        binlogFetcher.disconnect();
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

    private boolean buildParallelWriteSwitch() {
        boolean result;
        String configValue = DynamicApplicationConfig.getString(BINLOG_WRITE_USE_PARALLEL);
        if (StringUtils.equals("RANDOM", configValue)) {
            result = new Random().nextBoolean();
        } else {
            result = Boolean.parseBoolean(configValue);
        }
        if (result) {
            logger.info("binlog file write mode is parallel.");
        } else {
            logger.info("binlog file write mode is serial.");
        }
        return result;
    }

    private void prepare() throws IOException, InterruptedException {
        logger.info("prepare dumping from target task.");
        buildParallelWriter();
        buildBinlogFile();
        DynamicApplicationVersionConfig.applyConfigByTso(startTso);
        waitTaskConfigReady(startTso, streamName, () -> running);
        updateCursor(startTso);
    }

    private void buildParallelWriter() {
        if (useParallelWrite) {
            if (parallelWriter != null) {
                parallelWriter.stop();
            }
            int bufferSize = DynamicApplicationConfig.getInt(BINLOG_WRITE_PARALLEL_BUFFER_SIZE);
            int parallelism = DynamicApplicationConfig.getInt(BINLOG_WRITE_PARALLELISM);
            parallelWriter = new ParallelWriter(this, bufferSize, parallelism, metrics, dryRun, dryRunMode);
            parallelWriter.start();
        }
    }

    private void consume(TxnMessage message, MessageType processType) throws IOException, InterruptedException {
        checkTxnToken(message, processType);
        switch (processType) {
        case BEGIN:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();
            currentServerId = extractServerId(message);
            Assert.isTrue(currentToken.getType() == TxnType.DML);
            metrics.markBegin();
            metrics.setLatestDelayTimeOnCommit(System.currentTimeMillis() - currentTsoTimeMillSecond);
            writeBegin();

            break;
        case DATA:
            Assert.isTrue(currentToken.getType() == TxnType.DML);
            final List<TxnItem> itemsList = message.getTxnData().getTxnItemsList();
            writeDml(itemsList);

            break;
        case END:
            Assert.isTrue(currentToken.getType() == TxnType.DML);
            writeCommit();

            metrics.markEnd();
            metrics.incrementTotalWriteTxnCount();
            metrics.setLatestDelayTimeOnCommit(calcDelayTime());
            break;
        case TAG:
            currentToken = message.getTxnTag().getTxnMergedToken();
            resetCurrentTsoTime();
            currentServerId = extractServerId(message);
            if (currentToken.getType() == TxnType.META_DDL) {
                writeMetaDdl();
            } else if (currentToken.getType() == TxnType.META_SCALE) {
                writeMetaScale();
            } else if (currentToken.getType() == TxnType.META_HEARTBEAT) {
                writeHeartbeat();
            } else if (currentToken.getType() == TxnType.META_CONFIG_ENV_CHANGE) {
                writeMetaConfigEnvChange();
            }
            break;
        default:
            throw new PolardbxException("invalid message type for logfile generator: " + processType);
        }
    }

    private void checkTxnToken(TxnMessage message, MessageType processType) {
        TxnMergedToken latestToken = null;
        if (processType == MessageType.BEGIN && message.getTxnBegin().getTxnMergedToken().getType() == TxnType.DML) {
            latestToken = message.getTxnBegin().getTxnMergedToken();
        } else if (processType == MessageType.TAG) {
            latestToken = message.getTxnTag().getTxnMergedToken();
        }

        if (latestToken != null) {
            String latestTso = latestToken.getTso();
            if (latestToken.getType() != TxnType.FORMAT_DESC && latestTso.compareTo(startTso) <= 0) {
                throw new PolardbxException(
                    "Received duplicated token, the token`s tso can`t be equal or less than the start tso.The token is :"
                        + latestToken + ", the start tso is :" + startTso);
            }
            if (currentToken != null && latestToken.getType() != TxnType.FORMAT_DESC && checkTso) {
                int compareFlag = latestTso.compareTo(currentToken.getTso());
                if (compareFlag <= 0) {
                    throw new PolardbxException(
                        "Received disordered or duplicated token, latest token is " + latestToken +
                            ", previous token is " + currentToken);
                }
            }
        }
    }

    private void writeMetaDdl() throws IOException {
        logger.info("receive a ddl token with tso {}", currentToken.getTso());
        tryAwait();
        writeDdl(currentToken.getPayload().toByteArray());
        tryInvalidateTableId();
        tryFlush(true, nextWritePosition, currentToken.getTso(), currentTsoTimeSecond, true, false);
    }

    private void writeMetaScale() throws InterruptedException, IOException {
        logger.info("receive an meta scale token with tso {}", currentToken.getTso());
        tryAwait();
        StorageChangeInfo changeInfo = JSONObject.parseObject(
            new String(currentToken.getPayload().toByteArray()), StorageChangeInfo.class);
        recordStorageHistory(currentToken.getTso(), changeInfo.getInstructionId(),
            changeInfo.getStorageChangeEntity().getStorageInstList(), streamName, () -> running);
        writeTso(false, false);
        tryFlush(true, nextWritePosition, currentToken.getTso(), currentTsoTimeSecond, true, true);
        throw new RetryableException("try restarting because of meat_scale token :" + currentToken);
    }

    /**
     * 需要定时记录心跳信息到逻辑Binlog文件，</p>
     * 因为PolarX如果长时间没有数据写入，DN节点上的Binlog文件会被清理掉并上传到OSS，那么，逻辑Binlog文件中最新的tso可能也已经被清理掉了
     * 如果Dumper发生重启，会导致上游Task搜不到位点
     */
    private void writeHeartbeat() throws IOException {
        metrics.setLatestDelayTimeOnCommit(calcDelayTime());
        if (useParallelWrite) {
            if (shouldWriteHeartBeat()) {
                writeTso(true, true);
            } else {
                parallelWriter.push(SingleEventToken.builder().tso(currentToken.getTso())
                    .nextPosition(nextWritePosition).type(HEARTBEAT).serverId(currentServerId)
                    .tsoTimeSecond(currentTsoTimeSecond).build());
            }
        } else {
            if (shouldWriteHeartBeat()) {
                writeTso(false, false);
                tryFlush(true, nextWritePosition, currentToken.getTso(), currentTsoTimeSecond, true, false);
            } else {
                tryFlush(false, nextWritePosition, currentToken.getTso(), currentTsoTimeSecond, true, false);
            }
        }
    }

    private void writeMetaConfigEnvChange() throws IOException {
        logger.info("receive an meta config env change token with tso {}", currentToken.getTso());
        tryAwait();
        EnvConfigChangeInfo envConfigChangeInfo =
            GSON.fromJson(new String(currentToken.getPayload().toByteArray()), EnvConfigChangeInfo.class);
        recordMetaEnvConfigHistory(currentToken.getTso(), envConfigChangeInfo);
        writeConfigChangeEvent();
        tryFlush(true, nextWritePosition, currentToken.getTso(), currentTsoTimeSecond, true, false);
    }

    private void tryInvalidateTableId() {
        if (StringUtils.isNotBlank(currentToken.getSchema()) && StringUtils
            .isNotBlank(currentToken.getTable())) {
            tableIdManager.invalidate(currentToken.getSchema(), currentToken.getTable());
        }
    }

    private long extractServerId(TxnMessage message) {
        if (currentToken.hasServerId()) {
            return currentToken.getServerId().getValue();
        } else {
            List<TxnItem> itemList = message.getTxnData().getTxnItemsList();
            if (CollectionUtils.isEmpty(itemList)) {
                return ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
            }
            ByteString payload = itemList.get(0).getPayload();
            byte[] serverIdBytes = payload.substring(5, 9).toByteArray();
            return ByteHelper.readUnsignedIntLittleEndian(serverIdBytes, 0);
        }
    }

    /**
     * 检测是否为空文件，如果是则写入文件头
     */
    private void tryWriteFileHeader(Long timestamp) throws IOException {
        if (binlogFile.fileSize() == 0 && binlogFile.filePointer() == 0) {
            binlogFile.writeHeader();
            EventGenerator.updateServerId(formatDescData);
            EventGenerator.updatePos(formatDescData, 4 + formatDescData.length);
            if (timestamp != null) {
                EventGenerator.updateTimeStamp(formatDescData, timestamp);
            }
            binlogFile.writeEvent(formatDescData, 0, formatDescData.length, true);
            logger.info("write header and format desc event to binlog file : " + binlogFile.getFileName());
        }
    }

    private boolean shouldWriteHeartBeat() {
        return currentTsoTimeSecond % DynamicApplicationVersionConfig.getLong(ConfigKeys.HEARTBEAT_FLUSH_INTERVAL) == 0;
    }

    /**
     * 此方法只负责文件的准备，如果是一个新建文件，不进行文件头和format_desc的初始化
     */
    private void buildBinlogFile() throws IOException {
        latestSeekResult = null;
        CdcFile maxLocalCdcFile = logFileManager.getLocalMaxBinlogFile();
        File maxLocalFile = maxLocalCdcFile == null ? null : maxLocalCdcFile.newFile();
        long maxTableId = DynamicApplicationConfig.getLong(BINLOG_WRITE_TABLE_ID_BASE_VALUE);

        // recover by tso
        String recoverTso = null;
        String recoverFileName = null;
        Map<String, String> recoverTsoMap = executionConfig.getRecoverTsoMap();
        if (recoverTsoMap != null) {
            recoverTso = recoverTsoMap.get(streamName);
        }
        Map<String, String> recoverFileNameMap = executionConfig.getRecoverFileNameMap();
        if (recoverFileNameMap != null) {
            recoverFileName = recoverFileNameMap.get(streamName);
        }

        BinlogFileRecoverBuilder.RecoverInfo recoverInfo = BinlogFileRecoverBuilder
            .build(logFileManager, recoverTso, recoverFileName);
        if (maxLocalFile == null) {
            logger.info("recover by tso:{}, first file:{}", recoverInfo.getStartTso(), recoverInfo.getFileName());
            maxLocalFile = logFileManager.createLocalFile(recoverInfo.getFileName());
            binlogFile =
                new BinlogFile(maxLocalFile, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            startTso = recoverInfo.getStartTso();
        } else {
            BinlogFile.SeekResult seekResult;
            List<CdcFile> files = logFileManager.getAllLocalBinlogFilesOrdered();
            int count = files.size();

            // 从最后一个文件，尝试第一次获取startTso
            CdcFile maxFile = files.get(count - 1);
            binlogFile =
                new BinlogFile(maxFile.newFile(), MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            seekResult = binlogFile.seekLastTso();

            // 如果从最后一个文件没有获取到startTso，尝试从倒数第二个文件，进行第二次获取
            if (StringUtils.isBlank(seekResult.getLastTso())) {
                File startFile;
                if (count > 1) {
                    binlogFile.close();// 对上一个文件，先执行一下关闭
                    binlogFile = new BinlogFile(files.get(count - 2).newFile(), MODE, writeBufferSize, seekBufferSize,
                        useDirectByteBuffer, metrics);
                    seekResult = binlogFile.seekLastTso();
                    binlogFile.close();// 用完，关闭

                    if (StringUtils.isBlank(seekResult.getLastTso())) {
                        // 如果从倒数第二个文件仍然没有获取到，则为异常情况，因为附属于某个tso的数据，不允许跨文件保存(即一个事务不允许跨文件存储)，
                        // 我们会保证其完整性
                        throw new IllegalStateException(
                            String.format("can`t find start tso in the last two binlog files [%s, %s].",
                                files.get(count - 1),
                                files.get(count - 2)));
                    }
                    // 之前写入了一批不完整的数据，没必要保留，对文件进行一次重建
                    startFile = logFileManager.recreateLocalFile(files.get(count - 1).newFile());
                } else {
                    //如果只有一个文件，但没有tso，相当于没有文件，进入recover模式
                    if (!StringUtils.equals(maxFile.getName(), recoverInfo.getFileName())) {
                        maxFile.delete();
                        startFile = logFileManager.createLocalFile(recoverInfo.getFileName());
                    } else {
                        startFile = maxFile.newFile();
                    }
                    seekResult.setLastTso(recoverInfo.getStartTso());
                }

                binlogFile =
                    new BinlogFile(startFile, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            } else {
                // 如果从最后一个文件找到了tso，则需要判断一下文件的状态，是否需要rotate
                if (seekResult.getLastEventType() == LogEvent.ROTATE_EVENT) {
                    checkRotate(seekResult.getLastTso(), seekResult.getLastEventTimestamp(), true,
                        binlogFile.writePointer(), isMetaScaleTso(seekResult.getLastTso()));
                } else if (seekResult.getLastEventType() == LogEvent.ROWS_QUERY_LOG_EVENT) {
                    checkRotate(seekResult.getLastTso(), seekResult.getLastEventTimestamp(), false,
                        binlogFile.writePointer(), isMetaScaleTso(seekResult.getLastTso()));
                }
            }

            logger.info("seek result is : " + seekResult);
            startTso = seekResult.getLastTso();
            if (seekResult.getMaxTableId() != null) {
                maxTableId = seekResult.getMaxTableId();
            }
            latestSeekResult = seekResult;
        }

        binlogFile.tryTruncate();
        tryOverwriteStartTso();
        tableIdManager = new TableIdManager(maxTableId, binlogFile.filePointer() == 0);
        nextWritePosition = binlogFile.filePointer();
        logger.info("start tso is :[" + startTso + "]");
    }

    private int getFixedHeaderSize() {
        return 4 + formatDescData.length;
    }

    private void tryOverwriteStartTso() {
        String overwriteConfig = DynamicApplicationConfig.getString(BINLOG_RECOVERY_START_TSO_OVERWRITE_CONFIG);
        if (StringUtils.isNotBlank(overwriteConfig) && StringUtils.startsWith(overwriteConfig, "[") &&
            StringUtils.endsWith(overwriteConfig, "]")) {
            overwriteConfig = StringUtils.substringAfter(overwriteConfig, "[");
            overwriteConfig = StringUtils.substringBeforeLast(overwriteConfig, "]");
            String[] array = StringUtils.split(overwriteConfig, ",");
            if (array.length == 2 && StringUtils.equals(startTso, array[0])) {
                startTso = array[1];
                logger.warn("startTso is rewritten from {} to {}", array[0], array[1]);
            }
        }
    }

    private void writeBegin() throws IOException {
        nextWritePosition += BEGIN_EVENT_LENGTH;
        if (useParallelWrite) {
            parallelWriter.push(SingleEventToken.builder().tso(currentToken.getTso()).nextPosition(nextWritePosition)
                .type(BEGIN).serverId(currentServerId).tsoTimeSecond(currentTsoTimeSecond).length(BEGIN_EVENT_LENGTH)
                .build());
        } else {
            Pair<byte[], Integer> begin = makeBegin(currentTsoTimeSecond, currentServerId, nextWritePosition);
            binlogFile.writeEvent(begin.getLeft(), 0, begin.getRight(), true);
        }
    }

    private void writeDml(List<TxnItem> itemsList) throws IOException {
        int index = 0;
        String lastRowsQuery = null;
        for (TxnItem txnItem : itemsList) {
            // 从TableMap中取之前暂存的RowsQuery，生成一个RowsQuery Event
            if (txnItem.getEventType() == LogEvent.TABLE_MAP_EVENT && supportWriteRowQueryLogEvent) {
                String currentRowsQuery = txnItem.getRowsQuery();
                tryCheckRowsQuery(index, currentRowsQuery, lastRowsQuery);
                if (StringUtils.isNotBlank(currentRowsQuery)) {
                    writeRowsQuery(currentRowsQuery);
                }
                lastRowsQuery = currentRowsQuery;
            }

            final byte[] data = DirectByteOutput.unsafeFetch(txnItem.getPayload());
            metrics.incrementTotalRevBytes(data.length);
            updateDmlEvent(txnItem, data);
            nextWritePosition += data.length;

            if (useParallelWrite) {
                parallelWriter.push(SingleEventToken.builder().type(DML).nextPosition(nextWritePosition)
                    .tso(currentToken.getTso()).data(data).tsoTimeSecond(currentTsoTimeSecond)
                    .length(data.length).build());
            } else {
                EventGenerator.updatePos(data, nextWritePosition);
                binlogFile.writeEvent(data, 0, data.length, true);
            }
            metrics.incrementTotalWriteDmlEventCount();
            index++;
        }
    }

    private void tryCheckRowsQuery(int index, String currentRowsQuery, String lastRowsQuery) {
        if (checkRowsQuery && ClusterTypeEnum.BINLOG.name().equals(DynamicApplicationConfig.getClusterType())) {
            if (index == 0 && StringUtils.isBlank(currentRowsQuery)) {
                // DN未开启binlog_rows_query_log_events，或者task对RowsQuery的处理有bug
                throw new PolardbxException(String.format("rows query can`t be null, tso %s, index %s.",
                    currentToken.getTso(), index));
            }
            if (StringUtils.equals(currentRowsQuery, "/*DRDSnull*/")) {
                // DN中记录了RowsQuery，但是RowsQuery中没有traceid信息
                throw new PolardbxException(String.format("rows query must contains trace id info, tso %s , index %s ",
                    currentToken.getTso(), index));
            }
            if (StringUtils.isNotBlank(lastRowsQuery) && StringUtils.equals(currentRowsQuery, lastRowsQuery) &&
                CommonUtils.isTsoPolicyTrans(currentToken.getTso())) {
                // 对于 TSO事务，traceid不能重复
                throw new PolardbxException(String.format("detected duplicate trace id  %s , tso %s , index %s ",
                    currentRowsQuery, currentToken.getTso(), index));
            }
        }
    }

    private void updateDmlEvent(TxnItem txnItem, byte[] data) {
        EventGenerator.updateTimeStamp(data, currentTsoTimeSecond);
        if (StringUtils.isNotBlank(txnItem.getSchema()) && StringUtils.isNotBlank(txnItem.getTable())
            && containsTableId((byte) txnItem.getEventType())) {
            long tableId = tableIdManager.getTableId(txnItem.getSchema(), txnItem.getTable());
            EventGenerator.updateTableId(data, tableId);
        }
    }

    private void writeRowsQuery(String rowsQuery) throws IOException {
        //rows query 只包含hints，不会有中文字符，直接取string字符串的length即可
        int eventSize = ROWS_QUERY_FIXED_LENGTH + rowsQuery.length();
        nextWritePosition += eventSize;
        if (useParallelWrite) {
            parallelWriter.push(SingleEventToken.builder().tso(currentToken.getTso()).nextPosition(nextWritePosition)
                .type(ROWSQUERY).rowsQuery(rowsQuery).serverId(currentServerId).tsoTimeSecond(currentTsoTimeSecond)
                .length(eventSize).build());
        } else {
            final Pair<byte[], Integer> rowsQueryEvent =
                makeRowsQuery(currentTsoTimeSecond, currentServerId, rowsQuery, nextWritePosition);
            binlogFile.writeEvent(rowsQueryEvent.getLeft(), 0, rowsQueryEvent.getRight(), true);
        }
    }

    private void writeCommit() throws IOException {
        nextWritePosition += COMMIT_EVENT_LENGTH;
        if (useParallelWrite) {
            parallelWriter.push(SingleEventToken.builder().type(COMMIT).nextPosition(nextWritePosition)
                .tso(currentToken.getTso()).serverId(currentServerId).xid(XID_SEQ.incrementAndGet())
                .tsoTimeSecond(currentTsoTimeSecond).length(COMMIT_EVENT_LENGTH).build());
            writeTso(true, false);
        } else {
            final Pair<byte[], Integer> commit = makeCommit(currentTsoTimeSecond, currentServerId,
                XID_SEQ.incrementAndGet(), nextWritePosition);
            binlogFile.writeEvent(commit.getLeft(), 0, commit.getRight(), true);
            writeTso(false, false);
            tryFlush(currentFlushPolicy == FlushPolicy.FlushPerTxn, nextWritePosition, currentToken.getTso(),
                currentTsoTimeSecond, true, false);
        }
    }

    private void writeDdl(byte[] data) throws IOException {
        nextWritePosition += data.length;
        EventGenerator.updateTimeStamp(data, currentTsoTimeSecond);
        EventGenerator.updatePos(data, nextWritePosition);
        binlogFile.writeEvent(data, 0, data.length, true);

        writeTso(false, false);
        metrics.incrementTotalWriteDdlEventCount();
    }

    private void writeTso(boolean useParallel, boolean forceFlush) throws IOException {
        String cts = MarkType.CTS + "::" + currentToken.getTso();
        int eventSize = ROWS_QUERY_FIXED_LENGTH + cts.length();
        nextWritePosition += eventSize;

        if (useParallel) {
            parallelWriter.push(SingleEventToken.builder().tso(currentToken.getTso()).nextPosition(nextWritePosition)
                .type(TSO).cts(cts).forceFlush(forceFlush).serverId(currentServerId).tsoTimeSecond(currentTsoTimeSecond)
                .length(eventSize).build());
            resetNextWritePosition();
        } else {
            final Pair<byte[], Integer> tsoEvent = makeMarkEvent(currentTsoTimeSecond, currentServerId,
                cts, nextWritePosition);
            binlogFile.writeEvent(tsoEvent.getLeft(), 0, tsoEvent.getRight(), true);
        }
    }

    private void resetNextWritePosition() {
        if (nextWritePosition >= binlogFileSize) {
            nextWritePosition = getFixedHeaderSize();
            tableIdManager.tryReset();
        }
    }

    private void writeConfigChangeEvent() throws IOException {
        String markCTS = MarkType.CTS + "::" + currentToken.getTso() + "::" + MetaCoopCommandEnum.ConfigChange;
        nextWritePosition += (ROWS_QUERY_FIXED_LENGTH + markCTS.length());
        final Pair<byte[], Integer> tsoEvent = makeMarkEvent(currentTsoTimeSecond, currentServerId,
            markCTS, nextWritePosition);
        binlogFile.writeEvent(tsoEvent.getLeft(), 0, tsoEvent.getRight(), true);
        DynamicApplicationVersionConfig.setConfigByTso(currentToken.getTso());
    }

    private boolean checkRotate(String tso, long timestamp, boolean rotateAlreadyExist, long position,
                                boolean isMetaScale)
        throws IOException {
        if (rotateAlreadyExist || position >= binlogFileSize || isMetaScale) {
            logger.info("start to rotate file " + binlogFile.getFileName());

            // 如果rotateAlreadyExist为true，说明是上次成功写入rotate后，但没来的及创建新文件的情况
            if (!rotateAlreadyExist) {
                String nextFileName = BinlogFileUtil.getNextBinlogFileName(binlogFile.getFileName());
                Pair<byte[], Integer> rotateEvent = EventGenerator
                    .makeRotate(timestamp, nextFileName, position + 31 + nextFileName.length());
                binlogFile.writeEvent(rotateEvent.getLeft(), 0, rotateEvent.getRight(), true);
                binlogFile.close();
            }

            // reset binlog file
            String oldFileName = binlogFile.getFileName();
            File newFile = logFileManager.rotateFile(binlogFile.getFile(), new LogEndInfo(timestamp * 1000, tso));
            binlogFile = new BinlogFile(newFile, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics);
            logger.info("Binlog file rotate from {} to {}", oldFileName, newFile.getName());

            //wait前要先update一下cursor
            updateCursor(tso);
            if (isMetaScale) {
                waitUploadComplete(oldFileName, streamName);
            }
            return true;
        }
        return false;
    }

    private void dryRun(TxnMessage message) {
        switch (message.getType()) {
        case TAG:
            currentToken = message.getTxnTag().getTxnMergedToken();
            resetCurrentTsoTime();

            metrics.setLatestDelayTimeOnCommit(calcDelayTime());
            if (message.getTxnTag().getTxnMergedToken().getType() == TxnType.META_DDL) {
                metrics.incrementTotalWriteDdlEventCount();
            }
            break;
        case WHOLE:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();

            metrics.setLatestDelayTimeOnCommit(calcDelayTime());
            metrics.incrementTotalWriteEventCount();//模拟begin
            for (int i = 0; i < message.getTxnData().getTxnItemsCount(); i++) {
                metrics.incrementTotalWriteDmlEventCount();
                metrics.incrementTotalWriteEventCount();
            }
            metrics.incrementTotalWriteEventCount();//模拟end
            metrics.incrementTotalWriteTxnCount();
            break;
        case BEGIN:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();

            metrics.setLatestDelayTimeOnCommit(calcDelayTime());
            metrics.incrementTotalWriteEventCount();//模拟begin
            break;
        case DATA:
            for (int i = 0; i < message.getTxnData().getTxnItemsCount(); i++) {
                metrics.incrementTotalWriteDmlEventCount();
                metrics.incrementTotalWriteEventCount();
            }
            break;
        case END:
            metrics.incrementTotalWriteEventCount();//模拟end
            metrics.incrementTotalWriteTxnCount();
            break;
        default:
            throw new PolardbxException("invalid message type " + message.getType());
        }
    }

    private void recordMetaEnvConfigHistory(final String tso, EnvConfigChangeInfo envConfigChangeInfo) {
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        final BinlogEnvConfigHistoryMapper configHistoryMapper =
            SpringContextHolder.getObject(BinlogEnvConfigHistoryMapper.class);
        transactionTemplate.execute((o) -> {
            // 幂等判断
            // tso记录到binlog文件和记录到BinlogEnvConfigHistory表是非原子操作，需要做幂等判断
            // instructionId也是不能重复的，正常情况下tso和instructionId是一对一的关系，但可能出现bug
            // 比如：https://yuque.antfin-inc.com/jingwei3/knddog/uxpbzq，所以此处查询要更严谨一些，where条件也要包含instructionId
            List<BinlogEnvConfigHistory> configHistoryList = configHistoryMapper.select(
                s -> s.where(BinlogEnvConfigHistoryDynamicSqlSupport.tso, isEqualTo(tso))
                    .and(BinlogEnvConfigHistoryDynamicSqlSupport.instructionId,
                        isEqualTo(envConfigChangeInfo.getInstructionId())));
            if (configHistoryList.isEmpty()) {
                BinlogEnvConfigHistory binlogEnvConfigHistory = new BinlogEnvConfigHistory();
                binlogEnvConfigHistory.setTso(currentToken.getTso());
                binlogEnvConfigHistory.setInstructionId(envConfigChangeInfo.getInstructionId());
                binlogEnvConfigHistory.setChangeEnvContent(envConfigChangeInfo.getContent());
                configHistoryMapper.insert(binlogEnvConfigHistory);
                logger.info("record meta env config change history : " + GSON.toJson(envConfigChangeInfo));
            } else {
                logger
                    .info("env config change  history with tso {} or instruction id {} is already exist, ignored.", tso,
                        envConfigChangeInfo.getInstructionId());
            }
            return null;
        });
    }

    private void tryAwait() {
        if (useParallelWrite) {
            parallelWriter.await();
        }
    }

    public void tryFlush4ParallelWrite(long position, String tso, long tsoTimeSecond, boolean forceFlush)
        throws IOException {
        if (forceFlush) {
            tryFlush(true, position, tso, tsoTimeSecond, false, false);
        } else {
            tryFlush(currentFlushPolicy == FlushPolicy.FlushPerTxn, position, tso, tsoTimeSecond, false, false);
        }
        metrics.setLatestBinlogFile(binlogFile == null ? "" : binlogFile.getFileName());
        metrics.setLatestTsoTime(tsoTimeSecond * 1000);
    }

    private void tryFlush(boolean forceFlush, long position, String tso, long tsoTimeSecond,
                          boolean resetPositionIfRotate, boolean isMetaScale)
        throws IOException {
        if (forceFlush) {
            updateCursor(tso);
        } else if (System.currentTimeMillis() - binlogFile.lastFlushTime() >= flushInterval) {
            updateCursor(tso);
        }

        boolean needRotate = checkRotate(tso, tsoTimeSecond, false, position, isMetaScale);
        if (needRotate) {
            updateCursor(tso);
            tryWriteFileHeader(tsoTimeSecond);
            if (resetPositionIfRotate) {
                resetNextWritePosition();
            }
        }
    }

    private void updateCursor(String tso) throws IOException {
        binlogFile.flush();
        Cursor cursor = new Cursor(binlogFile.getFileName(), binlogFile.filePointer(),
            groupName, streamName, tso, executionConfig.getRuntimeVersion());
        logFileManager.setLatestFileCursor(cursor);
        new LatestFileCursorChangeEvent(cursor).post();
        if (logger.isDebugEnabled()) {
            logger.debug("cursor is updated to " + cursor);
        }
    }

    private void resetCurrentTsoTime() {
        currentTsoTimeMillSecond = getTsoPhysicalTime(currentToken.getTso(), TimeUnit.MILLISECONDS);
        currentTsoTimeSecond = currentTsoTimeMillSecond / 1000;
    }

    private long calcDelayTime() {
        long delayTime = System.currentTimeMillis() - currentTsoTimeMillSecond;
        if (currentToken.getType() == TxnType.DML) {
            if (delayTime > flushInterval) {
                //如果已经超过了flushInterval，自动切换为FlushAtInterval模式
                if (currentFlushPolicy == FlushPolicy.FlushPerTxn) {
                    currentFlushPolicy = FlushPolicy.FlushAtInterval;
                    logger.info("Flush policy is changed from {} to {}.", FlushPolicy.FlushPerTxn,
                        FlushPolicy.FlushAtInterval);
                }
            } else {
                if (initFlushPolicy == FlushPolicy.FlushPerTxn && currentFlushPolicy == FlushPolicy.FlushAtInterval) {
                    currentFlushPolicy = initFlushPolicy;
                    logger.info("Flush policy is recovered from {} to {}.", FlushPolicy.FlushAtInterval,
                        FlushPolicy.FlushPerTxn);
                }
            }
        }
        return delayTime;
    }

    private int calcFlowControlWindowSize() {
        if (isBinlogXStream(streamName)) {
            double maxWindowSize = DynamicApplicationConfig.getDouble(BINLOG_X_TXN_STREAM_FLOW_CONTROL_WINDOW_MAX_SIZE);
            int count = executionConfig.getSources().size() * executionConfig.getStreamNameSet().size();
            return Double.valueOf(maxWindowSize / count).intValue();
        } else {
            return DynamicApplicationConfig.getInt(BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE);
        }
    }

    private int calcSeekBufferSize() {
        if (isBinlogXStream(streamName)) {
            double maxSeekBufferSize = DynamicApplicationConfig.getDouble(BINLOG_X_FILE_SEEK_BUFFER_MAX_SIZE);
            int streamCount = executionConfig.getStreamNameSet().size();
            return Double.valueOf(maxSeekBufferSize / streamCount).intValue();
        } else {
            return DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_BUFFER_SIZE);
        }
    }

    public BinlogFile getBinlogFile() {
        return binlogFile;
    }
}
