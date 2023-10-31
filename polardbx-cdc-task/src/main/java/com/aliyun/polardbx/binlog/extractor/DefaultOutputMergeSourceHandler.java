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
import com.aliyun.polardbx.binlog.domain.EnvConfigChangeInfo;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN_MODE;
import static com.aliyun.polardbx.binlog.util.TxnTokenUtil.cleanTxnBuffer4Token;

public class DefaultOutputMergeSourceHandler implements LogEventHandler<Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOutputMergeSourceHandler.class);
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
        if (logger.isDebugEnabled()) {
            logger.debug("output transaction : " + transaction.toString());
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
                    logger.info("================== Detail Info Begin ================== ");
                    while (iterator.hasNext()) {
                        TxnItemRef ref = iterator.next();
                        transactionLogger.info("traceId = " + ref.getTraceId() + ", eventType = " + ref.getEventType()
                            + ", rowsQuery = " + ref.getEventData().getRowsQuery());
                    }
                    logger.info("================== Detail Info End   ================== ");
                }
            }
        }
    }

    private void pushToken(Transaction transaction) throws Exception {
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
            txnTokenBuilder.setType(TxnType.FORMAT_DESC);
            txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDescriptionLogEventData()));
            logger.info("output format description  : " + transaction.getPartitionId() + " for : "
                + transaction.getVirtualTsoStr());
        }

        if (transaction.isHeartbeat()) {
            txnTokenBuilder.setType(TxnType.META_HEARTBEAT);
        }

        if (transaction.isDDL()) {
            txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDdlEvent().getData()));
            txnTokenBuilder.setType(TxnType.META_DDL);
            String schema = transaction.getDdlEvent().getDdlRecord().getSchemaName();
            String table = transaction.getDdlEvent().getDdlRecord().getTableName();
            txnTokenBuilder.setSchema(schema == null ? "" : schema);
            txnTokenBuilder.setTable(table == null ? "" : table);
            txnTokenBuilder.setDdl(transaction.getDdlEvent().getDdlRecord().getDdlSql());
            logger.info("output logic ddl : " + transaction.getDdlEvent().getDdlRecord().getDdlSql() +
                " for : " + transaction.getVirtualTsoStr());
        }

        if (transaction.isCDCStartCommand()) {
            transaction.release();
            return;
        }

        if (transaction.isStorageChangeCommand()) {
            StorageChangeInfo changeInfo = new StorageChangeInfo();
            changeInfo.setInstructionId(transaction.getInstructionId());
            changeInfo.setStorageChangeEntity(JSONObject.parseObject(transaction.getInstructionContent(),
                StorageChangeInfo.StorageChangeEntity.class));
            txnTokenBuilder.setPayload(ByteString.copyFrom(JSONObject.toJSONString(changeInfo), "utf8"));
            txnTokenBuilder.setType(TxnType.META_SCALE);
            logger.info("output logic meta scale : " + transaction.getInstructionContent() + " for : "
                + transaction.getVirtualTsoStr());
        } else if (transaction.isEnvConfigChangeCommand()) {
            EnvConfigChangeInfo configChangeInfo = new EnvConfigChangeInfo();
            configChangeInfo.setInstructionId(transaction.getInstructionId());
            configChangeInfo.setContent(transaction.getInstructionContent());
            txnTokenBuilder.setPayload(ByteString.copyFrom(JSONObject.toJSONString(configChangeInfo), "utf8"));
            txnTokenBuilder.setType(TxnType.META_CONFIG_ENV_CHANGE);
            logger.info("output logic meta config env change : " + transaction.getInstructionContent() + " for : "
                + transaction.getVirtualTsoStr());
        } else if (transaction.isFlushLogCommand()) {
            txnTokenBuilder.setType(TxnType.FLUSH_LOG);
            logger.info("output logic command flush log for : "
                + transaction.getVirtualTsoStr());
        }

        if (transaction.getServerId() != null) {
            txnTokenBuilder.setServerId(Int64Value.of(transaction.getServerId()));
        }

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

        try {
            ThreadRecorder recorder = context.getRuntimeContext().getThreadRecorder();
            recorder.setMergeSourceQueueSize(mergeSource.getQueuedSize());
            recorder.setMergeSourcePassCount(mergeSource.getPassCount());
            recorder.setMergeSourcePollCount(mergeSource.getPollCount());
        } catch (Throwable t) {
            logger.error("record merge source queue size failed.", t);
        }
    }

    @Override
    public void onStart(HandlerContext context) {
        logger.info("start output handler !");
        this.context = context;
        this.running = true;
    }

    @Override
    public void onStop() {
        logger.info("stop output handler !");
        this.context = null;
        this.running = false;
    }
}
