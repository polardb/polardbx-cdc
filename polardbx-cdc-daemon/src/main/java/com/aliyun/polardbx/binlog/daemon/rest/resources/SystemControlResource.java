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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.Constants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.system.InstructionType;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.ann.Leader;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.google.common.collect.Lists;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonUtils.buildStartCmd;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.GLOBAL_BINLOG_LATEST_CURSOR;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

@Path("/system")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class SystemControlResource {
    private static final Logger logger = LoggerFactory.getLogger(SystemControlResource.class);

    private static final String QUERY_VIP_STORAGE =
        "select * from storage_info where inst_kind=0 and is_vip = 1 and storage_inst_id = '%s' limit 1";
    private static final String QUERY_STORAGE_LIMIT_1 =
        "select * from storage_info where inst_kind=0  and storage_inst_id = '%s' limit 1";
    private static final String TRANSACTION_POLICY = "set drds_transaction_policy='TSO'";
    private static final String SEND_CONFIG_UPDATE_CMND =
        "insert into __cdc_instruction__(INSTRUCTION_TYPE, INSTRUCTION_CONTENT, INSTRUCTION_ID) values(?,?,?)";

    private static String getGroupName() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (ClusterTypeEnum.BINLOG.name().equals(clusterType)) {
            return Constants.GROUP_NAME_GLOBAL;
        } else if (ClusterTypeEnum.BINLOG_X.name().equals(clusterType)) {
            String group = getString(BINLOG_X_STREAM_GROUP_NAME);
            if (StringUtils.isBlank(group)) {
                throw new PolardbxException("stream group name can`t be empty for binlog_x cluster");
            }
            return group;
        } else {
            throw new PolardbxException("invalid cluster type for reset, " + clusterType);
        }
    }

    private static String getCmdIdCondition() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (Objects.equals(clusterType, ClusterTypeEnum.BINLOG_X.name())) {
            return " cmd_id = '" + buildStartCmd() + "'";
        } else {
            return " cmd_id = '" + buildStartCmd() + "' or cmd_id = '00000000'";
        }
    }

    private static String getInstructionIdCondition() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (Objects.equals(clusterType, ClusterTypeEnum.BINLOG_X.name())) {
            return " INSTRUCTION_ID = '" + buildStartCmd() + "'";
        } else {
            return " INSTRUCTION_ID = '" + buildStartCmd() + "' or INSTRUCTION_ID = '0'";
        }
    }

    @GET
    @Path("/reset")
    @Leader
    public String reset() {
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            throw new PolardbxException("reset operation muste execute in daemon leader!");
        }

        ClusterBootstrapService bootstrapService = ClusterBootStrapFactory
            .getBootstrapService(ClusterTypeEnum.valueOf(DynamicApplicationConfig.getClusterType()));
        bootstrapService.stop();
        try {
            return doReset();
        } finally {
            bootstrapService.start();
        }
    }

    @GET
    @Path("/clean")
    @Leader
    public String clean() {
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            throw new PolardbxException("clean operation muste execute in daemon leader!");
        }

        closeBinlogXAutoInit();
        ClusterBootstrapService bootstrapService = ClusterBootStrapFactory
            .getBootstrapService(ClusterTypeEnum.valueOf(DynamicApplicationConfig.getClusterType()));
        bootstrapService.stop();
        return doClean();
    }

    @POST
    @Path("/setConfigEnv")
    public String setConfigEnv(String content) {
        JSONObject object = JSON.parseObject(content);
        String name = object.getString("name");
        String value = object.getString("value");
        logger.info("receive set name : " + name + " , value : " + value);
        if (StringUtils.isBlank(name)) {
            return "name should not be empty";
        }
        JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
        transactionTemplate.execute((o) -> transactionTemplate.execute(transactionStatus -> {
            template.execute(TRANSACTION_POLICY);
            JSONObject newObject = new JSONObject();
            newObject.put(name, value);
            template
                .update(SEND_CONFIG_UPDATE_CMND, InstructionType.CdcEnvConfigChange.name(), newObject.toJSONString(),
                    UUID.randomUUID().toString());
            return null;
        }));
        return "成功";
    }

    private String doReset() {
        cleanPolarxSystemDb();
        logger.info("polardbx is reset.");

        List<String> sqlList = new ArrayList<>();
        initCommonMetaCleanSql(sqlList);
        initResetMetaCleanSql(sqlList);
        executeMetaSqlList(sqlList);

        flushDNLogs();
        logger.info("successfully reset binlog");

        return "成功";
    }

    private List<String> initCommonMetaCleanSql(List<String> sqlList) {
        String CDC_META_TABLE_RESET_1 =
            String.format("delete from binlog_storage_history_detail where cluster_id = '%s';", getString(CLUSTER_ID));
        String CDC_META_TABLE_RESET_2 =
            String.format("delete from binlog_oss_record where group_id = '%s';", getGroupName());
        String CDC_META_TABLE_RESET_3 =
            String.format("delete from binlog_phy_ddl_history where cluster_id = '%s';", getString(CLUSTER_ID));
        String CDC_META_TABLE_RESET_4 =
            String.format("delete from binlog_polarx_command where %s;", getCmdIdCondition());
        String CDC_META_TABLE_RESET_5 =
            String.format("delete from binlog_storage_history where cluster_id = '%s';", getString(CLUSTER_ID));
        String CDC_META_TABLE_RESET_6 =
            String.format("delete from binlog_schedule_history where cluster_id = '%s';", getString(CLUSTER_ID));
        String CDC_META_TABLE_RESET_7 =
            String.format("delete from binlog_task_config where cluster_id ='%s';", getString(CLUSTER_ID));
        String CDC_META_TABLE_RESET_8 =
            String.format("delete from binlog_x_stream where group_name = '%s';", getGroupName());
        String CDC_META_TABLE_RESET_9 =
            String.format("delete from binlog_x_table_stream_mapping where cluster_id = '%s';", getString(CLUSTER_ID));
        String CDC_META_PARAMETER_RESET_1 =
            String.format("delete from binlog_system_config where config_key='%s';",
                EXPECTED_STORAGE_TSO_KEY);
        String CDC_META_PARAMETER_RESET_2 =
            String.format("delete from binlog_system_config where config_key='%s';",
                CLUSTER_SNAPSHOT_VERSION_KEY);
        String CDC_META_PARAMETER_RESET_3 =
            String.format("delete from binlog_system_config where config_key='%s';",
                CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY);
        String CDC_META_PARAMETER_RESET_4 =
            String.format("delete from binlog_system_config where config_key='%s';",
                CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY);
        String CDC_META_PARAMETER_RESET_6 =
            String.format("delete from binlog_system_config where config_key='%s';",
                GLOBAL_BINLOG_LATEST_CURSOR);
        sqlList.add(CDC_META_TABLE_RESET_1);
        sqlList.add(CDC_META_TABLE_RESET_2);
        sqlList.add(CDC_META_TABLE_RESET_3);
        sqlList.add(CDC_META_TABLE_RESET_4);
        sqlList.add(CDC_META_TABLE_RESET_5);
        sqlList.add(CDC_META_TABLE_RESET_6);
        sqlList.add(CDC_META_TABLE_RESET_7);

        if (DynamicApplicationConfig.getClusterType().equals(ClusterTypeEnum.BINLOG_X.name())) {
            sqlList.add(CDC_META_TABLE_RESET_8);
            sqlList.add(CDC_META_TABLE_RESET_9);
        }

        sqlList.add(CDC_META_PARAMETER_RESET_1);
        sqlList.add(CDC_META_PARAMETER_RESET_2);
        sqlList.add(CDC_META_PARAMETER_RESET_3);
        sqlList.add(CDC_META_PARAMETER_RESET_4);
        sqlList.add(CDC_META_PARAMETER_RESET_6);
        return sqlList;
    }

    private List<String> initResetMetaCleanSql(List<String> sqlList) {
        String CDC_META_PARAMETER_RESET_5 =
            String.format("delete from binlog_system_config where config_key='%s';",
                CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        sqlList.add(CDC_META_PARAMETER_RESET_5);
        return sqlList;
    }

    private void cleanPolarxSystemDb() {
        String CDC_POLARX_INSTRUCTION_TABLE_RESET =
            String.format("delete from __cdc__.__cdc_instruction__ where INSTRUCTION_TYPE = 'CdcStart' and %s;",
                getInstructionIdCondition());

        JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        TransactionTemplate polarxTransactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
        polarxTransactionTemplate.execute(t -> {
            polarxJdbcTemplate.execute(CDC_POLARX_INSTRUCTION_TABLE_RESET);
            return null;
        });
        logger.info("polardbx is reset.");
    }

    private void executeMetaSqlList(List<String> sqlList) {
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        transactionTemplate.execute(t -> {
            for (String sql : sqlList) {
                logger.info("execute sql : " + sql);
                metaTemplate.execute(sql);
            }
            logger.info("meta table and system parameters is reset.");
            return null;
        });
    }

    private void flushDNLogs() {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        final StorageInfoMapper storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        List<StorageInfo> storageInfos;
        storageInfos = storageInfoMapper.select(c ->
            c.where(instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                .and(status, isNotEqualTo(2))//0:storage ready, 1:storage not_ready
                .orderBy(id)
        );
        storageInfos = Lists.newArrayList(storageInfos.stream().collect(
            Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1,
                (s1, s2) -> s1)).values());

        for (StorageInfo storageInfo : storageInfos) {
            List<Map<String, Object>> dataList =
                metaTemplate.queryForList(String.format(QUERY_VIP_STORAGE, storageInfo.getStorageInstId()));
            if (CollectionUtils.isEmpty(dataList)) {
                dataList =
                    metaTemplate.queryForList(String.format(QUERY_STORAGE_LIMIT_1, storageInfo.getStorageInstId()));
            }
            if (dataList.size() != 1) {
                throw new PolardbxException("storageInstId expect size 1 , but query meta db size " + dataList.size());
            }

            String ip = (String) dataList.get(0).get("ip");
            int port = (int) dataList.get(0).get("port");
            String user = (String) dataList.get(0).get("user");
            String passwordEnc = (String) dataList.get(0).get("passwd_enc");
            String password = PasswdUtil.decryptBase64(passwordEnc);

            try {
                Class.forName("com.mysql.jdbc.Driver");
                try (Connection conn = DriverManager
                    .getConnection(String.format("jdbc:mysql://%s:%s?useSSL=false", ip, port), user, password)) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("flush logs");
                    }
                }
            } catch (Exception e) {
                logger.error("jdbc failed!", e);
                throw new RuntimeException("jdbc failed", e);
            }

            logger.info("storage node is reset for id : " + storageInfo.getStorageInstId());
        }
    }

    private void closeBinlogXAutoInit() {
        logger.info("close binlog x auto init");
        String clusterKeysPattern = DynamicApplicationConfig.getString(CLUSTER_ID) + ":";
        DynamicApplicationConfig.setValue(clusterKeysPattern + ConfigKeys.BINLOG_X_AUTO_INIT, false + "");
        DynamicApplicationConfig
            .setValue(clusterKeysPattern + ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING, false + "");
    }

    private void initCleanSql(List<String> sqlList) {
        String CDC_META_TABLE_CLEAN_1 =
            String.format("delete from binlog_x_stream_group where group_name = '%s';", getGroupName());

        sqlList.add(CDC_META_TABLE_CLEAN_1);
    }

    private String doClean() {

        cleanPolarxSystemDb();
        List<String> sqlList = new ArrayList<>();
        initCommonMetaCleanSql(sqlList);
        initCleanSql(sqlList);
        executeMetaSqlList(sqlList);
        flushDNLogs();
        logger.info("successfully clean binlog");

        return "成功";
    }
}
