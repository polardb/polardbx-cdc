/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexDefinition;
import com.alibaba.polardbx.druid.sql.ast.SQLName;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.DrdsMoveDataBase;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.QueryLogFlags2Enum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.DdlEngineArchiveMapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.log.DDLEvent;
import com.aliyun.polardbx.binlog.extractor.log.DDLEventBuilder;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_APPLY_FROM_RECORD_FIRST;
import static com.aliyun.polardbx.binlog.cdc.meta.CreateDropTableWithExistFilter.shouldIgnore;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.processDdlSqlCharacters;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.tryRemoveAutoShardKey;
import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;
import static com.aliyun.polardbx.binlog.util.SQLUtils.reWriteWrongDdl;

public class LogicDDLHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogicDDLHandler.class);
    private final PolarDbXTableMetaManager tableMetaManager;
    private final EventAcceptFilter acceptFilter;
    private final DdlEngineArchiveMapper ddlEngineArchiveMapper;
    private final long instanceServerId;

    public LogicDDLHandler(long instanceServerId, EventAcceptFilter acceptFilter,
                           PolarDbXTableMetaManager tableMetaManager) {
        this.instanceServerId = instanceServerId;
        this.acceptFilter = acceptFilter;
        this.tableMetaManager = tableMetaManager;
        this.ddlEngineArchiveMapper = SpringContextHolder.getObject(DdlEngineArchiveMapper.class);
    }

    public void processDDL(Transaction transaction, HandlerContext context) {
        try {
            long serverId = getInstanceServerId();
            if (transaction.getServerId() != null) {
                serverId = transaction.getServerId();
            }
            rebuildDDL(transaction, context, serverId);
        } catch (Exception e) {
            throw new PolardbxException(e);
        }
    }

    public long getInstanceServerId(){
        return instanceServerId;
    }

    public void tryReplaceEventDataBefore(Transaction transaction){
        boolean useCdcDdlRecordFirst = DynamicApplicationConfig.getBoolean(META_BUILD_APPLY_FROM_RECORD_FIRST);
        boolean isLabEnv = DynamicApplicationConfig.getBoolean(IS_LAB_ENV);
        // prepare parameters
        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();

        if (useCdcDdlRecordFirst || isLabEnv) {
            logger.warn("begin to rebuild ddlRecord from __cdc_ddl_record__ with id {} db {}, and tso{} ",
                ddlRecord.getId(),
                ddlRecord.getSchemaName(),
                ddlEvent.getPosition().getRtso());
            ddlEvent = replaceLogicSqlFromCdcDdlRecord(ddlEvent);
            transaction.setDdlEvent(ddlEvent);
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
    }

    public void processDdlEventBefore(DDLEvent ddlEvent){
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
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
    }

    public String buildOutputDdlForPolarx(DDLRecord ddlRecord){
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        return ddlExtInfo != null && StringUtils.isNotBlank(ddlExtInfo.getActualOriginalSql())
            ? ddlExtInfo.getActualOriginalSql() : ddlRecord.getDdlSql();
    }

    public Set<String> findIndexes(String schema, String table){
        return tableMetaManager.findIndexes(schema, table);
    }

    /**
     * 解决create table like 与 ddlSql 不一致问题。
     * like 目标表可能和 ddlSql 不一致，需要处理。
     * @param ddlRecord
     * @param outputBinlogSql4Mysql
     */
    public void compareAndFixShardKey(DDLRecord ddlRecord, String outputBinlogSql4Mysql){
        SQLStatement st = com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement(outputBinlogSql4Mysql);
        if (st instanceof MySqlCreateTableStatement){
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) st;
            SQLExprTableSource tableSource = createTableStatement.getLike();
            if (tableSource != null){
                String likeTable = SQLUtils.normalize(tableSource.getTableName());
                String dbName = SQLUtils.normalize(tableSource.getSchema());
                if (StringUtils.isBlank(dbName)){
                    dbName = ddlRecord.getSchemaName();
                }
                if (StringUtils.isNotBlank(likeTable)){
                    Set<String> indexes = findIndexes(dbName, likeTable);
                    SQLStatement st1 = com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement(ddlRecord.getDdlSql());
                    boolean modify = false;
                    if (st1 instanceof MySqlCreateTableStatement){
                        MySqlCreateTableStatement createTableStatement1 = (MySqlCreateTableStatement) st1;
                        Iterator<SQLTableElement> it = createTableStatement1.getTableElementList().iterator();
                        while (it.hasNext()){
                            SQLTableElement el = it.next();
                            SQLIndexDefinition indexDefinition = getSqlIndexDefinition(el);
                            if (indexDefinition != null && indexDefinition.getName() != null){
                                SQLName sqlName = indexDefinition.getName();
                                // memoryTable apply逻辑里，会将ddl全部转换成小写，这里用小写匹配一下
                                String indexName = StringUtils.lowerCase(SQLUtils.normalize(sqlName.getSimpleName()));
                                if (!indexes.contains(indexName)){
                                    logger.warn("detected index {} not exists in like table {}.", sqlName, dbName+"."+likeTable);
                                    if (StringUtils.contains(sqlName.getSimpleName(), "auto_shard_key")){
                                        it.remove();
                                        logger.warn("index {} not exists in like table {} , try remove it.", sqlName, dbName+"."+likeTable);
                                        modify = true;
                                    }
                                }
                            }
                        }
                    }
                    if (modify){
                        ddlRecord.setDdlSql(st1.toString());
                    }
                }
            }
        }
    }

    private static SQLIndexDefinition getSqlIndexDefinition(SQLTableElement el) {
        SQLIndexDefinition indexDefinition = null;
        if (el instanceof MySqlKey){
            MySqlKey tableIndex = (MySqlKey) el;
            indexDefinition = tableIndex.getIndexDefinition();
        }else if (el instanceof MySqlTableIndex){
            MySqlTableIndex tableIndex = (MySqlTableIndex) el;
            indexDefinition = tableIndex.getIndexDefinition();
        }
        return indexDefinition;
    }

    public String buildOutputDdlForMysql(DDLRecord ddlRecord){
        // prepare output binlog ddl sql

        String outputBinlogSql4Mysql = ddlRecord.getDdlSql();

        // 建表SQL使用用户侧输入的DDL，作为单机MySQL形态的DDL sql，不能用物理执行计划中的sql
        // 对于以/* //1/ */开头的建表SQL，属于创建影子表的范畴，内核会自动将源表名带上__test前缀，但MySQL并没有这个行为，所以不能用原始sql
        if (ddlRecord.getExtInfo() != null && ("CREATE_TABLE".equals(ddlRecord.getSqlKind()) ||
            BooleanUtils.isTrue(ddlRecord.getExtInfo().getForeignKeysDdl()))) {
            String actualSql = ddlRecord.getExtInfo().getActualOriginalSql();
            if (StringUtils.isNotBlank(actualSql) && !StringUtils.contains(actualSql, "/* //1/ */")) {
                outputBinlogSql4Mysql = actualSql;
                compareAndFixShardKey(ddlRecord, outputBinlogSql4Mysql);
            }
        }

        // removeAutoShardKey 逻辑从输出event前移到apply 前，解决shardKey必定会被remove掉的问题
        outputBinlogSql4Mysql = tryRemoveAutoShardKey(
            ddlRecord.getSchemaName(), ddlRecord.getTableName(), outputBinlogSql4Mysql,
            triple -> tableMetaManager.findIndexes(triple.getLeft(), triple.getMiddle()).stream().anyMatch(
                i -> StringUtils.equalsIgnoreCase(triple.getRight(), i)));
        return outputBinlogSql4Mysql;
    }

    public void rebuildDdlForApply(Transaction transaction, String dbCharset, String tbCollation){
        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        // apply logic ddl sql
        String ddlSql4Apply = ddlRecord.getDdlSql().trim();
        logger.info("begin to apply logic ddl : " + ddlSql4Apply + ", tso : " + transaction.getVirtualTsoStr()
            + " ddl_record_id : " + ddlRecord.getId());
        ddlSql4Apply = processDdlSqlCharacters(ddlRecord.getTableName(), ddlSql4Apply, dbCharset, tbCollation);
        ddlRecord.setDdlSql(ddlSql4Apply);

        boolean isGSI = ddlExtInfo != null && ddlRecord.getExtInfo().isGsi();
        boolean isCCI = ddlExtInfo != null && ddlRecord.getExtInfo().isCci();

        if (isGSI) {
            ddlEvent.setVisibleToMysql(false);
            if (ddlExtInfo.isOldVersionOriginalSql()) {
                //@see https://aone.alibaba-inc.com/v2/project/860366/bug/51253282
                ddlRecord.setDdlSql("select 1");
                ddlRecord.getExtInfo().setCreateSql4PhyTable(null);
                ddlRecord.setMetaInfo(null);
            }
        }
        if (isCCI) {
            ddlEvent.setVisibleToMysql(false);
            logger.info("invisible cci for mysql : " + ddlRecord.getDdlSql());
        }
        logger.info("real apply logic ddl is : " + ddlSql4Apply + ", tso :"
            + transaction.getVirtualTsoStr() + " isGSI : " + isGSI);
        processCharactersForOriginalSql(ddlRecord, dbCharset, tbCollation);
    }

    public void doApplyAndRebuildFilter(Transaction transaction){
        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        tableMetaManager.applyLogic(ddlEvent.getPosition(), ddlRecord, transaction.getInstructionId());
        boolean isGSI = ddlExtInfo != null && ddlRecord.getExtInfo().isGsi();
        boolean isCCI = ddlExtInfo != null && ddlRecord.getExtInfo().isCci();

        if (!isGSI && !isCCI) {
            acceptFilter.rebuild();
        }
    }

    public void buildQueryLogEvent(Transaction transaction, HandlerContext context, String outputBinlogSql4Mysql, String outputBinlogSql4PolarX,
                                   long serverId, String dbCharset, String tbCollation) throws Exception {
        DDLEvent ddlEvent = transaction.getDdlEvent();
        // 构造输出到全局binlog的ddl event
        if (!ddlEvent.isVisible()) {
            return;
        }
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
        DDLExtInfo ddlExtInfo = ddlRecord.getExtInfo();
        RuntimeContext runtimeContext = context.getRuntimeContext();
        ServerCharactorSet serverCharactorSet = runtimeContext.getServerCharactorSet();
        Integer clientCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetClient());
        Integer connectionCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetConnection());
        Integer serverCharsetId = CharsetConversion.getCharsetId(serverCharactorSet.getCharacterSetServer());
        if (clientCharsetId == null || connectionCharsetId == null || serverCharsetId == null){
            throw new PolardbxException("buildQueryLogEvent failed, charset is null, clientCharsetId : " + clientCharsetId
                + ", connectionCharsetId : " + connectionCharsetId + ", serverCharsetId : " + serverCharsetId);
        }
        String sqlMode = null;
        String flags2 = null;
        boolean isCCI = false;
        Map<String, Object> polarxVariables = null;
        if (ddlExtInfo != null) {
            sqlMode = ddlExtInfo.getSqlMode();
            flags2 = ddlExtInfo.getFlags2();
            polarxVariables = ddlExtInfo.getPolarxVariables();
            isCCI = ddlExtInfo.isCci();
        }
        if (sqlMode == null || StringUtils.equalsIgnoreCase(sqlMode, "null")) {
            sqlMode = runtimeContext.getSqlMode();
        }
        long sqlModeCode = SqlModeUtil.modesValue(sqlMode);

        String outputDdlSql = DDLConverter.buildDdlEventSql(
            ddlRecord.getTableName(),
            ddlEvent.isVisibleToPolardbX() ? outputBinlogSql4PolarX : null,
            dbCharset,
            tbCollation,
            transaction.getVirtualTsoStr(),
            ddlEvent.isVisibleToMysql() ? outputBinlogSql4Mysql : null,
            ddlRecord.getDdlSql(),
            isCCI,
            polarxVariables);
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

    public void rebuildDDL(Transaction transaction, HandlerContext context, Long serverId) throws Exception {
        logger.info("begin to build ddl event, ddl record is  " + transaction.getDdlEvent().getDdlRecord()
            + " , tso is " + transaction.getVirtualTsoStr());

        // 尝试使用开关控制替换binlog中的ddl语句
        tryReplaceEventDataBefore(transaction);

        // 在apply meta 和 输出 ddl之前， 处理一下不兼容的边界场景
        processDdlEventBefore(transaction.getDdlEvent());

        DDLEvent ddlEvent = transaction.getDdlEvent();
        DDLRecord ddlRecord = ddlEvent.getDdlRecord();
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

        // 构造输出到全局binlog的polarx ddl
        String outputBinlogSql4PolarX = buildOutputDdlForPolarx(transaction.getDdlEvent().getDdlRecord());
        // 构造输出到全局binlog的mysql ddl
        String outputBinlogSql4Mysql = buildOutputDdlForMysql(transaction.getDdlEvent().getDdlRecord());
        // 在apply 前，补全ddl charset 和 collation， 处理cci 和 gsi
        rebuildDdlForApply(transaction, dbCharset, tbCollation);

        doApplyAndRebuildFilter(transaction);

        buildQueryLogEvent(transaction, context, outputBinlogSql4Mysql, outputBinlogSql4PolarX, serverId, dbCharset, tbCollation);
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

    public void tryRewriteMoveDataBaseSql(DDLEvent ddlEvent, DDLRecord ddlRecord) {
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

    public String tryRewriteDropTableSql(String schema, String tableName, String ddl) {
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

    public String tryRewriteTruncateSql(String tableName, String ddl) {
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
}
