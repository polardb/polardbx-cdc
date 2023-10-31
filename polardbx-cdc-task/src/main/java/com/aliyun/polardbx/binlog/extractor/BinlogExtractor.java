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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.CanalBootstrap;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.DnHost;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.filter.MinTSOFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RebuildEventLogFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RtRecordFilter;
import com.aliyun.polardbx.binlog.extractor.filter.TransactionBufferEventFilter;
import com.aliyun.polardbx.binlog.metrics.ExtractorMetrics;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_PREFER_HOST_INSTANCES;

/**
 * @author chengjin.lyf on 2020/7/10 6:59 下午
 * @since 1.0.25
 */
@Slf4j
public class BinlogExtractor implements Extractor {
    private static final String QUERY_CDC_INFO = "select d.phy_db_name from db_group_info d inner join "
        + "group_detail_info g on d.group_name = g.group_name where storage_inst_id = '%s';";
    private static final String QUERY_START_CMD_WITH_REQUEST_TSO = "select tso from binlog_logic_meta_history where "
        + "type=1 and tso < '%s' order by id desc limit 1;";
    private static final String QUERY_FOR_VERSION = "select version()";
    private static final String CN_VERSION = getCnVersion();

    private final HashSet<String> cdcSchemaSet = new HashSet<>();
    private AuthenticationInfo authenticationInfo;
    private LogEventHandler<?> logEventHandler;
    private String localBinlogFilePath;
    private CanalBootstrap canalBootstrap;
    private String startCmdTso = null;
    private DnHost dnHost;

    private static String getCnVersion() {
        JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        return polarxTemplate.queryForObject(QUERY_FOR_VERSION, String.class);
    }

    public void setLogEventHandler(LogEventHandler<?> logEventHandler) {
        this.logEventHandler = logEventHandler;
    }

    public void init(BinlogParameter binlogParameter, String rdsBinlogPath) {
        assertNotNull(binlogParameter, "binlog parameter should not be null");
        assertNotNull(binlogParameter.getStorageInstId(), "storageInstId should not be null");

        this.localBinlogFilePath = rdsBinlogPath;
        String storageInstId = binlogParameter.getStorageInstId();
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");

        List<Map<String, Object>> cdcDataList = metaTemplate.queryForList(String.format(QUERY_CDC_INFO, storageInstId));
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

        dnHost = DnHost.buildHostForExtractor(storageInstId);
        authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setAddress(new InetSocketAddress(dnHost.getIp(), dnHost.getPort()));
        authenticationInfo.setCharset(dnHost.getCharset());
        authenticationInfo.setUsername(dnHost.getUserName());
        authenticationInfo.setPassword(dnHost.getPassword());
        authenticationInfo.setStorageMasterInstId(storageInstId);
        authenticationInfo.setStorageInstId(dnHost.getStorageInstId());
        authenticationInfo.setUid(DynamicApplicationConfig.getString(RDS_UID));
        authenticationInfo.setBid(DynamicApplicationConfig.getString(RDS_BID));

        MultiStreamStartTsoWindow.getInstance().addNewStream(storageInstId);

        log.info("init binlog extractor with host " + JSON.toJSONString(dnHost));
    }

    @Override
    public void start(String startTSO) {

        assertNotNull(authenticationInfo, "authenticationInfo should not be null");
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        if (StringUtils.isNotBlank(startTSO)) {
            List<String> startCmdTSOList =
                metaTemplate.queryForList(String.format(QUERY_START_CMD_WITH_REQUEST_TSO, startTSO), String.class);
            if (!CollectionUtils.isEmpty(startCmdTSOList)) {
                startCmdTso = startCmdTSOList.get(0);
            }
        }
        log.info("search base tso is : " + startCmdTso);

        ExtractorMetrics.get();
        Long preferHostId = null;
        String hostMap = DynamicApplicationConfig.getString(TASK_DUMP_OFFLINE_BINLOG_PREFER_HOST_INSTANCES);
        if (StringUtils.isNotBlank(hostMap)) {
            JSONObject jsonObject = JSON.parseObject(hostMap);
            String preferHostIdStr = jsonObject.getString(authenticationInfo.getStorageMasterInstId());
            if (StringUtils.isNotBlank(preferHostIdStr)) {
                preferHostId = Long.valueOf(preferHostIdStr);
                log.warn(authenticationInfo.getStorageMasterInstId() + " prefer host id : " + preferHostIdStr);
            }
        }
        canalBootstrap =
            new CanalBootstrap(authenticationInfo, CN_VERSION, localBinlogFilePath, preferHostId, startCmdTso);
        canalBootstrap.setHandler(logEventHandler);
        addDefaultFilter(startTSO);
        try {
            canalBootstrap.start(startTSO);
        } catch (Exception e) {
            log.error("start canal error", e);
            throw new PolardbxException(e);
        }
        log.info("binlog extractor started success");
    }

    /**
     * binlog event -> acceptFilter -> ddlFilter -> disruptor -> rtFilter -> recordTso -> rebuildEvent -> transaction
     */
    private void addDefaultFilter(String startTSO) {

        long serverId = ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
        String clusterType = DynamicApplicationConfig.getClusterType();

        log.info("starting binlog extractor serverId : " + serverId);

        PolarDbXTableMetaManager dbTableMetaManager =
            new PolarDbXTableMetaManager(authenticationInfo.getStorageMasterInstId());
        dbTableMetaManager.init();

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

        // 记录RT
        canalBootstrap.addLogFilter(new RtRecordFilter());
        // 先合并事务
        // 合并完事务后,要在合并事务是识别出逻辑DDL，，可以并发整形
        canalBootstrap.addLogFilter(new TransactionBufferEventFilter());
        // 整形
        canalBootstrap.addLogFilter(new RebuildEventLogFilter(serverId, acceptFilter,
            ClusterType.BINLOG_X.name().equals(clusterType), dbTableMetaManager));

        canalBootstrap.addLogFilter(new MinTSOFilter(startTSO));
    }

    @Override
    public void stop() {
        log.info("stopping binlog extrator");
        canalBootstrap.stop();
        log.info("binlog binlog extrator stopped");
    }

    private void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new NullPointerException(msg);
        }
    }

    /**
     * 利用时间戳创建位点，多回溯2个heartbeat间隔，用以确保消费端一定有真实tso
     */
    private BinlogPosition buildStartPosition(String startTSO) {
        BinlogPosition binlogPosition = null;
        binlogPosition = new BinlogPosition(null, -1, -1, -1);
        if (StringUtils.isNotBlank(startTSO)) {
            Long tso = CommonUtils.getTsoTimestamp(startTSO);
            binlogPosition.setTso(tso);
            binlogPosition.setRtso(startTSO);
            log.info(" starting to fetch binlog with tso : " + startTSO);
        } else {
            log.info(" starting to fetch binlog with tso is null , try start with current timestamp");
            binlogPosition.setTso(-1);
        }
        return binlogPosition;
    }

    public DnHost getDnHost() {
        return dnHost;
    }
}
