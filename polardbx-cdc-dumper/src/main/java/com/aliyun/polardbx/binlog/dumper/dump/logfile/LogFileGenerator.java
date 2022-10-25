/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSONObject;
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
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.ParallelWriter;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken;
import com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator;
import com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager;
import com.aliyun.polardbx.binlog.dumper.metrics.Metrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcClient;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_RECOVERY_START_TSO_OVERWRITE_CONFIG;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_CLIENT_USE_ASYNC_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_DRYRUN_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_PARALLEL_BUFFER_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_TABLE_ID_BASE_VALUE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_USE_PARALLEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.BEGIN;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.COMMIT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.DML;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.HEARTBEAT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.ROWSQUERY;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken.Type.TSO;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.BEGIN_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.COMMIT_EVENT_LENGTH;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.ROWS_QUERY_FIXED_LENGTH;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeBegin;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeCommit;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeMarkEvent;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeRowsQuery;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.recordStorageHistory;
import static com.aliyun.polardbx.binlog.dumper.dump.util.MetaScaleUtil.waitOriginTsoReady;
import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.containsTableId;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;

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
    private final boolean useDirectByteBuffer;
    private final int flowControlWindowSize;
    private long nextWritePosition = 0;

    //并行写入
    private final boolean useParallelWrite;
    private ParallelWriter parallelWriter;

    private byte[] formatDescData;
    private ExecutorService executor;
    private String startTso;
    private TxnMergedToken currentToken;
    private long currentTsoTimeSecond;//为了性能优化，将物理时间保存到该实例变量，避免每次实时解析
    private long currentTsoTimeMillSecond;
    private Long currentServerId;
    private volatile BinlogFile binlogFile;
    private String targetTaskAddress;
    private volatile FlushPolicy currentFlushPolicy;
    private TableIdManager tableIdManager;
    private BinlogFile.SeekResult latestSeekResult;
    private volatile boolean running;

    public LogFileGenerator(LogFileManager logFileManager, int binlogFileSize, boolean dryRun, FlushPolicy flushPolicy,
                            int flushInterval, int writeBufferSize, int seekBufferSize) {
        this.logFileManager = logFileManager;
        this.binlogFileSize = binlogFileSize;
        this.dryRun = dryRun;
        this.initFlushPolicy = flushPolicy;
        this.currentFlushPolicy = initFlushPolicy;
        this.flushInterval = flushInterval;
        this.writeBufferSize = writeBufferSize;
        this.seekBufferSize = seekBufferSize;
        this.startTso = "";
        this.supportWriteRowQueryLogEvent = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_SUPPORT_ROWS_QUERY_LOG);
        this.dryRunMode = DynamicApplicationConfig.getInt(BINLOG_WRITE_DRYRUN_MODE);
        this.useParallelWrite = buildParallelWriteSwitch();
        this.rpcUseAsyncMode = DynamicApplicationConfig.getBoolean(BINLOG_TXN_STREAM_CLIENT_USE_ASYNC_MODE);
        this.rpcReceiveQueueSize = DynamicApplicationConfig.getInt(BINLOG_TXN_STREAM_CLIENT_RECEIVE_QUEUE_SIZE);
        this.useDirectByteBuffer = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_USE_DIRECT_BYTE_BUFFER);
        this.flowControlWindowSize = DynamicApplicationConfig.getInt(BINLOG_TXN_STREAM_FLOW_CONTROL_WINDOW_SIZE);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        executor = Executors.newFixedThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("log-file-generator-%d").build());
        executor.execute(() -> {
            TxnStreamRpcClient rpcClient = null;
            long sleepInterval = 1000L;
            while (running) {
                try {
                    prepare();
                    NettyChannelBuilder channelBuilder =
                        (NettyChannelBuilder) ManagedChannelBuilder.forTarget(targetTaskAddress).usePlaintext();
                    AtomicBoolean isFirst = new AtomicBoolean(true);
                    rpcClient = new TxnStreamRpcClient(channelBuilder, messages -> {
                        try {
                            Metrics.get().setLatestDataReceiveTime(System.currentTimeMillis());
                            for (TxnMessage message : messages) {
                                if (isFirst.compareAndSet(true, false)) {
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
                                            latestSeekResult != null ? latestSeekResult.getLastEventTimestamp() : null);
                                        nextWritePosition = binlogFile.writePointer();
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
                    rpcClient.setMetricsConsumer(i -> Metrics.get().setReceiveQueueSize(i));
                    rpcClient.connect();
                    rpcClient.dump(DumpRequest.newBuilder().setTso(startTso).build());
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
                    // 重试间隔随重试次数增大，最大重试间隔10S
                    if (sleepInterval < 10 * 1000) {
                        sleepInterval += 1000;
                    }
                } finally {
                    if (rpcClient != null) {
                        rpcClient.disconnect();
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
        logger.info("prepare dumping from final task.");
        buildParallelWriter();
        buildBinlogFile();
        DynamicApplicationVersionConfig.applyConfigByTso(startTso);
        waitTaskConfigReady(startTso);
        buildTarget();
        if (StringUtils.isNotBlank(startTso)) {
            updateCursor();
        }
    }

    private void buildParallelWriter() {
        if (useParallelWrite) {
            if (parallelWriter != null) {
                parallelWriter.stop();
            }
            int bufferSize = DynamicApplicationConfig.getInt(BINLOG_WRITE_PARALLEL_BUFFER_SIZE);
            int parallelism = DynamicApplicationConfig.getInt(BINLOG_WRITE_PARALLELISM);
            parallelWriter = new ParallelWriter(this, bufferSize, parallelism, dryRun, dryRunMode);
            parallelWriter.start();
        }
    }

    private void consume(TxnMessage message, MessageType processType) throws IOException, InterruptedException {
        if (processType == MessageType.BEGIN && message.getTxnBegin().getTxnMergedToken().getType() == TxnType.DML
            && message.getTxnBegin().getTxnMergedToken().getTso().compareTo(startTso) <= 0) {
            throw new PolardbxException(
                "Received duplicate token, the token`s tso can`t be equal or less than the start tso.The token is :"
                    + message.getTxnBegin().getTxnMergedToken() + ", the start tso is :"
                    + startTso);
        }

        switch (processType) {
        case BEGIN:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();
            currentServerId = extractServerId(message);
            Assert.isTrue(currentToken.getType() == TxnType.DML);
            Metrics.get().markBegin();
            Metrics.get().setLatestDelayTimeOnCommit(System.currentTimeMillis() - currentTsoTimeMillSecond);
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

            Metrics.get().markEnd();
            Metrics.get().incrementTotalWriteTxnCount();
            Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime());
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

    private void writeMetaDdl() throws IOException {
        logger.info("receive a ddl token with tso {}", currentToken.getTso());
        tryAwait();
        writeDdl(currentToken.getPayload().toByteArray());
        tryInvalidateTableId();
        tryFlush(true, nextWritePosition, currentTsoTimeSecond, true);
    }

    private void writeMetaScale() throws InterruptedException, IOException {
        logger.info("receive an meta scale token with tso {}", currentToken.getTso());
        tryAwait();
        StorageChangeInfo changeInfo = JSONObject.parseObject(
            new String(currentToken.getPayload().toByteArray()), StorageChangeInfo.class);
        recordStorageHistory(currentToken.getTso(), changeInfo.getInstructionId(),
            changeInfo.getStorageChangeEntity().getStorageInstList(), () -> running);
        writeTso(false, false);
        tryFlush(true, nextWritePosition, currentTsoTimeSecond, true);
        throw new RetryableException("try restarting because of meat_scale token :" + currentToken);
    }

    /**
     * 需要定时记录心跳信息到逻辑Binlog文件，</p>
     * 因为PolarX如果长时间没有数据写入，DN节点上的Binlog文件会被清理掉并上传到OSS，那么，逻辑Binlog文件中最新的tso可能也已经被清理掉了
     * 如果Dumper发生重启，会导致上游Task搜不到位点
     */
    private void writeHeartbeat() throws IOException {
        Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime());
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
                tryFlush(true, nextWritePosition, currentTsoTimeSecond, true);
            } else {
                tryFlush(false, nextWritePosition, currentTsoTimeSecond, true);
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
        tryFlush(true, binlogFile.writePointer(), currentTsoTimeSecond, true);
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
        File file = logFileManager.getMaxBinlogFile();
        long maxTableId = DynamicApplicationConfig.getLong(BINLOG_WRITE_TABLE_ID_BASE_VALUE);

        if (file == null) {
            file = logFileManager.createFirstLogFile();
            binlogFile = new BinlogFile(file, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer);
            startTso = "";
        } else {
            BinlogFile.SeekResult seekResult;
            List<File> files = logFileManager.getAllLogFilesOrdered();
            int count = files.size();

            // 从最后一个文件，尝试第一次获取startTso
            File maxFile = files.get(count - 1);
            binlogFile = new BinlogFile(maxFile, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer);
            seekResult = binlogFile.seekLastTso();

            // 如果从最后一个文件没有获取到startTso，尝试从倒数第二个文件，进行第二次获取
            if (StringUtils.isBlank(seekResult.getLastTso())) {
                if (count > 1) {
                    binlogFile.close();// 对上一个文件，先执行一下关闭
                    binlogFile = new BinlogFile(files.get(count - 2), MODE, writeBufferSize, seekBufferSize,
                        useDirectByteBuffer);
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
                }

                // 之前写入了一批不完整的数据，没必要保留，对文件进行一次重建
                File max = logFileManager.reCreateFile(files.get(count - 1));
                binlogFile = new BinlogFile(max, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer);
            } else {
                // 如果从最后一个文件找到了tso，则需要判断一下文件的状态，是否需要rotate
                if (seekResult.getLastEventType() == LogEvent.ROTATE_EVENT) {
                    checkRotate(seekResult.getLastEventTimestamp(), true, binlogFile.writePointer());
                } else if (seekResult.getLastEventType() == LogEvent.ROWS_QUERY_LOG_EVENT) {
                    checkRotate(seekResult.getLastEventTimestamp(), false, binlogFile.writePointer());
                }
            }

            logger.info("seek result is : " + seekResult);
            startTso = seekResult.getLastTso();
            if (seekResult.getMaxTableId() != null) {
                maxTableId = seekResult.getMaxTableId();
            }
            latestSeekResult = seekResult;
        }

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

    private void buildTarget() {
        // fixed port move to TaskInfo
        RelayFinalTaskInfoMapper relayFinalTaskInfoMapper = SpringContextHolder
            .getObject(RelayFinalTaskInfoMapper.class);
        Optional<RelayFinalTaskInfo> relayFinalTaskInfo = relayFinalTaskInfoMapper.selectOne(
            s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId,
                SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(TaskType.Final.name())));
        relayFinalTaskInfo.ifPresent(info -> {
            targetTaskAddress = info.getIp() + ":" + info.getPort();
        });

        if (StringUtils.isBlank(targetTaskAddress)) {
            throw new PolardbxException("target task is unavailable now ,will try later.");
        }

        logger.info("target final task address is :" + targetTaskAddress);
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
        for (TxnItem txnItem : itemsList) {
            // 从TableMap中取之前暂存的RowsQuery，生成一个RowsQuery Event
            if (txnItem.getEventType() == LogEvent.TABLE_MAP_EVENT && StringUtils.isNotBlank(txnItem.getRowsQuery())
                && supportWriteRowQueryLogEvent) {
                writeRowsQuery(txnItem.getRowsQuery());
            }

            final byte[] data = DirectByteOutput.unsafeFetch(txnItem.getPayload());
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
            Metrics.get().incrementTotalWriteDmlEventCount();
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
            tryFlush(currentFlushPolicy == FlushPolicy.FlushPerTxn, nextWritePosition, currentTsoTimeSecond, true);
        }
    }

    private void writeDdl(byte[] data) throws IOException {
        nextWritePosition += data.length;
        EventGenerator.updateTimeStamp(data, currentTsoTimeSecond);
        EventGenerator.updatePos(data, nextWritePosition);
        binlogFile.writeEvent(data, 0, data.length, true);

        writeTso(false, false);
        Metrics.get().incrementTotalWriteDdlEventCount();
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
        nextWritePosition += markCTS.length();
        final Pair<byte[], Integer> tsoEvent = makeMarkEvent(currentTsoTimeSecond, currentServerId,
            markCTS, nextWritePosition);
        binlogFile.writeEvent(tsoEvent.getLeft(), 0, tsoEvent.getRight(), true);
        DynamicApplicationVersionConfig.setConfigByTso(currentToken.getTso());
    }

    private boolean checkRotate(long timestamp, boolean rotateAlreadyExist, long position) throws IOException {
        if (rotateAlreadyExist || position >= binlogFileSize) {
            logger.info("start to rotate file " + binlogFile.getFileName());

            // 如果rotateAlreadyExist为true，说明是上次成功写入rotate后，但没来的及创建新文件的情况
            if (!rotateAlreadyExist) {
                String nextFileName = logFileManager.nextBinlogFileName(binlogFile.getFileName());
                Pair<byte[], Integer> rotateEvent = EventGenerator
                    .makeRotate(timestamp, nextFileName, position + 31 + nextFileName.length());
                binlogFile.writeEvent(rotateEvent.getLeft(), 0, rotateEvent.getRight(), true);
                binlogFile.close();
            }

            // reset binlog file
            String oldFileName = binlogFile.getFileName();
            File newFile = logFileManager.rotateFile(binlogFile.getFile(), timestamp * 1000);
            binlogFile = new BinlogFile(newFile, MODE, writeBufferSize, seekBufferSize, useDirectByteBuffer);
            logger.info("Binlog file rotate from {} to {}", oldFileName, newFile.getName());
            return true;
        }
        return false;
    }

    private void dryRun(TxnMessage message) {
        switch (message.getType()) {
        case TAG:
            currentToken = message.getTxnTag().getTxnMergedToken();
            resetCurrentTsoTime();

            Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime());
            if (message.getTxnTag().getTxnMergedToken().getType() == TxnType.META_DDL) {
                Metrics.get().incrementTotalWriteDdlEventCount();
            }
            break;
        case WHOLE:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();

            Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime());
            Metrics.get().incrementTotalWriteEventCount();//模拟begin
            for (int i = 0; i < message.getTxnData().getTxnItemsCount(); i++) {
                Metrics.get().incrementTotalWriteDmlEventCount();
                Metrics.get().incrementTotalWriteEventCount();
            }
            Metrics.get().incrementTotalWriteEventCount();//模拟end
            Metrics.get().incrementTotalWriteTxnCount();
            break;
        case BEGIN:
            currentToken = message.getTxnBegin().getTxnMergedToken();
            resetCurrentTsoTime();

            Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime());
            Metrics.get().incrementTotalWriteEventCount();//模拟begin
            break;
        case DATA:
            for (int i = 0; i < message.getTxnData().getTxnItemsCount(); i++) {
                Metrics.get().incrementTotalWriteDmlEventCount();
                Metrics.get().incrementTotalWriteEventCount();
            }
            break;
        case END:
            Metrics.get().incrementTotalWriteEventCount();//模拟end
            Metrics.get().incrementTotalWriteTxnCount();
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

    public void tryFlush4ParallelWrite(long position, long tsoTimeSecond, boolean forceFlush)
        throws IOException {
        if (forceFlush) {
            tryFlush(true, position, tsoTimeSecond, false);
        } else {
            tryFlush(currentFlushPolicy == FlushPolicy.FlushPerTxn, position, tsoTimeSecond, false);
        }
    }

    private void tryFlush(boolean forceFlush, long position, long tsoTimeSecond, boolean resetPositionIfRotate)
        throws IOException {
        if (forceFlush) {
            updateCursor();
        } else if (System.currentTimeMillis() - binlogFile.lastFlushTime() >= flushInterval) {
            updateCursor();
        }

        boolean needRotate = checkRotate(tsoTimeSecond, false, position);
        if (needRotate) {
            updateCursor();
            tryWriteFileHeader(tsoTimeSecond);
            if (resetPositionIfRotate) {
                resetNextWritePosition();
            }
        }
    }

    private void updateCursor() throws IOException {
        binlogFile.flush();
        Cursor cursor = new Cursor(binlogFile.getFileName(), binlogFile.filePointer());
        logFileManager.setLatestFileCursor(cursor);
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

    private void waitTaskConfigReady(String startTso) throws InterruptedException {
        waitOriginTsoReady(() -> running);
        String expectedStorageTso = StorageUtil.buildExpectedStorageTso(startTso);
        logger.info("expected storage tso is : " + expectedStorageTso);
        SystemDbConfig.upsertSystemDbConfig(EXPECTED_STORAGE_TSO_KEY, expectedStorageTso);

        BinlogTaskConfigMapper taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        long start = System.currentTimeMillis();
        while (true) {
            List<BinlogTaskConfig> taskConfigs = taskConfigMapper.select(
                t -> t.where(BinlogTaskConfigDynamicSqlSupport.clusterId,
                    isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                    .and(BinlogTaskConfigDynamicSqlSupport.role, isIn(TaskType.Final.name(), TaskType.Relay.name())))
                .stream().filter(tc -> {
                    TaskConfig config = GSON.fromJson(tc.getConfig(), TaskConfig.class);
                    return !expectedStorageTso.equals(config.getTso());
                }).collect(Collectors.toList());

            if (!taskConfigs.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                if ((System.currentTimeMillis() - start) >= 10 * 1000) {
                    throw new PolardbxException(
                        "task config for current storage tso is not ready :" + expectedStorageTso);
                }
                if (!running) {
                    throw new InterruptedException("wait interrupt");
                }
            } else {
                break;
            }
        }
    }

    public BinlogFile getBinlogFile() {
        return binlogFile;
    }
}
