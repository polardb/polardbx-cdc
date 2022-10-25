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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.log.VirtualTSO;
import com.aliyun.polardbx.binlog.metrics.ExtractorMetrics;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MinTSOFilter implements LogEventFilter<TransactionGroup> {

    private static final Logger logger = LoggerFactory.getLogger(MinTSOFilter.class);
    private VirtualTSO lastPushTso;
    private Transaction lastTransaction;
    private String currentFile;
    private volatile boolean running = false;
    private volatile boolean processingEvent = false;

    public MinTSOFilter(String lastPushTso) {
        if (!StringUtils.isEmpty(lastPushTso)) {
            this.lastPushTso = new VirtualTSO(lastPushTso);
        }
    }

    @Override
    public void handle(TransactionGroup handlerEvent, HandlerContext context) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("handle transaction group start ------------");
        }
        if (!running) {
            logger.warn("min tso not start ,but receive msg!");
            return;
        }

        List<Transaction> transactions = handlerEvent.getTransactionList();
        for (Transaction transaction : transactions) {
            doHandleTran(transaction, context);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("handle transaction group end -----------");
        }
    }

    /**
     * heartbeat 只要不重复则一直推送push到下游,单独计算tso，以保证下游推进和对其
     * 非 heartbeat :
     * 1. ddl 保障 tso 没有重复
     * 2. 非DDL 允许重复
     */
    private boolean isPass(Transaction transaction, HandlerContext context) {
        long cmp = transaction.getVirtualTSOModel().tso - lastPushTso.tso;
        if (cmp < 0) {
            // 已经正常执行了，就必须确保tso 递增
            if (processingEvent) {
                logger.error("detected disorderly transaction \r\n" + " current transaction info is " + transaction
                    + "\r\n" + " last transaction info is :" + lastTransaction);
                throw new PolardbxException("detected disorderly transaction，current tso is: "
                    + transaction.getVirtualTSO() + ", last tso is :" + lastPushTso);
            }
            logger.info(
                "filter:TSO:" + transaction.getVirtualTSO() + ":pos:[" + transaction
                    .getBinlogFileName() + ":" + transaction.getStartLogPos() + "]xid:" + transaction.getXid());
            return false;
        }

        return true;
    }

    private void doHandleTran(Transaction transaction, HandlerContext context) throws Exception {
        if (!transaction.isDescriptionEvent() && !isPass(transaction, context)) {
            transaction.release();
            return;
        }
        doOutput(transaction, context);
    }

    private void doOutput(Transaction transaction, HandlerContext context) throws Exception {
        ExtractorMetrics.get().metricEvent(transaction);
        context.doNext(transaction);
        if (transaction.isDescriptionEvent()) {
            transaction.release();
            return;
        }

        lastPushTso = transaction.getVirtualTSOModel();
        lastTransaction = transaction;
        currentFile = context.getRuntimeContext().getBinlogFile();

        if (!processingEvent) {
            logger.info("****--- ready to push event start with tso " + transaction.getVirtualTSO() + ", at position "
                + transaction.getBinlogFileName() + ":" + transaction.getStartLogPos());
        }
        processingEvent = true;
    }

    @Override
    public void onStart(HandlerContext context) {
        running = true;
        logger.info("start init min tso filter");
        if (!context.getRuntimeContext().isRecovery()) {
            BinlogPosition startPos = context.getRuntimeContext().getStartPosition();
            logger.info("start with tso : " + startPos.getRtso());
            lastPushTso = new VirtualTSO(startPos.getRtso());
            logger.info("is first start will init last push tso " + lastPushTso);
        }
    }

    @Override
    public void onStop() {
        running = false;
    }

    @Override
    public void onStartConsume(HandlerContext context) {

    }
}
