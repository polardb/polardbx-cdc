/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.CanalBootstrap;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.filter.MinTSOFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RebuildEventLogFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RtRecordFilter;
import com.aliyun.polardbx.binlog.extractor.filter.TransactionBufferEventFilter;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_PREFER_HOST_INSTANCES;

/**
 * @author chengjin.lyf on 2020/7/10 6:59 下午
 * @since 1.0.25
 */
@Slf4j
public class BinlogExtractor implements Extractor {
    private static final String QUERY_CDC_PHY_DB_NAME = "select d.phy_db_name from db_group_info d inner join "
        + "group_detail_info g on d.group_name = g.group_name where storage_inst_id = '%s';";
    private static final String QUERY_START_CMD_WITH_REQUEST_TSO =
        "select tso from binlog_logic_meta_history where " + "type=1 and tso < '%s' order by id desc limit 2;";

    private final HashSet<String> cdcSchemaSet = new HashSet<>();
    private AuthenticationInfo authenticationInfo;
    @Setter
    private LogEventHandler<?> logEventHandler;
    private String localBinlogFilePath;
    private CanalBootstrap canalBootstrap;
    private String startCmdTso = null;
    private Long preferHostId;
    private long serverId;
    @Getter
    private String storageInstId;
    private Storage storage;
    private boolean deepDecodeEvent;
    private DnHealthChecker dnHealthChecker;

    public void init(BinlogParameter binlogParameter, String rdsBinlogPath, long serverId,
                     Storage storage, boolean deepDecodeEvent) {
        assertNotNull(binlogParameter, "binlog parameter should not be null");
        assertNotNull(binlogParameter.getStorageInstId(), "storageInstId should not be null");

        this.localBinlogFilePath = rdsBinlogPath;
        this.serverId = serverId;
        this.storage = storage;
        this.deepDecodeEvent = deepDecodeEvent;
        this.storageInstId = binlogParameter.getStorageInstId();
    }

    @Override
    public void start(String startTSO) {
        Thread.currentThread().setName("binlog-extractor-starter-" + storageInstId);
        initCdcPhySchemaTopology();
        initAuthenticationInfo();
        initStartCmdTso(startTSO);
        initPreferHostId();
        statCanal(startTSO);
        startDnHealthChecker();
        log.info("binlog extractor started success.");
    }

    private void initCdcPhySchemaTopology() {
        List<Map<String, Object>> cdcDataList = queryCdcTopology();
        if (CollectionUtils.isEmpty(cdcDataList)) {
            throw new PolardbxException("can not find cdc schema info from storage inst id : " + storageInstId);
        }
        for (Map<String, Object> cdcMap : cdcDataList) {
            String schemaName = (String) cdcMap.get("phy_db_name");
            if (schemaName.endsWith("single")) {
                continue;
            }
            if (schemaName.startsWith("__cdc__")) {
                cdcSchemaSet.add(schemaName);
            }
        }
        log.info("init cdc schema set in binlog extractor, {}.", cdcSchemaSet);
    }

    public void initAuthenticationInfo() {
        List<DnHost> dnHostList = buildDnHost(storageInstId);
        DnHost leaderDnHost = dnHostList.get(0);
        authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setLeader(leaderDnHost);
        authenticationInfo.setDnNodeList(dnHostList);
        authenticationInfo.switchLeader();
        authenticationInfo.setStorageMasterInstId(storageInstId);
        authenticationInfo.setStorageInstId(leaderDnHost.getStorageInstId());
        authenticationInfo.setUid(DynamicApplicationConfig.getString(RDS_UID));
        authenticationInfo.setBid(DynamicApplicationConfig.getString(RDS_BID));
        log.info("init authentication info in binlog extractor, {}.", JSON.toJSONString(leaderDnHost));
    }

    private void initStartCmdTso(String startTSO) {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        if (StringUtils.isNotBlank(startTSO)) {
            List<String> startCmdTSOList =
                metaTemplate.queryForList(String.format(QUERY_START_CMD_WITH_REQUEST_TSO, startTSO), String.class);
            if (!CollectionUtils.isEmpty(startCmdTSOList)) {
                startCmdTso = startCmdTSOList.get(0);
                if (startCmdTSOList.size() == 2){
                    long secondDiff = CommonUtils.getTsoPhysicalTime(startTSO, TimeUnit.SECONDS)
                        - CommonUtils.getTsoPhysicalTime(startCmdTso, TimeUnit.SECONDS);
                    // request 和 base 之前选取大于10分钟的，解决pushback 失效的问题
                    if (secondDiff <= DynamicApplicationConfig.getLong(
                        ConfigKeys.TASK_RECOVER_SEARCH_TSO_AUTO_QUICK_MODE_SWITCH_PUSH_BACKWARD_SECOND)){
                        log.warn("first start cmd tso {} diff with request tso {} less than {}, will use second tso : {}",
                            startCmdTso, startTSO, secondDiff, startCmdTSOList.get(1));
                        startCmdTso = startCmdTSOList.get(1);
                    }
                }
            }
        }
        log.info("init start command tso in binlog extractor, {}.", startCmdTso);
    }

    private void initPreferHostId() {
        String hostMap = DynamicApplicationConfig.getString(TASK_DUMP_OFFLINE_BINLOG_PREFER_HOST_INSTANCES);
        if (StringUtils.isNotBlank(hostMap)) {
            JSONObject jsonObject = JSON.parseObject(hostMap);
            String preferHostIdStr = jsonObject.getString(authenticationInfo.getStorageMasterInstId());
            if (StringUtils.isNotBlank(preferHostIdStr)) {
                preferHostId = Long.valueOf(preferHostIdStr);
                log.info("find preferred host id in binlog extractor, {}", preferHostIdStr);
            }
        }
    }

    void statCanal(String startTSO) {
        final String cnVersion = ServerConfigUtil.getCnVersion();
        canalBootstrap = new CanalBootstrap(authenticationInfo, cnVersion,
            localBinlogFilePath, preferHostId, startCmdTso);
        canalBootstrap.setHandler(logEventHandler);
        addDefaultFilter(startTSO);
        try {
            canalBootstrap.start(startTSO);
        } catch (Exception e) {
            log.error("start canal error!!", e);
            throw new PolardbxException("start canal error!!", e);
        }
    }

    void startDnHealthChecker() {
        if (dnHealthChecker != null) {
            dnHealthChecker.stopCheck();
        }
        dnHealthChecker = new DnHealthChecker(authenticationInfo);
        dnHealthChecker.startCheck();
    }

    List<Map<String, Object>> queryCdcTopology() {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        return metaTemplate.queryForList(String.format(QUERY_CDC_PHY_DB_NAME, storageInstId));
    }

    List<DnHost> buildDnHost(String storageInstId) {
        return DnHost.buildHostForExtractor(storageInstId);
    }

    /**
     * binlog event -> acceptFilter -> ddlFilter -> disruptor -> rtFilter -> recordTso -> rebuildEvent -> transaction
     */
    private void addDefaultFilter(String startTSO) {
        PolarDbXTableMetaManager dbTableMetaManager =
            new PolarDbXTableMetaManager(authenticationInfo.getStorageMasterInstId());
        dbTableMetaManager.init();

        EventAcceptFilter acceptFilter = getEventAcceptFilter(dbTableMetaManager);

        canalBootstrap.addLogFilter(new RtRecordFilter());
        canalBootstrap.addLogFilter(new TransactionBufferEventFilter(storage, startTSO));
        canalBootstrap.addLogFilter(
            new RebuildEventLogFilter(serverId, acceptFilter, deepDecodeEvent, dbTableMetaManager));
        canalBootstrap.addLogFilter(new MinTSOFilter(startTSO));
    }

    private EventAcceptFilter getEventAcceptFilter(PolarDbXTableMetaManager dbTableMetaManager) {
        EventAcceptFilter acceptFilter =
            new EventAcceptFilter(authenticationInfo.getStorageMasterInstId(), true, dbTableMetaManager, cdcSchemaSet);

        acceptFilter.addAcceptEvent(LogEvent.FORMAT_DESCRIPTION_EVENT);
        // accept dml
        acceptFilter.addAcceptEvent(LogEvent.WRITE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.WRITE_ROWS_EVENT_V1);
        acceptFilter.addAcceptEvent(LogEvent.DELETE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.DELETE_ROWS_EVENT_V1);
        acceptFilter.addAcceptEvent(LogEvent.UPDATE_ROWS_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.UPDATE_ROWS_EVENT_V1);
        // accept query
        acceptFilter.addAcceptEvent(LogEvent.QUERY_EVENT);
        // support trace
        acceptFilter.addAcceptEvent(LogEvent.ROWS_QUERY_LOG_EVENT);
        // accept xa
        acceptFilter.addAcceptEvent(LogEvent.XA_PREPARE_LOG_EVENT);
        // accept tso
        acceptFilter.addAcceptEvent(LogEvent.SEQUENCE_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.GCN_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.TABLE_MAP_EVENT);
        acceptFilter.addAcceptEvent(LogEvent.XID_EVENT);
        return acceptFilter;
    }

    @Override
    public void stop() {
        log.info("stopping binlog extractor.");
        canalBootstrap.stop();
        dnHealthChecker.stopCheck();
        log.info("binlog binlog extractor stopped.");
    }

    private void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new NullPointerException(msg);
        }
    }

}
