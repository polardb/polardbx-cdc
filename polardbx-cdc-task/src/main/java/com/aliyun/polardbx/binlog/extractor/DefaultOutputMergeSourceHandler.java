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
package com.aliyun.polardbx.binlog.extractor;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.metadata.DdlScope;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN_MODE;
import static com.aliyun.polardbx.binlog.util.TxnTokenUtil.cleanTxnBuffer4Token;

@Slf4j
public class DefaultOutputMergeSourceHandler implements LogEventHandler<Transaction> {

    private static final Logger transactionLogger = LoggerFactory.getLogger(
        "com.aliyun.polardbx.binlog.extractor.transaction");
    private final MergeSource mergeSource;
    private final Storage storage;
    private final boolean dryRun;
    private final int dryRunMode;

    private HandlerContext context;
    private boolean running;

    public DefaultOutputMergeSourceHandler(MergeSource mergeSource, Storage storage) {
        this.mergeSource = mergeSource;
        this.storage = storage;
        this.dryRun = DynamicApplicationConfig.getBoolean(TASK_MERGE_DRY_RUN);
        this.dryRunMode = DynamicApplicationConfig.getInt(TASK_MERGE_DRY_RUN_MODE);
    }

    @Override
    public void handle(Transaction transaction) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("output transaction : " + transaction.toString());
        }

        pushToken(transaction);
        logTransAudit(transaction);
    }

    private void logTransAudit(Transaction transaction) {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_LOG_TRANS)) {
            transactionLogger.info(transaction.toString());
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACT_LOG_TRANS_DETAIL)) {
                Iterator<TxnItemRef> iterator = transaction.iterator();
                if (iterator != null) {
                    log.info("================== Detail Info Begin ================== ");
                    while (iterator.hasNext()) {
                        TxnItemRef ref = iterator.next();
                        transactionLogger.info("traceId = " + ref.getTraceId() + ", eventType = " + ref.getEventType()
                            + ", rowsQuery = " + ref.getEventData().getRowsQuery());
                    }
                    log.info("================== Detail Info End   ================== ");
                }
            }
        }
    }

    private void pushToken(Transaction transaction) throws Exception {
        if (transaction.isCDCStartCommand()) {
            transaction.release();
            return;
        }

        TxnKey tk = transaction.getTxnKey();
        long txnId;
        if (tk != null) {
            txnId = tk.getTxnId();
        } else {
            txnId = transaction.getTransactionId();
        }

        TxnToken.Builder txnTokenBuilder = TxnToken.newBuilder()
            .setPartitionId(transaction.getPartitionId())
            .setTso(transaction.getVirtualTsoStr())
            .setTxnSize(transaction.getEventCount())
            .setTxnId(txnId)
            .setType(TxnType.DML)
            .setSchema("")
            .setTsoTransaction(transaction.isTsoTransaction())
            .setXaTxn(transaction.isXa());

        if (transaction.isDescriptionEvent()) {
            process4FormatDescription(txnTokenBuilder, transaction);
        } else if (transaction.isHeartbeat()) {
            txnTokenBuilder.setType(TxnType.META_HEARTBEAT);
        } else if (transaction.isDDL()) {
            process4Ddl(txnTokenBuilder, transaction);
        } else if (transaction.isStorageChangeCommand()) {
            process4StorageChangeCommand(txnTokenBuilder, transaction);
        } else if (transaction.isEnvConfigChangeCommand()) {
            process4EnvConfigChangeCommand(txnTokenBuilder, transaction);
        } else if (transaction.isFlushLogCommand()) {
            process4FlushLogCommand(txnTokenBuilder, transaction);
        }

        if (transaction.getServerId() != null) {
            txnTokenBuilder.setServerId(Int64Value.of(transaction.getServerId()));
        }

        sendToDownstream(txnTokenBuilder, transaction);
        updateMetrics();
    }

    private void process4FormatDescription(TxnToken.Builder txnTokenBuilder, Transaction transaction) {
        txnTokenBuilder.setType(TxnType.FORMAT_DESC);
        txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDescriptionLogEventData()));
        log.info("output format description  : " + transaction.getPartitionId()
            + " for : " + transaction.getVirtualTsoStr());
    }

    private void process4Ddl(TxnToken.Builder txnTokenBuilder, Transaction transaction) {
        String schema = transaction.getDdlEvent().getDdlRecord().getSchemaName();
        String table = transaction.getDdlEvent().getDdlRecord().getTableName();

        txnTokenBuilder.setSchema(schema == null ? "" : schema);
        txnTokenBuilder.setTable(table == null ? "" : table);
        txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDdlEvent().getData()));
        txnTokenBuilder.setType(TxnType.META_DDL);
        txnTokenBuilder.setDdl(transaction.getDdlEvent().getDdlRecord().getDdlSql());

        DDLExtInfo extInfo = transaction.getDdlEvent().getDdlRecord().getExtInfo();
        if (extInfo != null) {
            txnTokenBuilder.setDdlScope(extInfo.getDdlScope());
        } else {
            txnTokenBuilder.setDdlScope(DdlScope.Schema.getValue());
        }

        log.info("output logic ddl : " + transaction.getDdlEvent().getDdlRecord().getDdlSql() +
            " for : " + transaction.getVirtualTsoStr());
    }

    private void process4StorageChangeCommand(TxnToken.Builder txnTokenBuilder, Transaction transaction)
        throws UnsupportedEncodingException {
        StorageChangeInfo changeInfo = new StorageChangeInfo();
        changeInfo.setInstructionId(transaction.getInstructionId());
        changeInfo.setStorageChangeEntity(JSONObject.parseObject(
            transaction.getInstructionContent(), StorageChangeInfo.StorageChangeEntity.class));

        txnTokenBuilder.setPayload(ByteString.copyFrom(JSONObject.toJSONString(changeInfo), "utf8"));
        txnTokenBuilder.setType(TxnType.META_SCALE);

        log.info("output logic meta scale : " + transaction.getInstructionContent() + " for : "
            + transaction.getVirtualTsoStr());
    }

    private void process4EnvConfigChangeCommand(TxnToken.Builder txnTokenBuilder, Transaction transaction)
        throws UnsupportedEncodingException {
        EnvConfigChangeInfo configChangeInfo = new EnvConfigChangeInfo();
        configChangeInfo.setInstructionId(transaction.getInstructionId());
        configChangeInfo.setContent(transaction.getInstructionContent());

        txnTokenBuilder.setPayload(ByteString.copyFrom(JSONObject.toJSONString(configChangeInfo), "utf8"));
        txnTokenBuilder.setType(TxnType.META_CONFIG_ENV_CHANGE);
        log.info("output logic meta config env change : " + transaction.getInstructionContent() + " for : "
            + transaction.getVirtualTsoStr());
    }

    private void process4FlushLogCommand(TxnToken.Builder txnTokenBuilder, Transaction transaction) {
        txnTokenBuilder.setType(TxnType.FLUSH_LOG);
        log.info("output logic command flush log for : " + transaction.getVirtualTsoStr());
    }

    private void sendToDownstream(TxnToken.Builder txnTokenBuilder, Transaction transaction)
        throws InterruptedException {
        transaction.markBufferComplete();

        do {
            try {
                if (dryRun && dryRunMode == 0) {
                    cleanTxnBuffer4Token(txnTokenBuilder.build(), storage);
                } else {
                    mergeSource.push(txnTokenBuilder.build(), false, 100L);
                }
                transaction.release();
                break;
            } catch (TimeoutException ignored) {
            } catch (PolardbxException e) {
                throw new PolardbxException(
                    "txn keys " + transaction.getTransactionId() + " party " + transaction.getPartitionId(), e);
            }
        } while (running);
    }

    private void updateMetrics() {
        try {
            ThreadRecorder recorder = context.getRuntimeContext().getThreadRecorder();
            recorder.setMergeSourceQueueSize(mergeSource.getQueuedSize());
            recorder.setMergeSourcePassCount(mergeSource.getPassCount());
            recorder.setMergeSourcePollCount(mergeSource.getPollCount());
        } catch (Throwable t) {
            log.error("record merge source queue size failed.", t);
        }
    }

    @Override
    public void onStart(HandlerContext context) {
        log.info("start output merge source handler !");
        this.context = context;
        this.running = true;
    }

    @Override
    public void onStop() {
        log.info("stop output merge source handler !");
        this.context = null;
        this.running = false;
    }
}
