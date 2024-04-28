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
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.constant.ClusterExecutionInstruction;
import com.aliyun.polardbx.binlog.daemon.constant.ClusterRebalanceInstruction;
import com.aliyun.polardbx.binlog.daemon.rest.ann.Leader;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.BinlogTaskStatus;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.PooledHttpHelper;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
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
import static com.aliyun.polardbx.binlog.util.CommonUtils.buildStartCmd;
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

    private final DumperInfoMapper dumperInfoMapper =
        SpringContextHolder.getObject(DumperInfoMapper.class);
    private final BinlogTaskInfoMapper taskInfoMapper =
        SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper =
        SpringContextHolder.getObject(NodeInfoMapper.class);

    private static String getGroupName() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (ClusterType.BINLOG.name().equals(clusterType)) {
            return CommonConstants.GROUP_NAME_GLOBAL;
        } else if (ClusterType.BINLOG_X.name().equals(clusterType)) {
            String group = getString(BINLOGX_STREAM_GROUP_NAME);
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
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        if (Objects.equals(clusterType, ClusterType.BINLOG_X.name())) {
            return String
                .format(" cmd_id = '%s' or cmd_id = '%s' ", buildStartCmd(), clusterId + ":" + buildStartCmd());
        } else {
            return String
                .format(" (cmd_id = '%s' or cmd_id = '00000000' or cmd_id = '%s')",
                    buildStartCmd(), clusterId + ":" + buildStartCmd());
        }
    }

    private static String getInstructionIdCondition() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        if (Objects.equals(clusterType, ClusterType.BINLOG_X.name())) {
            return String
                .format(" INSTRUCTION_ID = '%s' or  INSTRUCTION_ID = '%s'", buildStartCmd(),
                    clusterId + ":" + buildStartCmd());
        } else {
            return String.format(
                " ((INSTRUCTION_ID = '%s' and (CLUSTER_ID is NULL or CLUSTER_ID = '0' or CLUSTER_ID = '%s' ) ) or INSTRUCTION_ID = '0' or INSTRUCTION_ID = '%s')",
                buildStartCmd(), clusterId, clusterId + ":" + buildStartCmd());
        }
    }

    @GET
    @Path("/reset")
    @Leader
    public String reset() {
        logger.info("try to reset master");

        // force 开关打开，不需要等待任务调度，本地开发使用
        if (!DynamicApplicationConfig.getBoolean(ConfigKeys.FORCE_RESET_ENABLE)) {
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                logger.error("reset operation must execute in daemon leader!");
                ResultCode<Object> res = ResultCode.builder().code(CommonConstants.FAILURE_CODE)
                    .msg("reset operation must execute in daemon leader!").data(false).build();
                return JSON.toJSONString(res);
            }

            try {
                waitStop();
            } catch (Exception e) {
                logger.error("wait stop error", e);
                ResultCode<Object> res = ResultCode.builder().code(CommonConstants.FAILURE_CODE)
                    .msg("Want to reset master? You should execute stop master first!")
                    .data(false).build();
                return JSON.toJSONString(res);
            }
        }

        ClusterBootstrapService bootstrapService = ClusterBootStrapFactory
            .getBootstrapService(ClusterType.valueOf(DynamicApplicationConfig.getClusterType()));
        try {
            bootstrapService.stop();
            doReset();
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        } finally {
            bootstrapService.start();
        }
    }

    @GET
    @Path("/clean")
    @Leader
    public String clean() {
        logger.info("receive clean request");
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            throw new PolardbxException("clean operation must execute in daemon leader!");
        }

        closeBinlogXAutoInit();
        ClusterBootstrapService bootstrapService = ClusterBootStrapFactory
            .getBootstrapService(ClusterType.valueOf(DynamicApplicationConfig.getClusterType()));
        bootstrapService.stop();
        return doClean();
    }

    @GET
    @Path("/cleanBinlog")
    public synchronized String cleanBinlog() {
        logger.info("receive clean binlog command");

        try {
            logger.info("try to clean local binlog file");
            String binlogPath = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH);
            FileUtils.cleanDirectory(new File(binlogPath));
            Integer timeout = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_WAIT_CLEAN_BINLOG_TIMEOUT_SECOND);

            if (RuntimeLeaderElector.isDaemonLeader()) {
                logger.info("send clean binlog command to daemon slave");
                String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
                List<NodeInfo> slaveNodeInfo = getSlaveNodeInfo(clusterId);
                slaveNodeInfo.stream().map(r -> "http://" + r.getIp() + ":" + r.getDaemonPort() + "/system/cleanBinlog")
                    .forEach(r -> {
                        try {
                            PooledHttpHelper.doGetWithoutParam(r, null, timeout * 1000);
                        } catch (URISyntaxException | IOException e) {
                            logger.error("send clean binlog command to daemon slave error", e);
                            throw new RuntimeException(e);
                        }
                    });
            }

            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            logger.error("clean binlog error", e);
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        }
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

    private void doReset() {
        cleanPolarxSystemDb();
        List<String> sqlList = new ArrayList<>();
        initCommonMetaCleanSql(sqlList);
        initResetMetaCleanSql(sqlList);
        executeMetaSqlList(sqlList);
        flushDNLogs();
    }

    private List<String> initCommonMetaCleanSql(List<String> sqlList) {
        String clusterId = getString(CLUSTER_ID);
        String groupName = getGroupName();
        String CDC_META_TABLE_RESET_1 =
            String.format("delete from binlog_storage_history_detail where cluster_id = '%s';", clusterId);
        String CDC_META_TABLE_RESET_2 =
            String.format("delete from binlog_oss_record where cluster_id = '%s' and group_id = '%s';", clusterId,
                groupName);
        String CDC_META_TABLE_RESET_3 =
            String.format("delete from binlog_phy_ddl_history where cluster_id = '%s';", clusterId);
        String CDC_META_TABLE_RESET_4 =
            String.format("delete from binlog_polarx_command where %s;", getCmdIdCondition());
        String CDC_META_TABLE_RESET_5 =
            String.format("delete from binlog_storage_history where cluster_id = '%s';", clusterId);
        String CDC_META_TABLE_RESET_6 =
            String.format("delete from binlog_schedule_history where cluster_id = '%s';", clusterId);
        String CDC_META_TABLE_RESET_7 =
            String.format("delete from binlog_task_config where cluster_id ='%s';", clusterId);
        String CDC_META_TABLE_RESET_8 =
            String.format("delete from binlog_x_stream where group_name = '%s';", groupName);
        String CDC_META_TABLE_RESET_9 =
            String.format("delete from binlog_x_table_stream_mapping where cluster_id = '%s';", clusterId);
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

        if (DynamicApplicationConfig.getClusterType().equals(ClusterType.BINLOG_X.name())) {
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
        logger.info("cdc instruction table is reset");
    }

    private void executeMetaSqlList(List<String> sqlList) {
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        try {
            transactionTemplate.execute(t -> {
                for (String sql : sqlList) {
                    logger.info("execute sql : " + sql);
                    metaTemplate.execute(sql);
                }
                logger.info("meta table and system parameters is reset.");
                return null;
            });
        } catch (Exception e) {
            logger.error("reset meta failed!", e);
            throw new RuntimeException("reset meta failed!", e);
        }
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
        DynamicApplicationConfig.setValue(clusterKeysPattern + ConfigKeys.BINLOGX_AUTO_INIT, false + "");
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

    @GET
    @Path("/stop")
    @Leader
    public String stop() {
        logger.info("receive stop master command");

        try {
            SystemDbConfig.upsertSystemDbConfig(ConfigKeys.CLUSTER_EXECUTION_INSTRUCTION,
                ClusterExecutionInstruction.STOP_EXECUTION_INSTRUCTION);
            waitStop();

            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            logger.error("stop master error", e);
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        }
    }

    @GET
    @Path("/start")
    @Leader
    public String start() {
        logger.info("receive start master command");

        try {
            SystemDbConfig.upsertSystemDbConfig(ConfigKeys.CLUSTER_EXECUTION_INSTRUCTION,
                ClusterExecutionInstruction.START_EXECUTION_INSTRUCTION);
            waitStart();

            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            logger.error("start master error", e);
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        }
    }

    @GET
    @Path("/restart")
    @Leader
    public String restart() {
        logger.info("receive restart master command");

        try {
            logger.info("try to stop master");
            SystemDbConfig.upsertSystemDbConfig(ConfigKeys.CLUSTER_EXECUTION_INSTRUCTION,
                ClusterExecutionInstruction.STOP_EXECUTION_INSTRUCTION);
            waitStop();

            logger.info("try to start master");
            SystemDbConfig.upsertSystemDbConfig(ConfigKeys.CLUSTER_EXECUTION_INSTRUCTION,
                ClusterExecutionInstruction.START_EXECUTION_INSTRUCTION);
            waitStart();

            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            logger.error("restart master error", e);
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        }
    }

    @GET
    @Path("/rebalance")
    @Leader
    public String rebalance() {
        logger.info("receive rebalance master command");

        try {
            logger.info("try to rebalance master");
            SystemDbConfig.upsertSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION,
                ClusterRebalanceInstruction.SET_REBALANCE_INSTRUCTION);

            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            return JSON.toJSONString(res);
        } catch (Exception e) {
            logger.error("rebalance master error", e);
            ResultCode<Object> res =
                ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
            return JSON.toJSONString(res);
        }
    }

    @GET
    @Path("/getVersion")
    public String getVersion() {
        String result = "";
        // 用例中使用变量
        String releaseNotePath = DynamicApplicationConfig.getString(ConfigKeys.RELEASE_NOTE_PATH);
        try {
            java.nio.file.Path path = Paths.get(releaseNotePath);
            String prefix = "t-polardbx-cdc-";
            String endfix = ".noarch.rpm";
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    result = line.substring(prefix.length(), line.indexOf(endfix));
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("get version error", e);
        }
        return result;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void waitStop() throws Exception {
        ExecutorService es = Executors.newSingleThreadExecutor();
        TimeLimiter timeLimiter = SimpleTimeLimiter.create(es);
        Duration timeout =
            Duration.ofSeconds(DynamicApplicationConfig.getLong(ConfigKeys.DAEMON_WAIT_TASK_STOP_TIMEOUT_SECOND));

        try {
            timeLimiter.callWithTimeout(() -> {
                String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
                while (true) {
                    // wait all tasks stop
                    List<BinlogTaskInfo> taskInfos = getTaskInfo(clusterId);
                    Optional<BinlogTaskInfo> anyRunningTask =
                        taskInfos.stream().filter(t -> t.getStatus().equals(BinlogTaskStatus.RUNNING.ordinal()))
                            .findAny();
                    if (anyRunningTask.isPresent()) {
                        continue;
                    }

                    // wait all dumpers stop
                    List<DumperInfo> dumperInfos = getBinlogDumperInfo(clusterId);
                    Optional<DumperInfo> anyRunningDumper =
                        dumperInfos.stream().filter(d -> d.getStatus().equals(BinlogTaskStatus.RUNNING.ordinal()))
                            .findAny();
                    if (!anyRunningDumper.isPresent()) {
                        break;
                    }
                }

                // we don't need the result, but we must return one object
                return null;
            }, timeout);
        } catch (Exception e) {
            logger.error("wait stop error", e);
            es.shutdownNow();
            throw new Exception(e);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void waitStart() throws Exception {
        ExecutorService es = Executors.newSingleThreadExecutor();
        TimeLimiter timeLimiter = SimpleTimeLimiter.create(es);
        Duration timeout =
            Duration.ofSeconds(DynamicApplicationConfig.getLong(ConfigKeys.DAEMON_WAIT_TASK_START_TIMEOUT_SECOND));

        try {
            timeLimiter.callWithTimeout(() -> {
                String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
                while (true) {
                    // wait all tasks start
                    List<BinlogTaskInfo> taskInfos = getTaskInfo(clusterId);
                    Optional<BinlogTaskInfo> anyStopTask =
                        taskInfos.stream().filter(t -> t.getStatus().equals(BinlogTaskStatus.STOPPED.ordinal()))
                            .findAny();
                    if (anyStopTask.isPresent()) {
                        continue;
                    }

                    // wait all dumpers start
                    List<DumperInfo> dumperInfos = getBinlogDumperInfo(clusterId);
                    Optional<DumperInfo> anyStopDumper =
                        dumperInfos.stream().filter(d -> d.getStatus().equals(BinlogTaskStatus.STOPPED.ordinal()))
                            .findAny();
                    if (!anyStopDumper.isPresent()) {
                        break;
                    }
                }

                // we don't need the result, but we must return one object
                return null;
            }, timeout);
        } catch (Exception e) {
            logger.error("wait start error", e);
            es.shutdownNow();
            throw new Exception(e);
        }
    }

    private List<BinlogTaskInfo> getTaskInfo(String clusterId) {
        return taskInfoMapper.select(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
    }

    private List<DumperInfo> getBinlogDumperInfo(String clusterId) {
        return dumperInfoMapper.select(
            s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
    }

    private List<NodeInfo> getSlaveNodeInfo(String clusterId) {
        return nodeInfoMapper.select(
            s -> s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.role, SqlBuilder.isEqualTo("S")));
    }
}
