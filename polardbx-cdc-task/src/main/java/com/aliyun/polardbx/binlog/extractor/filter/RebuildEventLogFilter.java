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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsMoveDataBase;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.QueryLogFlags2Enum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.DdlEngineArchiveMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.EventReformater;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.ReformatContext;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.QueryEventReformator;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.RowEventReformator;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat.TableMapEventReformator;
import com.aliyun.polardbx.binlog.extractor.log.DDLEvent;
import com.aliyun.polardbx.binlog.extractor.log.DDLEventBuilder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.extractor.log.VirtualTSO;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.util.DirectByteOutput;
import com.aliyun.polardbx.binlog.util.LabEventType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_APPLY_FROM_RECORD_FIRST;
import static com.aliyun.polardbx.binlog.cdc.meta.CreateDropTableWithExistFilter.shouldIgnore;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.processDdlSqlCharacters;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.tryRemoveAutoShardKey;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.binlog.util.SQLUtils.reWriteWrongDdl;

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
    private final DdlEngineArchiveMapper ddlEngineArchiveMapper;
    private long injectErrorTimestamp = -1;

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
        ddlEngineArchiveMapper = SpringContextHolder.getObject(DdlEngineArchiveMapper.class);
        new QueryEventReformator(tableMetaManager).register(reformaterMap);
        new RowEventReformator(binlogx, tableMetaManager).register(reformaterMap);
        new TableMapEventReformator(tableMetaManager).register(reformaterMap);

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
                logicDDLProcess(transaction, context);
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

    private void logicDDLProcess(Transaction transaction, HandlerContext context) {
        try {
            long serverId = instanceServerId;
            if (transaction.getServerId() != null) {
                serverId = transaction.getServerId();
            }
            rebuildDDL(transaction, context, serverId);
        } catch (Exception e) {
            throw new PolardbxException(e);
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

    private void rebuildDDL(Transaction transaction, HandlerContext context, Long serverId) throws Exception {
        logger.info("begin to build ddl event, ddl record is  " + transaction.getDdlEvent().getDdlRecord()
            + " , tso is " + transaction.getVirtualTsoStr());

        // prepare parameters
        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();

        boolean useCdcDdlRecordFirst = DynamicApplicationConfig.getBoolean(META_BUILD_APPLY_FROM_RECORD_FIRST);
        boolean isLabEnv = DynamicApplicationConfig.getBoolean(IS_LAB_ENV);

        if (useCdcDdlRecordFirst || isLabEnv) {
            logger.warn("begin to rebuild ddlRecord from __cdc_ddl_record__ with id {} db {}, and tso{} ",
                ddlRecord.getId(),
                ddlRecord.getSchemaName(),
                ddlEvent.getPosition().getRtso());
            ddlEvent = replaceLogicSqlFromCdcDdlRecord(ddlEvent);
            ddlRecord = ddlEvent.getDdlRecord();
            transaction.setDdlEvent(ddlEvent);

        }

        RuntimeContext runtimeContext = context.getRuntimeContext();
        ServerCharactorSet serverCharactorSet = runtimeContext.getServerCharactorSet();
        Integer clientCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetClient());
        Integer connectionCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetConnection());
        Integer serverCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetServer());
        TopologyRecord topologyRecord = JSONObject.parseObject(ddlRecord.getMetaInfo(), TopologyRecord.class);
        String dbCharset = null;
        String tbCollation = null;
        if (topologyRecord != null) {
            LogicMetaTopology.LogicTableMetaTopology tableMetas = topologyRecord.getLogicTableMeta();
            if (tableMetas != null) {
                tbCollation = tableMetas.getTableCollation();
            }
            LogicMetaTopology.LogicDbTopology dbTopology = topologyRecord.getLogicDbMeta();
            if (dbTopology != null) {
                dbCharset = dbTopology.getCharset();
            }
        }

        // 用来处理一些异常情况，比如打标sql有问题，或cdc识别不了打标sql，都可以通过该方式进行容错处理
        boolean useHistoryTableFirst = DynamicApplicationConfig.getBoolean(META_BUILD_APPLY_FROM_HISTORY_FIRST);
        if (useHistoryTableFirst) {
            logger.warn("begin to get sql from history table with db {}, and tso{} ", ddlRecord.getSchemaName(),
                ddlEvent.getPosition().getRtso());
            String sql = getLogicSqlFromHistoryTable(ddlRecord.getSchemaName(), ddlEvent.getPosition().getRtso());
            if (StringUtils.isNotBlank(sql)) {
                logger.warn("ddl sql in history table is " + sql);
                ddlRecord.setDdlSql(sql);
            } else {
                logger.warn("ddl sql is not existed in history table.");
            }
        }
        // try rewrite for move database sql, parse ddl 出错，会尝试重写一次ddl
        tryRewriteMoveDataBaseSql(ddlEvent, ddlRecord);

        // try rewrite for drop table sql
        ddlRecord.setDdlSql(tryRewriteDropTableSql(ddlRecord.getSchemaName(),
            ddlRecord.getTableName(), ddlRecord.getDdlSql()));

        // try rewrite for truncate table sql
        ddlRecord.setDdlSql(tryRewriteTruncateSql(ddlRecord.getTableName(), ddlRecord.getDdlSql()));

        // try ignore create table or drop table sql with exists
        if (shouldIgnore(ddlRecord.getDdlSql(), ddlRecord.getId(), ddlRecord.getJobId(), ddlRecord.getExtInfo())) {
            ddlEvent.setVisible(false);
        }

        // prepare output binlog ddl sql
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        String outputBinlogSql4PolarX = ddlExtInfo != null && StringUtils.isNotBlank(ddlExtInfo.getActualOriginalSql())
            ? ddlExtInfo.getActualOriginalSql() : ddlRecord.getDdlSql();
        String outputBinlogSql4Mysql = ddlRecord.getDdlSql();

        // 建表SQL使用用户侧输入的DDL，作为单机MySQL形态的DDL sql，不能用物理执行计划中的sql
        // 对于以/* //1/ */开头的建表SQL，属于创建影子表的范畴，内核会自动将源表名带上__test前缀，但MySQL并没有这个行为，所以不能用原始sql
        if (ddlRecord.getExtInfo() != null && ("CREATE_TABLE".equals(ddlRecord.getSqlKind()) ||
            BooleanUtils.isTrue(ddlRecord.getExtInfo().getForeignKeysDdl()))) {
            String actualSql = ddlRecord.getExtInfo().getActualOriginalSql();
            if (StringUtils.isNotBlank(actualSql) && !StringUtils.contains(actualSql, "/* //1/ */")) {
                outputBinlogSql4Mysql = actualSql;
            }
        }

        // apply logic ddl sql
        String ddlSql4Apply = ddlRecord.getDdlSql().trim();
        logger.info("begin to apply logic ddl : " + ddlSql4Apply + ", tso : " + transaction.getVirtualTsoStr()
            + " ddl_record_id : " + ddlRecord.getId());
        ddlSql4Apply = processDdlSqlCharacters(ddlRecord.getTableName(), ddlSql4Apply, dbCharset, tbCollation);
        ddlRecord.setDdlSql(ddlSql4Apply);

        boolean isGSI = ddlExtInfo != null && ddlRecord.getExtInfo().isGsi();
        if (isGSI) {
            ddlEvent.setVisibleToMysql(false);
            if (ddlExtInfo.isOldVersionOriginalSql()) {
                //@see https://aone.alibaba-inc.com/v2/project/860366/bug/51253282
                ddlRecord.setDdlSql("select 1");
                ddlRecord.getExtInfo().setCreateSql4PhyTable(null);
                ddlRecord.setMetaInfo(null);
            }
        }
        boolean isCCI = ddlExtInfo != null && ddlRecord.getExtInfo().isCci();
        if (isCCI) {
            ddlEvent.setVisibleToMysql(false);
            logger.info("invisible cci for mysql : " + ddlRecord.getDdlSql());
        }
        logger.info("real apply logic ddl is : " + ddlSql4Apply + ", tso :"
            + transaction.getVirtualTsoStr() + " isGSI : " + isGSI);
        processCharactersForOriginalSql(ddlRecord, dbCharset, tbCollation);
        tableMetaManager.applyLogic(ddlEvent.getPosition(), ddlRecord, transaction.getInstructionId());
        if (!isGSI && !isCCI) {
            acceptFilter.rebuild();
        }

        // 构造输出到全局binlog的ddl event
        if (ddlEvent.isVisible()) {
            String sqlMode = null;
            String flags2 = null;
            if (ddlRecord.getExtInfo() != null) {
                sqlMode = ddlRecord.getExtInfo().getSqlMode();
                flags2 = ddlRecord.getExtInfo().getFlags2();
            }
            if (sqlMode == null || StringUtils.equalsIgnoreCase(sqlMode, "null")) {
                sqlMode = runtimeContext.getSqlMode();
            }
            long sqlModeCode = SqlModeUtil.modesValue(sqlMode);

            outputBinlogSql4Mysql = tryRemoveAutoShardKey(
                ddlRecord.getSchemaName(), ddlRecord.getTableName(), outputBinlogSql4Mysql,
                triple -> tableMetaManager.findIndexes(triple.getLeft(), triple.getMiddle()).stream().anyMatch(
                    i -> StringUtils.equalsIgnoreCase(triple.getRight(), i)));

            String outputDdlSql = DDLConverter.buildDdlEventSql(
                ddlRecord.getTableName(),
                ddlEvent.isVisibleToPolardbX() ? outputBinlogSql4PolarX : null,
                dbCharset,
                tbCollation,
                transaction.getVirtualTsoStr(),
                ddlEvent.isVisibleToMysql() ? outputBinlogSql4Mysql : null,
                ddlRecord.getDdlSql(),
                isCCI,
                ddlExtInfo.getPolarxVariables());
            int ddlCostTime = ddlCostTime(ddlRecord);

            long flags2Value = 0;

            if (StringUtils.isNotBlank(flags2)) {
                flags2Value = QueryLogFlags2Enum.getFlags2Value(flags2);
            }

            ddlEvent.setQueryEventBuilder(new QueryEventBuilder(ddlRecord.getSchemaName(),
                outputDdlSql,
                clientCharsetId,
                connectionCharsetId,
                serverCharsetId,
                true,
                (int) ddlEvent.getPosition().getTimestamp(),
                serverId,
                sqlModeCode,
                ddlCostTime,
                flags2Value));
            ddlEvent.setCommitKey(outputDdlSql);
            ddlEvent.setData(ReformatContext.toByte(ddlEvent.getQueryEventBuilder()));
        }
    }

    private void processCharactersForOriginalSql(DDLRecord ddlRecord, String dbCharset, String tbCollation) {
        // 为original sql 附加character，保证当按照original sql进行apply时，tablemeta中包含charset信息
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        if (ddlExtInfo != null && StringUtils.isNotBlank(ddlExtInfo.getActualOriginalSql())) {
            ddlExtInfo.resetOriginalSql(processDdlSqlCharacters("",
                ddlExtInfo.getActualOriginalSql(), dbCharset, tbCollation));
        }
    }

    private int ddlCostTime(DDLRecord record) {
        if (record.getJobId() == null) {
            return 1;
        }
        Long costTime;
        try {
            costTime = ddlEngineArchiveMapper.selectDdlCost(record.getJobId());
            if (costTime == null) {
                costTime = ddlEngineArchiveMapper.selectArchiveDdlCost(record.getJobId());
            }
            if (costTime == null) {
                return 1;
            }
        } catch (Throwable e) {
            logger.error("query ddl cost time error", e);
            costTime = 1L;
        }

        return (int) Math.abs(TimeUnit.MILLISECONDS.toSeconds(costTime));
    }

    protected void tryRewriteMoveDataBaseSql(DDLEvent ddlEvent, DDLRecord ddlRecord) {
        try {
            SQLStatement stmt = parseSQLStatement(ddlRecord.getDdlSql());
            if (stmt instanceof DrdsMoveDataBase) {
                // don`t know why process like this, maybe some history reason, keep it
                ddlRecord.setDdlSql("select 1");
                ddlEvent.setVisible(false);
            }
        } catch (Throwable t) {
            logger.error("try rewrite move database sql error!", t);
            String newDdl = reWriteWrongDdl(ddlRecord.getDdlSql());
            if (newDdl != null) {
                String log = "rewrite sql : " + ddlRecord.getDdlSql() + " to : " + newDdl;
                logger.warn(log);
                LabEventManager.logEvent(LabEventType.EXCEPTION_RE_WRITE_DDL, log);
                ddlRecord.setDdlSql(newDdl);
                return;
            }
            throw t;
        }
    }

    private String getLogicSqlFromHistoryTable(String dbName, String tso) {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> list = metaJdbcTemplate.queryForList(
            "select ddl from binlog_logic_meta_history where tso = '" + tso + "' and db_name = '" + dbName + "'",
            String.class);
        return list.isEmpty() ? null : list.get(0);
    }

    private DDLEvent replaceLogicSqlFromCdcDdlRecord(DDLEvent ddlEvent) {
        DDLRecord record = ddlEvent.getDdlRecord();
        JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        boolean isLabEnv = DynamicApplicationConfig.getBoolean(IS_LAB_ENV);
        List<Map<String, Object>> searchRecordList;
        long start = System.currentTimeMillis();
        do {
            searchRecordList = polarxJdbcTemplate.queryForList(
                "select sql_kind, schema_name, table_name, meta_info, ddl_sql,visibility, ext from __cdc_ddl_record__ where id = "
                    + record.getId());

            if (CollectionUtils.isNotEmpty(searchRecordList)) {
                break;
            }
            // 此处报错原因是commit 动作是异步的，部分commit较快的dn 到此位置去查 __cdc_ddl_record__时 ，
            // 可能会因为没有全部提交导致查不到数据
            logger.warn("can not find ddl record from __cdc_ddl_record__, id : " + record.getId() + " will retry ");
            // 重试10s
            if (System.currentTimeMillis() - start > 10000) {
                throw new PolardbxException(
                    "can not find ddl record from __cdc_ddl_record__ after 10s , id : " + record.getId());
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new PolardbxException("can not find ddl record from __cdc_ddl_record__, id : " + record.getId(),
                    e);
            }
        } while (true);
        if (searchRecordList.size() != 1) {
            throw new PolardbxException("too many ddl records found from __cdc_ddl_record__, id : " + record.getId());
        }
        Map<String, Object> searchRecord = searchRecordList.get(0);
        String newDdlSql = (String) searchRecord.get("ddl_sql");
        String ext = (String) searchRecord.get("ext");
        String sqlKind = (String) searchRecord.get("sql_kind");
        String schemaName = (String) searchRecord.get("schema_name");
        String tableName = (String) searchRecord.get("table_name");
        String metaInfo = (String) searchRecord.get("meta_info");
        int visibility = ((Long) searchRecord.get("visibility")).intValue();

        DDLEvent newDDLEvent =
            DDLEventBuilder.build(record.getId() + "", record.getJobId() == null ? "" : record.getJobId() + "",
                schemaName,
                tableName, sqlKind,
                newDdlSql, visibility, ext, metaInfo);
        newDDLEvent.setPosition(ddlEvent.getPosition());
        // DDLEvent以及内部对象，都实现了@EqualsAndHashCode注解，所以直接比较即可
        if (!Objects.equals(newDDLEvent, ddlEvent)) {
            logger.info("replace ddl record from __cdc_ddl_record__ for id {}, db_name {} table_name {}",
                record.getId(),
                record.getSchemaName(), record.getTableName());
            if (isLabEnv) {
                throw new PolardbxException(
                    String.format("check ddl event consistency failed! , id %s, db_name %s table_name %s ",
                        record.getId() + "",
                        record.getSchemaName(), record.getTableName()));
            }
            return newDDLEvent;
        }
        return ddlEvent;
    }

    String tryRewriteDropTableSql(String schema, String tableName, String ddl) {
        try {
            SQLStatement stmt = parseSQLStatement(ddl);

            //fix https://work.aone.alibaba-inc.com/issue/36762424
            if (stmt instanceof SQLDropTableStatement) {
                SQLDropTableStatement dropTableStatement = (SQLDropTableStatement) stmt;
                if (dropTableStatement.getTableSources().size() > 1) {
                    Optional<SQLExprTableSource> optional = dropTableStatement.getTableSources().stream()
                        .filter(ts ->
                            tableName.equalsIgnoreCase(SQLUtils.normalizeNoTrim(ts.getTableName())) && (
                                StringUtils.isBlank(ts.getSchema())
                                    || schema.equalsIgnoreCase(SQLUtils.normalize(ts.getSchema())))
                        ).findFirst();
                    if (!optional.isPresent()) {
                        throw new PolardbxException(String.format("can`t find table %s in sql %s", tableName, ddl));
                    } else {
                        dropTableStatement.getTableSources().clear();
                        dropTableStatement.addTableSource(optional.get());
                        String newSql = dropTableStatement.toUnformattedString();
                        logger.info("rewrite drop table sql from {}, to {}", ddl, newSql);
                        return newSql;
                    }
                }
            }

            return ddl;
        } catch (Throwable t) {
            logger.error("try rewrite drop table sql failed. schema:{}, table:{}, sql:{}", schema, tableName, ddl);
            throw new PolardbxException("try rewrite drop table sql failed!!", t);
        }
    }

    String tryRewriteTruncateSql(String tableName, String ddl) {
        SQLStatement sqlStatement = parseSQLStatement(ddl);

        //fix https://aone.alibaba-inc.com/issue/46776374
        if (sqlStatement instanceof SQLTruncateStatement) {
            SQLTruncateStatement sqlTruncateStatement = (SQLTruncateStatement) sqlStatement;
            if (sqlTruncateStatement.getTableSources().size() == 1) {
                SQLExprTableSource source = sqlTruncateStatement.getTableSources().get(0);
                String sqlTable = SQLUtils.normalizeNoTrim(source.getTableName());
                if (StringUtils.equalsIgnoreCase("__test_" + sqlTable, tableName)) {
                    source.setSimpleName(tableName);
                    return sqlTruncateStatement.toUnformattedString();
                }
            }
        }
        return ddl;
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
