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
import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ServerConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.CanalBootstrap;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.DbHostVO;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.filter.EventAcceptFilter;
import com.aliyun.polardbx.binlog.extractor.filter.MinTSOFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RebuildEventLogFilter;
import com.aliyun.polardbx.binlog.extractor.filter.RtRecordFilter;
import com.aliyun.polardbx.binlog.extractor.filter.TransactionBufferEventFilter;
import com.aliyun.polardbx.binlog.metrics.ExtractorMetrics;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.ASSIGNED_DN_IP;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_RDSBINLOG_DOWNLOAD_ASSINGED_HOST;

/**
 * @author chengjin.lyf on 2020/7/10 6:59 下午
 * @since 1.0.25
 */
public class BinlogExtractor implements Extractor {

    private static final Logger logger = LoggerFactory.getLogger(BinlogExtractor.class);
    private static final String QUERY_VIP_STORAGE =
        "select * from storage_info where inst_kind=0 and is_vip = 1 and storage_inst_id = '%s' limit 1";
    private static final String QUERY_STORAGE_LIMIT_1 =
        "select * from storage_info where inst_kind=0 and storage_inst_id = '%s' and xport <> -1 limit 1";
    private static final String QUERY_CDC_INFO =
        "select d.phy_db_name from db_group_info d inner join group_detail_info g on d.group_name = g.group_name where storage_inst_id = '%s';";
    private static final String QUERY_FOR_VERSION = "select version()";
    private static final String QUERY_START_CMD = "select tso from binlog_logic_meta_history order by id asc limit 1";
    private static final String cnVersion = getCnVersion();

    private final HashSet<String> cdcSchemaSet = new HashSet<>();
    private AuthenticationInfo authenticationInfo;
    private LogEventHandler<?> logEventHandler;
    private Storage storage;
    private String localBinlogFilePath;
    private List<LogEventFilter<?>> processLogEventFilter = Lists.newArrayList();
    private CanalBootstrap canalBootstrap;
    private String startCmdTSO = null;

    private static String getCnVersion() {
        JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        return polarxTemplate.queryForObject(QUERY_FOR_VERSION, String.class);
    }

    public void setLogEventHandler(LogEventHandler<?> logEventHandler) {
        this.logEventHandler = logEventHandler;
    }

    public void init(BinlogParameter binlogParameter, Storage storage, String rdsBinlogPath) {
        assertNotNull(binlogParameter, "binlog parameter should not be null");
        assertNotNull(binlogParameter.getStorageInstId(), "storageInstId should not be null");

        this.storage = storage;
        this.localBinlogFilePath = rdsBinlogPath;
        String storageInstId = binlogParameter.getStorageInstId();
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<Map<String, Object>> dataList = metaTemplate.queryForList(String.format(QUERY_VIP_STORAGE, storageInstId));
        if (CollectionUtils.isEmpty(dataList)) {
            dataList = metaTemplate.queryForList(String.format(QUERY_STORAGE_LIMIT_1, storageInstId));
        }
        if (dataList.size() != 1) {
            throw new PolardbxException("storageInstId expect size 1 , but query meta db size " + dataList.size());
        }

        String ip;
        if (StringUtils.isNotBlank(DynamicApplicationConfig.getString(ASSIGNED_DN_IP))) {
            ip = DynamicApplicationConfig.getString(ASSIGNED_DN_IP);
        } else {
            ip = (String) dataList.get(0).get("ip");
        }
        int port = (int) dataList.get(0).get("port");
        String user = (String) dataList.get(0).get("user");
        String passwordEnc = (String) dataList.get(0).get("passwd_enc");
        String password = PasswdUtil.decryptBase64(passwordEnc);

        DbHostVO dbHost = new DbHostVO();
        dbHost.setUserName(user);
        dbHost.setPassword(password);
        dbHost.setIp(ip);
        dbHost.setPort(port);
        dbHost.setCharset("utf8");

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

        List<String> startCmdTSOList = metaTemplate.queryForList(QUERY_START_CMD, String.class);
        if (!CollectionUtils.isEmpty(startCmdTSOList)) {
            startCmdTSO = startCmdTSOList.get(0);
        }
        authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setAddress(new InetSocketAddress(dbHost.getIp(), dbHost.getPort()));
        authenticationInfo.setCharset(dbHost.getCharset());
        authenticationInfo.setUsername(dbHost.getUserName());
        authenticationInfo.setPassword(dbHost.getPassword());
        authenticationInfo.setStorageInstId(storageInstId);
        authenticationInfo.setUid(DynamicApplicationConfig.getString(RDS_UID));
        authenticationInfo.setBid(DynamicApplicationConfig.getString(RDS_BID));

        MultiStreamStartTsoWindow.getInstance().addNewStream(storageInstId);

        logger.info("init binlog extractor with host " + JSON.toJSONString(dbHost));
    }

    @Override
    public void start(String startTSO) {

        assertNotNull(authenticationInfo, "authenticationInfo should not be null");

        ExtractorMetrics.get();
        Long preferHostId = null;
        String hostMap = DynamicApplicationConfig.getString(TASK_RDSBINLOG_DOWNLOAD_ASSINGED_HOST);
        if (StringUtils.isNotBlank(hostMap)) {
            JSONObject jsonObject = JSON.parseObject(hostMap);
            String preferHostIdStr = jsonObject.getString(authenticationInfo.getStorageInstId());
            if (StringUtils.isNotBlank(preferHostIdStr)) {
                preferHostId = Long.valueOf(preferHostIdStr);
                logger.warn(authenticationInfo.getStorageInstId() + " prefer host id : " + preferHostIdStr);
            }
        }
        canalBootstrap =
            new CanalBootstrap(authenticationInfo, cnVersion, localBinlogFilePath, preferHostId, startCmdTSO);
        canalBootstrap.setHandler(logEventHandler);
        addDefaultFilter(startTSO);
        try {
            canalBootstrap.start(startTSO);
        } catch (Exception e) {
            logger.error("start canal error", e);
            throw new PolardbxException(e);
        }
        logger.info("binlog extractor started success");
    }

    /**
     * binlog event -> acceptFilter -> ddlFilter -> disruptor -> rtFilter -> recordTso -> rebuildEvent -> transaction
     */
    private void addDefaultFilter(String startTSO) {

        long serverId = ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
        String clusterType = DynamicApplicationConfig.getClusterType();

        logger.info("starting binlog extractor serverId : " + serverId);

        PolarDbXTableMetaManager dbTableMetaManager =
            new PolarDbXTableMetaManager(authenticationInfo.getStorageInstId(), authenticationInfo);
        dbTableMetaManager.init();

        EventAcceptFilter acceptFilter =
            new EventAcceptFilter(authenticationInfo.getStorageInstId(), true, dbTableMetaManager, cdcSchemaSet);
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
        canalBootstrap.addLogFilter(new TransactionBufferEventFilter(storage));
        // 整形
        canalBootstrap.addLogFilter(new RebuildEventLogFilter(serverId, acceptFilter,
            ClusterTypeEnum.BINLOG_X.name().equals(clusterType), dbTableMetaManager));

        canalBootstrap.addLogFilter(new MinTSOFilter(startTSO));
    }

    @Override
    public void stop() {
        logger.info("stopping binlog extrator");
        canalBootstrap.stop();
        logger.info("binlog binlog extrator stopped");
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
            logger.info(" starting to fetch binlog with tso : " + startTSO);
        } else {
            logger.info(" starting to fetch binlog with tso is null , try start with current timestamp");
            binlogPosition.setTso(-1);
        }
        return binlogPosition;
    }

    public List<LogEventFilter<?>> getProcessLogEventFilter() {
        return processLogEventFilter;
    }

    public void setProcessLogEventFilter(List<LogEventFilter<?>> processLogEventFilter) {
        this.processLogEventFilter = processLogEventFilter;
    }
}
