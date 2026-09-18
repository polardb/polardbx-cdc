/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.full;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.TopologyManager;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.domain.po.RplDbFullPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.ReplicaMode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.filter.DataImportFilter;
import com.aliyun.polardbx.rpl.filter.ReplicaFilter;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author shicai.xsc 2020/12/6 19:10
 * @since 5.0.0.0
 */
@Slf4j
public class MysqlFullExtractor extends BaseExtractor {

    protected Map<String, DataSource> dataSourceMap;
    protected FullExtractorConfig extractorConfig;
    protected ExecutorService countExecutorService;
    protected ExecutorService executorService;
    protected List<MysqlFullProcessor> runningProcessors;
    protected List<Future<?>> runningFetchTasks;
    protected List<Future<?>> runningCountTasks;

    protected HostInfo hostInfo;

    protected HostInfo physicalHostInfo;
    protected String extractorName;
    protected DataImportFilter dataImportFilter;
    protected ReplicaFilter replicaFilter;
    /*
    only for replica full
    * */
    protected boolean isReplicaFull;
    protected Set<String> defaultIgnoreDbList;
    private static final String CREATE_DB = "CREATE DATABASE IF NOT EXISTS `%s` mode='auto'";
    protected Map<String, Map<String, String>> structureImportDdl;
    boolean setMaxStatementTimeOption = DynamicApplicationConfig.getBoolean(
        ConfigKeys.RPL_SET_MAX_STATEMENT_TIME_OPTION, false);

    public MysqlFullExtractor(FullExtractorConfig extractorConfig, HostInfo hostInfo, DataImportFilter filter) {
        super(extractorConfig);
        this.extractorName = "MysqlFullExtractor";
        this.extractorConfig = extractorConfig;
        this.hostInfo = hostInfo;
        this.dataImportFilter = filter;
        isReplicaFull = false;
        physicalHostInfo = hostInfo;
    }

    public MysqlFullExtractor(FullExtractorConfig extractorConfig, HostInfo hostInfo, ReplicaFilter replicaFilter) {
        super(extractorConfig);
        this.extractorName = "MysqlFullExtractor";
        this.extractorConfig = extractorConfig;
        this.hostInfo = hostInfo;
        this.replicaFilter = replicaFilter;
        isReplicaFull = true;
        physicalHostInfo = hostInfo;
    }

    @Override
    public void init() throws Exception {
        super.init();
        executorService = ThreadPoolUtil.createExecutorWithFixedNum(
            extractorConfig.getParallelCount(),
            extractorName);
        countExecutorService = ThreadPoolUtil.createExecutorWithFixedNum(
            1,
            "count " + extractorName);
        runningProcessors = new ArrayList<>();
        runningFetchTasks = new ArrayList<>();
        runningCountTasks = new ArrayList<>();
    }

    @Override
    public void start() throws Exception {
        log.info("starting {}", extractorName);

        if (isReplicaFull) {
            ReplicaMeta replicaMeta = JSON.parseObject(
                extractorConfig.getPrivateMeta(), ReplicaMeta.class);
            generateDataImportMetaIfReplica(false, null);
            if (replicaMeta.getMode() == ReplicaMode.IMAGE) {
                structureImportIfReplica();
            }
            if (replicaMeta.isExtractFullFromDn()) {
                generateDataImportMetaIfReplica(true, replicaMeta.getDnId());
            }
        }

        List<String> newConnectionSqls = new ArrayList<>(1);
        // 老版本rds需要执行该语句
        if (setMaxStatementTimeOption) {
            newConnectionSqls.add("set max_statement_time=0");
        }

        dataSourceMap = new HashMap<>(4);
        for (String db : dataImportFilter.getDoDbs()) {
            DataSource dataSource =
                DataSourceUtil.createDruidMySqlDataSource(
                    physicalHostInfo.isUsePolarxPoolCN(),
                    physicalHostInfo.getHost(),
                    physicalHostInfo.getPort(),
                    db,
                    physicalHostInfo.getUserName(),
                    physicalHostInfo.getPassword(),
                    "",
                    1,
                    extractorConfig.getParallelCount(),
                    true,
                    null,
                    newConnectionSqls);
            dataSourceMap.put(db, dataSource);
        }

        Set<String> dbNames = dataImportFilter.getDoDbs();
        for (String dbName : dbNames) {
            Set<String> tableNames = dataImportFilter.getDoTables().get(dbName);
            for (String tbName : tableNames) {

                String dstDbName = dataImportFilter.getRewriteDb(dbName, DBMSAction.INSERT);
                String dstTbName = dataImportFilter.getRewriteTable(dbName, tbName);
                MysqlFullProcessor processor = new MysqlFullProcessor();
                processor.setExtractorConfig(extractorConfig);
                processor.setDataSource(dataSourceMap.get(dbName));
                processor.setSchema(dbName);
                processor.setTbName(tbName);
                processor.setLogicalSchema(dstDbName);
                processor.setLogicalTbName(dstTbName);
                processor.setHostInfo(physicalHostInfo);
                processor.setPipeline(pipeline);
                // 获得所有的待全量的表的行数，用来计算进度
                Future<?> future = countExecutorService.submit(processor::preStart);
                runningCountTasks.add(future);
                runningProcessors.add(processor);
                log.info("{} submit for schema:{}, tbName:{}", extractorName, dbName, tbName);
            }
        }

        for (Future<?> future : runningCountTasks) {
            future.get();
        }
        for (MysqlFullProcessor processor : runningProcessors) {
            Future<?> future = executorService.submit(processor::start);
            runningFetchTasks.add(future);
        }
    }

    public void structureImportIfReplica() throws Exception {
        Set<String> dbNames = dataImportFilter.getDoDbs();
        for (String dbName : dbNames) {
            String dstDbName = dataImportFilter.getRewriteDb(dbName, DBMSAction.INSERT);
            StatisticalProxy.getInstance().applyDdlSql("", String.format(CREATE_DB, dstDbName));
            for (String tbName : dataImportFilter.getDoTables().get(dbName)) {
                String dstTbName = dataImportFilter.getRewriteTable(dbName, tbName);
                Optional<String> ddl = Optional.ofNullable(structureImportDdl.get(dstDbName))
                    .map(data -> data.get(dstTbName));
                if (ddl.isPresent()) {
                    StatisticalProxy.getInstance().applyDdlSql(dstDbName, ddl.get());
                }

            }
        }

    }

    public void generateDataImportMetaIfReplica(boolean extractFullFromDn, String dnName) throws Exception {
        initIgnoreDbList();
        getDoDbAndTableFromReplicaFilter();
        if (extractFullFromDn && dnName != null) {
            getPhysicalDoDbAndTableFromReplicaFilter(dnName);
        }
    }

    @Override
    public boolean isDone() {
        boolean allDone = true;
        for (Future<?> future : runningFetchTasks) {
            allDone &= future.isDone();
        }
        // here if allDone but !isAllDataTransferred()
        // need to exit process to retry tasks failed
        if (allDone) {
            if (isAllDataTransferred()) {
                FSMMetaManager.setFullCopyFinishTime(TaskContext.getInstance().getStateMachineId(),
                    System.currentTimeMillis());
                return true;
            } else {
                log.error("All futures have been done but some tasks are not finished");
                throw new PolardbxException("All futures have been done but some tasks are not finished");
            }
        }
        return false;
    }

    public boolean isAllDataTransferred() {
        Set<String> dbNames = dataImportFilter.getDoDbs();
        for (String dbName : dbNames) {
            for (String tbName : dataImportFilter.getDoTables().get(dbName)) {
                String fullTableName = dbName + "." + tbName;
                RplDbFullPosition position = DbTaskMetaManager
                    .getDbFullPosition(TaskContext.getInstance().getTaskId(), fullTableName);
                if (position == null || position.getFinished() != RplConstants.FINISH) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void stop() {
        log.warn("stopping {}", extractorName);
    }

    public void getDoDbAndTableFromReplicaFilter() throws Exception {
        structureImportDdl = new HashMap<>();
        Map<String, Set<String>> doTables = new HashMap<>();
        Map<String, String> dbMappings = new HashMap<>();
        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 2, 2, true);
        List<String> dbs = dbMetaCache.getDatabases();

        Set<String> doDbs = new HashSet<>();
        log.info("source db list: {}", dbs);
        for (String dbName : dbs) {
            if (defaultIgnoreDbList.contains(dbName.toLowerCase())) {
                continue;
            }
            if (replicaFilter.ignoreEvent(replicaFilter.getRewriteDb(dbName, DBMSAction.CREATEDB), null
                , DBMSAction.CREATEDB, Integer.MIN_VALUE)) {
                continue;
            }
            doDbs.add(dbName);
            Set<String> nowDoTables = new HashSet<>();
            Map<String, String> nowStructureImportDdl = new HashMap<>();
            doTables.put(dbName, nowDoTables);
            structureImportDdl.put(dbName, nowStructureImportDdl);
            List<String> tbNames = dbMetaCache.getTables(dbName);
            log.debug("from db: {} source table list: {}", dbName, doTables);
            for (String tbName : tbNames) {
                if (!replicaFilter.ignoreEvent(replicaFilter.getRewriteDb(dbName, DBMSAction.INSERT), tbName
                    , DBMSAction.INSERT, Integer.MIN_VALUE)) {
                    String tableSchema = dbMetaCache.getCreateTable(dbName, tbName);
                    nowStructureImportDdl.put(tbName, tableSchema);
                    log.debug("from db: {} source table: {}, ddl: {}", dbName, tbName, tableSchema);
                    nowDoTables.add(tbName);
                }
            }
        }
        DataImportMeta.PhysicalMeta importMeta = new DataImportMeta.PhysicalMeta();
        importMeta.setRewriteTableMapping(new HashMap<>());
        importMeta.setIgnoreServerIds("");
        importMeta.setDstDbMapping(dbMappings);
        importMeta.setSrcDbList(doDbs);
        importMeta.setPhysicalDoTableList(doTables);
        dataImportFilter = new DataImportFilter(importMeta);
        dataImportFilter.init();
        log.info("generated data import filter: {}", dataImportFilter);
    }

    public void getPhysicalDoDbAndTableFromReplicaFilter(String dnId) throws Exception {
        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 2, 2, true);

        HostInfo dnHostInfo = CommonUtil.getDnInfoByDnId(dbMetaCache.getDefaultDataSource().getConnection(), dnId);

        List<String> logicalDoDbs = dbMetaCache.getDatabases();
        log.info("source db list: {}", logicalDoDbs);
        DataImportMeta.PhysicalMeta meta = new DataImportMeta.PhysicalMeta();
        meta.setSrcHost(dnHostInfo.getHost());
        meta.setSrcPort(dnHostInfo.getPort());
        meta.setSrcUser(dnHostInfo.getUserName());
        meta.setSrcPassword(dnHostInfo.getPassword());
        meta.setSrcType(HostType.RDS);
        meta.setDstDbMapping(new HashMap<>());
        meta.setSrcDbList(new HashSet<>());
        meta.setPhysicalDoTableList(new HashMap<>());
        meta.setRewriteTableMapping(new HashMap<>());
        meta.setIgnoreServerIds("");
        for (String logicalDbName : logicalDoDbs) {
            if (defaultIgnoreDbList.contains(logicalDbName.toLowerCase())) {
                continue;
            }
            if (replicaFilter.ignoreEvent(replicaFilter.getRewriteDb(logicalDbName, DBMSAction.CREATEDB), null
                , DBMSAction.CREATEDB, Integer.MIN_VALUE)) {
                continue;
            }
            TopologyManager topologyManager = new TopologyManager(hostInfo.getHost(), hostInfo.getPort(),
                logicalDbName, hostInfo.getUserName(), hostInfo.getPassword());
            // show ds where db = 'logical_db_name' and STORAGE_INST_ID='dn_id';
            // 带全量的模式暂时都不支持设置 db mappings
            List<String> physicalDbInfoList = CommonUtil.getPhysicalDbInfoList(
                dbMetaCache.getDefaultDataSource().getConnection(), dnId, logicalDbName);
            for (String physicalDbName : physicalDbInfoList) {
                meta.getDstDbMapping().put(physicalDbName.toLowerCase(), logicalDbName);
            }
            meta.getSrcDbList().addAll(physicalDbInfoList);

            List<String> logicTableList = dbMetaCache.getTables(logicalDbName);
            for (String physicalDbName : physicalDbInfoList) {
                List<String> physicalTableList;
                physicalTableList = topologyManager.getAllPhyTableList(physicalDbName, new HashSet<>(logicTableList));
                physicalDbName = physicalDbName.toLowerCase();
                meta.getPhysicalDoTableList().put(physicalDbName, new HashSet<>(physicalTableList));
                Map<String, String> dbRewriteTableMapping = new HashMap<>();
                meta.getRewriteTableMapping().put(physicalDbName, dbRewriteTableMapping);
                for (String physicalTable : physicalTableList) {
                    physicalTable = physicalTable.toLowerCase();
                    dbRewriteTableMapping.put(physicalTable, topologyManager.getLogicTable(physicalTable));
                }
            }
            // 使用 dn 的 hostInfo 去初始化 processor
            physicalHostInfo = dnHostInfo;
        }
        dataImportFilter = new DataImportFilter(meta);
        dataImportFilter.init();
        log.info("generated data import filter: {}", dataImportFilter);
    }

    protected void initIgnoreDbList() {
        String config = DynamicApplicationConfig.getString(ConfigKeys.RPL_DEFAULT_IGNORE_DB_LIST);
        Set<String> filters = new HashSet<>();
        if (StringUtils.isNotBlank(config)) {
            for (String token : config.trim().toLowerCase().split(RplConstants.COMMA)) {
                filters.add(token.trim());
            }
        }
        defaultIgnoreDbList = filters;
    }

}
