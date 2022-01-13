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
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator;
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
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.instructionId;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeBegin;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeCommit;
import static com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator.makeMarkEvent;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * Created by ziyang.lb
 */
@SuppressWarnings("rawtypes")
public class LogFileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(LogFileGenerator.class);
    private static final String MODE = "rw";
    private static final Gson GSON = new GsonBuilder().create();

    // 缓存formatDesc数据，binlog文件滚动需要
    private final LogFileManager logFileManager;
    private final Integer binlogFileSize;
    private final boolean dryRun;
    private final FlushPolicy initFlushPolicy;
    private final int flushInterval;
    private final int writeBufferSize;
    private final int seekBufferSize;

    private byte[] formatDescData;
    private ExecutorService executor;
    private String startTso;
    private TxnMergedToken currentToken;
    private BinlogFile binlogFile;
    private String targetTaskAddress;
    private FlushPolicy currentFlushPolicy;
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
                                Metrics.get().incrementTotalRevBytes(message.getSerializedSize());

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
                                        tryWriteFileHeader();
                                    }
                                }

                                if (dryRun) {
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
                    });
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

    private void prepare() throws IOException, InterruptedException {
        logger.info("prepare dumping from final task.");
        buildBinlogFile();
        DynamicApplicationVersionConfig.applyConfigByTso(startTso);
        waitTaskConfigReady(startTso);
        buildTarget();
        if (StringUtils.isNotBlank(startTso)) {
            updateCursor();
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
            assert currentToken.getType() == TxnType.DML;
            Metrics.get().setLatestDelayTimeOnReceive(calcDelayTime(currentToken));
            Metrics.get().markBegin();
            long serverId = extractServerId(message);
            writeBegin(serverId);

            break;
        case DATA:
            assert currentToken.getType() == TxnType.DML;
            final List<TxnItem> itemsList = message.getTxnData().getTxnItemsList();
            writeDml(itemsList);

            break;
        case END:
            assert currentToken.getType() == TxnType.DML;
            serverId = extractServerId(message);
            writeCommit(serverId);
            tryFlush(currentFlushPolicy == FlushPolicy.FlushPerTxn);

            Metrics.get().markEnd();
            Metrics.get().incrementTotalWriteTxnCount();
            Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime(currentToken));

            break;
        case TAG:
            currentToken = message.getTxnTag().getTxnMergedToken();
            if (currentToken.getType() == TxnType.META_DDL) {
                writeDdl(currentToken.getPayload().toByteArray());
                tryFlush(true);
            } else if (currentToken.getType() == TxnType.META_DDL_PRIVATE) {
                writePrivateDdl(currentToken.getPayload().toByteArray());
                tryFlush(true);
            } else if (currentToken.getType() == TxnType.META_SCALE) {
                logger.info("receive an meta scale token with tso {}", currentToken.getTso());
                StorageChangeInfo changeInfo = JSONObject.parseObject(
                    new String(currentToken.getPayload().toByteArray()), StorageChangeInfo.class);
                recordStorageHistory(currentToken.getTso(), changeInfo.getInstructionId(),
                    changeInfo.getStorageChangeEntity().getStorageInstList());
                writeTso();
                tryFlush(true);
                throw new RetryableException("try restarting because of meat_scale token :" + currentToken);
            } else if (currentToken.getType() == TxnType.META_HEARTBEAT) {
                Metrics.get().setLatestDelayTimeOnCommit(calcDelayTime(currentToken));
                boolean flag = tryWriteHeartBeat();
                if (binlogFile.hasBufferedData() && flag) {
                    tryFlush(true);
                }
            } else if (currentToken.getType() == TxnType.META_CONFIG_ENV_CHANGE) {
                logger.info("receive an meta config env change token with tso {}", currentToken.getTso());
                EnvConfigChangeInfo envConfigChangeInfo =
                    GSON.fromJson(new String(currentToken.getPayload().toByteArray()), EnvConfigChangeInfo.class);
                recordMetaEnvConfigHistory(currentToken.getTso(), envConfigChangeInfo);
                writeConfigChangeEvent();
                tryFlush(true);
            }
            break;
        default:
            throw new PolardbxException("invalid message type for logfile generator: " + processType);
        }
    }

    private long extractServerId(TxnMessage message) {
        List<TxnItem> itemList = message.getTxnData().getTxnItemsList();
        if (CollectionUtils.isEmpty(itemList)) {
            return ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
        }
        ByteString payload = itemList.get(0).getPayload();
        byte[] serverIdBytes = payload.substring(5, 9).toByteArray();
        return ByteHelper.readUnsignedIntLittleEndian(serverIdBytes, 0);
    }

    /**
     * 检测是否为空文件，如果是则写入文件头
     */
    private void tryWriteFileHeader() throws IOException {
        if (binlogFile.length() == 0 && binlogFile.position() == 0) {
            binlogFile.writeHeader();
            EventGenerator.updateServerId(formatDescData);
            binlogFile.writeEvent(formatDescData, 0, formatDescData.length, true);
            logger.info("write header and format desc event to binlog file : " + binlogFile.getFileName());
        }
    }

    /**
     * 需要定时记录心跳信息到逻辑Binlog文件，</p>
     * 因为PolarX如果长时间没有数据写入，DN节点上的Binlog文件会被清理掉并上传到OSS，那么，逻辑Binlog文件中最新的tso可能也已经被清理掉了
     * 如果Dumper发生重启，会导致上游Task搜不到位点
     */
    private boolean tryWriteHeartBeat() throws IOException {
        long seconds = CommonUtils.getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS);
        if (seconds % DynamicApplicationVersionConfig.getLong(ConfigKeys.HEARTBEAT_FLUSH_INTERVAL) == 0) {
            writeTso();
            if (logger.isDebugEnabled()) {
                logger.debug("Write a heartbeat tso {} to binlog file {}.", currentToken.getTso(),
                    binlogFile.getFileName());
            }
            return true;
        }
        return false;
    }

    /**
     * 此方法只负责文件的准备，如果是一个新建文件，不进行文件头和format_desc的初始化
     */
    private void buildBinlogFile() throws IOException {
        File file = logFileManager.getMaxBinlogFile();
        if (file == null) {
            file = logFileManager.createFirstLogFile();
            binlogFile = new BinlogFile(file, MODE, writeBufferSize, seekBufferSize);
            startTso = "";
        } else {
            BinlogFile.SeekResult seekResult;
            List<File> files = logFileManager.getAllLogFilesOrdered();
            int count = files.size();

            // 从最后一个文件，尝试第一次获取startTso
            File maxFile = files.get(count - 1);
            binlogFile = new BinlogFile(maxFile, MODE, writeBufferSize, seekBufferSize);
            seekResult = binlogFile.seekLastTso();

            // 如果从最后一个文件没有获取到startTso，尝试从倒数第二个文件，进行第二次获取
            if (StringUtils.isBlank(seekResult.getLastTso())) {
                if (count > 1) {
                    binlogFile.close();// 对上一个文件，先执行一下关闭
                    binlogFile = new BinlogFile(files.get(count - 2), MODE, writeBufferSize, seekBufferSize);
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
                binlogFile = new BinlogFile(max, MODE, writeBufferSize, seekBufferSize);
            } else {
                // 如果从最后一个文件找到了tso，则需要判断一下文件的状态，是否需要rotate
                if (seekResult.getLastEventType() == LogEvent.ROTATE_EVENT) {
                    checkRotate(seekResult.getLastEventTimestamp(), true);
                } else if (seekResult.getLastEventType() == LogEvent.ROWS_QUERY_LOG_EVENT) {
                    checkRotate(seekResult.getLastEventTimestamp(), false);
                }
            }

            startTso = seekResult.getLastTso();
        }

        logger.info("start tso is :[" + startTso + "]");
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

    private void writeBegin(long serverId) throws IOException {
        Pair<byte[], Integer> begin = makeBegin(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS),
            currentToken.getBeginSchema(), serverId);
        binlogFile.writeEvent(begin.getLeft(), 0, begin.getRight(), true);

        if (logger.isDebugEnabled()) {
            logger.debug("receive a txn，token is :" + currentToken);
        }
    }

    private void writeDml(List<TxnItem> itemsList) throws IOException {
        for (TxnItem txnItem : itemsList) {
            final byte[] data = txnItem.getPayload().toByteArray();
            EventGenerator.updateTimeStamp(data,
                CommonUtils.getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS));
            binlogFile.writeEvent(data, 0, data.length, true);
            Metrics.get().incrementTotalWriteDmlEventCount();
        }
    }

    private void writeCommit(long serverId) throws IOException {
        final Pair<byte[], Integer> commit =
            makeCommit(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS), serverId);
        binlogFile.writeEvent(commit.getLeft(), 0, commit.getRight(), true);

        writeTso();
    }

    private void writeDdl(byte[] data) throws IOException {
        EventGenerator.updateTimeStamp(data, CommonUtils.getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS));
//        EventGenerator.updateServerId(data);
        binlogFile.writeEvent(data, 0, data.length, true);

        writeTso();
        Metrics.get().incrementTotalWriteDdlEventCount();
    }

    private void writePrivateDdl(byte[] data) throws IOException {
        String ddlSql = new String(data);
        ddlSql = MarkType.PRIVATE_DDL + "::" + ddlSql;

        final Pair<byte[], Integer> markEvent =
            makeMarkEvent(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS), ddlSql);
        binlogFile.writeEvent(markEvent.getLeft(), 0, markEvent.getRight(), true);

        Metrics.get().incrementTotalWriteDdlEventCount();
    }

    private void writeTso() throws IOException {
        String tso = MarkType.CTS + "::" + currentToken.getTso();
        final Pair<byte[], Integer> tsoEvent =
            makeMarkEvent(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS), tso);
        binlogFile.writeEvent(tsoEvent.getLeft(), 0, tsoEvent.getRight(), true);
    }

    private void writeConfigChangeEvent() throws IOException {
        String tso = MarkType.CTS + "::" + currentToken.getTso() + "::" + MetaCoopCommandEnum.ConfigChange;
        final Pair<byte[], Integer> tsoEvent =
            makeMarkEvent(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS), tso);
        binlogFile.writeEvent(tsoEvent.getLeft(), 0, tsoEvent.getRight(), true);
        DynamicApplicationVersionConfig.setConfigByTso(currentToken.getTso());
    }

    private boolean checkRotate(long timestamp, boolean rotateAlreadyExist) throws IOException {
        if (rotateAlreadyExist || binlogFile.writeSize() >= binlogFileSize) {

            // 如果rotateAlreadyExist为true，说明是上次成功写入rotate后，但没来的及创建新文件的情况
            if (!rotateAlreadyExist) {
                Pair<byte[], Integer> rotateEvent = EventGenerator
                    .makeRotate(timestamp, logFileManager.nextBinlogFileName(binlogFile.getFileName()), false);
                binlogFile.writeEvent(rotateEvent.getLeft(), 0, rotateEvent.getRight(), true);
                binlogFile.close();
            }

            // reset binlog file
            String oldFileName = binlogFile.getFileName();
            File newFile = logFileManager.rotateFile(binlogFile.getFile());
            binlogFile = new BinlogFile(newFile, MODE, writeBufferSize, seekBufferSize);
            logger.info("Binlog file rotate from {} to {}", oldFileName, newFile.getName());
            return true;
        }
        return false;
    }

    private void dryRun(TxnMessage message) {
        switch (message.getType()) {
        case TAG:
            if (message.getTxnTag().getTxnMergedToken().getType() == TxnType.META_DDL) {
                Metrics.get().incrementTotalWriteDdlEventCount();
            }
            break;
        case WHOLE:
            Metrics.get().setLatestDelayTimeOnReceive(calcDelayTime(message.getTxnBegin().getTxnMergedToken()));
            Metrics.get().incrementTotalWriteEventCount();//模拟begin
            for (int i = 0; i < message.getTxnData().getTxnItemsCount(); i++) {
                Metrics.get().incrementTotalWriteDmlEventCount();
                Metrics.get().incrementTotalWriteEventCount();
            }
            Metrics.get().incrementTotalWriteEventCount();//模拟end
            Metrics.get().incrementTotalWriteTxnCount();
            break;
        case BEGIN:
            Metrics.get().setLatestDelayTimeOnReceive(calcDelayTime(message.getTxnBegin().getTxnMergedToken()));
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

    private void recordStorageHistory(String tsoParam, String instructionIdParam, List<String> storageList)
        throws InterruptedException {
        if (storageList == null || storageList.isEmpty()) {
            throw new PolardbxException("Meta scale storage list can`t be null");
        }
        waitOriginTsoReady();

        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        transactionTemplate.execute((o) -> {
            // 幂等判断
            // tso记录到binlog文件和记录到binlog_storage_history表是非原子操作，需要做幂等判断
            // instructionId也是不能重复的，正常情况下tso和instructionId是一对一的关系，但可能出现bug
            // 比如：https://yuque.antfin-inc.com/jingwei3/knddog/uxpbzq，所以此处查询要更严谨一些，where条件也要包含instructionId
            List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(
                s -> s.where(tso, isEqualTo(tsoParam))
                    .or(instructionId, isEqualTo(instructionIdParam)));
            if (storageHistoryInfos.isEmpty()) {
                boolean repaired = tryRepairStorageList(instructionIdParam, storageList);
                StorageContent content = new StorageContent();
                content.setStorageInstIds(storageList);
                content.setRepaired(repaired);

                StorageHistoryInfo info = new StorageHistoryInfo();
                info.setStatus(0);
                info.setTso(tsoParam);
                info.setStorageContent(GSON.toJson(content));
                info.setInstructionId(instructionIdParam);
                storageHistoryMapper.insert(info);
                logger.info("record storage history : " + GSON.toJson(info));
            } else {
                logger.info("storage history with tso {} or instruction id {} is already exist, ignored.", tsoParam,
                    instructionIdParam);
            }
            return null;
        });
    }

    private void recordMetaEnvConfigHistory(final String tso, EnvConfigChangeInfo envConfigChangeInfo)
        throws InterruptedException {
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

    private boolean tryRepairStorageList(String instructionIdParam, List<String> storageList) {
        BinlogPolarxCommandMapper commandMapper = SpringContextHolder.getObject(BinlogPolarxCommandMapper.class);
        StorageInfoMapper storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        Optional<BinlogPolarxCommand> optional = commandMapper
            .selectOne(s -> s.where(BinlogPolarxCommandDynamicSqlSupport.cmdId, isEqualTo(instructionIdParam)));

        if (optional.isPresent()) {
            BinlogPolarxCommand command = optional.get();
            if ("ADD_STORAGE".equals(command.getCmdType())) {
                logger.info("storage list before trying to repair is " + storageList);
                List<StorageInfo> storageInfosInDb = storageInfoMapper.select(c ->
                    c.where(instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                        .and(status, isNotEqualTo(2))//0:storage ready, 1:prepare offline, 2:storage offline
                        .and(gmtCreated, isLessThanOrEqualTo(command.getGmtCreated()))
                        .orderBy(id));
                Set<String> storageIdsInDb = storageInfosInDb.stream().collect(
                    Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1, (s1, s2) -> s1)).values().stream()
                    .map(StorageInfo::getStorageInstId)
                    .collect(Collectors.toSet());

                boolean repaired = false;
                for (String id : storageIdsInDb) {
                    if (!storageList.contains(id)) {
                        logger.warn("storage inst id {} is not exist in instruction storage list.", id);
                        storageList.add(id);
                        repaired = true;
                    }
                }
                logger.info("storage list after trying to repair is " + storageList);
                return repaired;
            }
        } else {
            logger.error("can`t find the polarx command record for instruction-id " + instructionIdParam);
        }
        return false;
    }

    private void tryFlush(boolean forceFlush) throws IOException {
        if (forceFlush) {
            updateCursor();
        } else if (System.currentTimeMillis() - binlogFile.lastFlushTime() >= flushInterval) {
            updateCursor();
        }
        boolean needRotate = checkRotate(getTsoPhysicalTime(currentToken.getTso(), TimeUnit.SECONDS), false);
        if (needRotate) {
            tryWriteFileHeader();
        }
    }

    private void updateCursor() throws IOException {
        binlogFile.flush();
        Cursor cursor = new Cursor(binlogFile.getFileName(), binlogFile.position());
        logFileManager.setLatestFileCursor(cursor);
        if (logger.isDebugEnabled()) {
            logger.debug("cursor is updated to " + cursor);
        }
    }

    private long calcDelayTime(TxnMergedToken token) {
        long delayTime = System.currentTimeMillis() - getTsoPhysicalTime(token.getTso(), TimeUnit.MILLISECONDS);
        if (token.getType() == TxnType.DML) {
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

    // 兼容一下之前的设计，之前没有StorageHistory机制
    // 虽然TopologyWatcher会为之前已经生成的TaskConfig进行"增补"操作，但和这里并没有同步机制，所以安全起见做一个判断，抛异常概率其实很小
    private void waitOriginTsoReady() throws InterruptedException {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);

        long start = System.currentTimeMillis();
        while (true) {
            List<StorageHistoryInfo> origStorageHistory =
                storageHistoryMapper.select(s -> s.where(tso, isEqualTo(TaskConfig.ORIGIN_TSO)));

            if (origStorageHistory.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                if ((System.currentTimeMillis() - start) > 10 * 1000) {
                    throw new PolardbxException("storage history with original tso is not ready.");
                }
                if (!running) {
                    throw new InterruptedException("wait interrupt");
                }
            } else {
                break;
            }
        }
    }

    private void waitTaskConfigReady(String startTso) throws InterruptedException {
        waitOriginTsoReady();
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
}
