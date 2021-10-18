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

package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.domain.StorageChangeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutputMergeSourceHandler implements LogEventHandler<Transaction> {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOutputMergeSourceHandler.class);
    private static final Logger transactionLogger = LoggerFactory.getLogger(
        "com.aliyun.polardbx.binlog.extractor.transaction");
    private final MergeSource mergeSource;
    private Storage storage;
    private boolean running;

    public DefaultOutputMergeSourceHandler(MergeSource mergeSource, Storage storage) {
        this.mergeSource = mergeSource;
        this.storage = storage;
    }

    @Override
    public void handle(Transaction transaction) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.error("output transaction : " + transaction.toString());
        }

        pushToken(transaction);
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_EXTRACTOR_RECORD_TRANSLOG)) {
            transactionLogger.info(transaction.getVirtualTSO() + ":" + getTransactionType(transaction) + ":"
                + transaction.getTransactionId() + ":pos[" + transaction.getBinlogFileName() + ":"
                + transaction.getStartLogPos() + "]");
        }
    }

    private String getTransactionType(Transaction transaction) {
        if (transaction.isDDL()) {
            return "ddl";
        }
        if (transaction.isStorageChangeCommand()) {
            return "storageChangeCommand";
        }
        if (transaction.isHeartbeat()) {
            return "heartbeat";
        }
        if (transaction.isDescriptionEvent()) {
            return "description";
        }
        return "data";
    }

    private void pushToken(Transaction transaction) throws Exception {
        TxnKey tk = transaction.getBufferKey();
        String txnId = null;
        if (tk != null) {
            txnId = tk.getTxnId();
        } else {
            txnId = transaction.getTransactionId() + "";
        }

        TxnToken.Builder txnTokenBuilder = TxnToken.newBuilder()
            .setPartitionId(transaction.getPartitionId())
            .setTso(transaction.getVirtualTSO())
            .setTxnSize(transaction.getEventCount())
            .setTxnId(txnId)
            .setType(TxnType.DML)
            .setBeginSchema(transaction.getStartSchema())
            .setTsoTransaction(transaction.isTsoTransaction())
            .setXaTxn(transaction.isXa())
            .setSnapshotSeq(transaction.getSnapshotSeq() == null ? -1 : transaction.getSnapshotSeq());

        if (transaction.isDescriptionEvent()) {
            txnTokenBuilder.setType(TxnType.FORMAT_DESC);
            txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDescriptionLogEventData()));
            logger.info("output format description  : " + transaction.getPartitionId() + " for : "
                + transaction.getVirtualTSO());
        }

        if (transaction.isHeartbeat()) {
            txnTokenBuilder.setType(TxnType.META_HEARTBEAT);
        }

        if (transaction.isDDL()) {
            txnTokenBuilder.setPayload(ByteString.copyFrom(transaction.getDdlEvent().getData()));
            txnTokenBuilder.setType(TxnType.META_DDL);
            logger.info("output logic ddl : " + transaction.getDdlEvent().getDdlRecord().getDdlSql() + " for : "
                + transaction.getVirtualTSO());
        }

        if (transaction.isCDCStartCommand()) {
            transaction.release();
            return;
        }

        if (transaction.isStorageChangeCommand()) {
            Gson gson = new Gson().newBuilder().create();
            StorageChangeInfo changeInfo = new StorageChangeInfo();
            changeInfo.setInstructionId(transaction.getInstructionId());
            changeInfo.setStorageChangeEntity(gson.fromJson(transaction.getInstructionContent(),
                StorageChangeInfo.StorageChangeEntity.class));
            txnTokenBuilder.setPayload(ByteString.copyFrom(gson.toJson(changeInfo), "utf8"));
            txnTokenBuilder.setType(TxnType.META_SCALE);
            logger.info("output logic meta scale : " + transaction.getInstructionContent() + " for : "
                + transaction.getVirtualTSO());
        }

        transaction.markBufferComplete();

        do {
            try {
                mergeSource.push(txnTokenBuilder.build(), false, 100L);
                transaction.release();
                break;
            } catch (TimeoutException e) {

            } catch (PolardbxException e) {
                throw new PolardbxException(
                    "txn keys " + transaction.getTransactionId() + " party " + transaction.getPartitionId(), e);
            }
        } while (running);
    }

    @Override
    public void onStart(HandlerContext context) {
        logger.info("start output handler !");
        this.running = true;
    }

    @Override
    public void onStop() {
        logger.info("stop output handler !");
        this.running = false;
    }
}
