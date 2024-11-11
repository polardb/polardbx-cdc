/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTruncateStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.dao.DdlEngineArchiveMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.EventReformater;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.LogicDDLHandler;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.QueryEventReformator;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.RowEventReformator;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.TableMapEventReformator;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.log.VirtualTSO;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * @author chengjin.lyf on 2020/8/7 3:13 下午
 * @since 1.0.25
 */
public class RebuildEventLogFilter implements LogEventFilter<TransactionGroup> {

    private static final Logger logger = LoggerFactory.getLogger("rebuildEventLogger");
    private final LogDecoder logDecoder = new LogDecoder();
    private final PolarDbXTableMetaManager tableMetaManager;
    private final LogContext logContext;
    private final EventAcceptFilter acceptFilter;
    private final long instanceServerId;
    private FormatDescriptionLogEvent fde;
    private VirtualTSO baseVTSO;
    private final Map<Integer, EventReformater> reformaterMap = new HashMap<>();
    private long injectErrorTimestamp = -1;
    private final LogicDDLHandler logicDDLHandler;

    public RebuildEventLogFilter(long instanceServerId, EventAcceptFilter acceptFilter, boolean binlogx,
                                 PolarDbXTableMetaManager tableMetaManager) {
        this.instanceServerId = instanceServerId;
        this.acceptFilter = acceptFilter;
        this.tableMetaManager = tableMetaManager;
        logDecoder.handle(LogEvent.QUERY_EVENT);
        logDecoder.handle(LogEvent.UPDATE_ROWS_EVENT);
        logDecoder.handle(LogEvent.UPDATE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.WRITE_ROWS_EVENT);
        logDecoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.DELETE_ROWS_EVENT);
        logDecoder.handle(LogEvent.DELETE_ROWS_EVENT_V1);
        logDecoder.handle(LogEvent.TABLE_MAP_EVENT);
        logContext = new LogContext();
        logContext.setFormatDescription(fde);
        logContext.setLogPosition(new LogPosition(""));
        new QueryEventReformator(tableMetaManager).register(reformaterMap);
        new RowEventReformator(binlogx, tableMetaManager).register(reformaterMap);
        new TableMapEventReformator(tableMetaManager).register(reformaterMap);
        logicDDLHandler = new LogicDDLHandler(instanceServerId, acceptFilter, tableMetaManager);

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean reformat(TxnItemRef txnItemRef, LogEvent event, ReformatContext context, EventData eventData)
        throws Exception {
        int type = event.getHeader().getType();

        EventReformater reformater = reformaterMap.get(type);
        if (reformater == null) {
            return false;
        }
        if (!reformater.accept(event)) {
            return false;
        }
        return reformater.reformat(event, txnItemRef, context, eventData);
    }

    @Override
    public void handle(TransactionGroup event, HandlerContext context) throws Exception {

        RuntimeContext rc = context.getRuntimeContext();
        Iterator<Transaction> tranIt = event.getTransactionList().iterator();
        logContext.setServerCharactorSet(rc.getServerCharactorSet());
        ReformatContext reformatContext =
            new ReformatContext(rc.getDefaultDatabaseCharset(), rc.getServerCharactorSet().getCharacterSetServer(),
                rc.getLowerCaseTableNames(), rc.getStorageInstId());
        reformatContext.setBinlogFile(rc.getBinlogFile());

        while (tranIt.hasNext()) {
            Transaction transaction = tranIt.next();
            transaction.restoreEntity();

            if (transaction.needRevert()) {
                transaction.release();
                tranIt.remove();
                continue;
            }

            if (!transaction.isDescriptionEvent() &&
                baseVTSO != null &&
                transaction.getVirtualTSOModel().compareTo(baseVTSO) <= 0) {
                transaction.release();
                tranIt.remove();
                logger.info("ignore event for : " + transaction.getVirtualTsoStr());
                continue;
            }

            if (transaction.isMetadataBuildCommand()) {
                buildMetaData(transaction);
                transaction.release();
                tranIt.remove();
                continue;
            }

            if (transaction.isHeartbeat() || transaction.isInstructionCommand()) {
                if (!acceptFilter.acceptCdcSchema(transaction.getSourceCdcSchema())) {
                    transaction.release();
                    tranIt.remove();
                    continue;
                }
            }

            if (transaction.isDDL()) {
                logicDDLHandler.processDDL(transaction, context);
                transaction.release();
                if (!transaction.isVisibleDdl()) {
                    tranIt.remove();
                }
            }

            reformatEvent(transaction, reformatContext);

            if (!transaction.isValidTransaction()) {
                transaction.release();
                tranIt.remove();
            }
        }

        if (!event.isEmpty()) {
            context.doNext(event);
        }

        if (injectErrorTimestamp > 0 && injectErrorTimestamp < System.currentTimeMillis()) {
            System.exit(0);
        }
    }

    private void buildMetaData(Transaction transaction) {
        String metaContent = transaction.getInstructionContent();
        String cmdId = transaction.getInstructionId();
        BinlogPosition po = new BinlogPosition(transaction.getBinlogFileName(), transaction.getStartLogPos(), -1, -1);
        po.setRtso(transaction.getVirtualTsoStr());
        int affectRow = tableMetaManager.buildSnapshot(po, metaContent, cmdId);
        boolean injectError = DynamicApplicationConfig.getBoolean(ConfigKeys.META_BUILD_SNAPSHOT_ERROR_INJECT);
        if (injectError && affectRow == 1) {
            // 5~15s 后退出
            long now = System.currentTimeMillis();
            int randomSeconds = 5 + RandomUtils.nextInt(15);
            injectErrorTimestamp = now + TimeUnit.SECONDS.toMillis(randomSeconds);
            logger.info("error inject for buildMetaData , task will exit after " + randomSeconds + "s");
        }
    }

    private void reformatEvent(Transaction transaction, ReformatContext reformatContext) throws Exception {
        if (transaction.isDescriptionEvent()) {
            fde = transaction.getFdLogEvent();
            logContext.setFormatDescription(fde);
        }
        IteratorBuffer it = transaction.iterator();
        reformatContext.setIt(it);
        reformatContext.setVirtualTSO(transaction.getVirtualTsoStr());
        if (it != null) {
            boolean allRemove = true;
            while (it.hasNext()) {
                TxnItemRef tir = it.next();
                EventData eventData = tir.getEventData();
                byte[] bytes = DirectByteOutput.unsafeFetch(eventData.getPayload());
                LogEvent e = logDecoder.decode(new LogBuffer(bytes, 0, bytes.length), logContext);
                if (!acceptFilter.accept(e)) {
                    removeOneItem(tir, it);
                    continue;
                }

                Long serverId = instanceServerId;
                if (transaction.getServerId() != null) {
                    serverId = transaction.getServerId();
                }
                reformatContext.setServerId(serverId);
                if (!reformat(tir, e, reformatContext, eventData)) {
                    it.remove();
                    continue;
                }

                allRemove = false;
            }
            if (allRemove) {
                transaction.release();
            }
        }
    }

    private void removeOneItem(TxnItemRef tir, Iterator<TxnItemRef> it) {
        try {
            tir.delete();
            it.remove();
        } catch (RocksDBException e) {
            throw new PolardbxException("remove txn item ref failed!", e);
        }
    }

    @Override
    public void onStart(HandlerContext context) {
        context.getRuntimeContext().setServerId(instanceServerId);
        final String defaultCharset = context.getRuntimeContext().getDefaultDatabaseCharset();
        tableMetaManager.onStart(defaultCharset);
        this.acceptFilter.onStart(context);
        BinlogPosition startPos = context.getRuntimeContext().getStartPosition();
        logger.info("start with tso : " + startPos.getRtso());
        baseVTSO = new VirtualTSO(startPos.getRtso());
        logger.info("is first start will init skip base tso " + startPos.getRtso());
    }

    @Override
    public void onStop() {
        this.acceptFilter.onStop();
    }

    @Override
    public void onStartConsume(HandlerContext context) {
        final String defaultCharset = context.getRuntimeContext().getDefaultDatabaseCharset();
        tableMetaManager.onStart(defaultCharset);
        this.acceptFilter.onStartConsume(context);
    }
}
