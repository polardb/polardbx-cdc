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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.system.InstructionType;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
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

    private static final String CDC_POLARX_RESET_SQL_1 = "truncate __cdc__.__cdc_instruction__";
    private static final String CDC_POLARX_RESET_SQL_2 = "truncate __cdc__.__cdc_ddl_record__";

    private static final String CDC_METADB_RESET_1 = "truncate binlog_logic_meta_history";
    private static final String CDC_METADB_RESET_2 = "truncate binlog_oss_record";
    private static final String CDC_METADB_RESET_3 = "truncate binlog_phy_ddl_history";
    private static final String CDC_METADB_RESET_4 = "truncate binlog_polarx_command";
    private static final String CDC_METADB_RESET_5 = "truncate binlog_storage_history";
    private static final String CDC_METADB_RESET_6 = "truncate binlog_schedule_history";
    private static final String CDC_METADB_RESET_7 =
        String.format("delete from binlog_system_config where config_key='%s';",
            EXPECTED_STORAGE_TSO_KEY);
    private static final String CDC_METADB_RESET_8 =
        String.format("delete from binlog_system_config where config_key='%s';",
            CLUSTER_SNAPSHOT_VERSION_KEY);
    private static final String CDC_METADB_RESET_9 =
        String.format("delete from binlog_system_config where config_key='%s';",
            CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY);
    private static final String CDC_METADB_RESET_10 =
        String.format("delete from binlog_system_config where config_key='%s';",
            CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY);
    private static final String CDC_METADB_RESET_11 = "truncate binlog_semi_snapshot";
    private static final String CDC_METADB_RESET_12 = "truncate binlog_task_config";
    private static final String CDC_METADB_RESET_13 = "truncate binlog_phy_ddl_hist_clean_point";

    private static final String QUERY_VIP_STORAGE =
        "select * from storage_info where inst_kind=0 and is_vip = 1 and storage_inst_id = '%s' limit 1";
    private static final String QUERY_STORAGE_LIMIT_1 =
        "select * from storage_info where inst_kind=0  and storage_inst_id = '%s' limit 1";

    private static final String TRANSACTION_POLICY = "set drds_transaction_policy='TSO'";

    private static String SEND_CONFIG_UPDATE_CMND =
        "insert into __cdc_instruction__(INSTRUCTION_TYPE, INSTRUCTION_CONTENT, INSTRUCTION_ID) values(?,?,?)";

    @GET
    @Path("/reset")
    public String reset() {
        //
        JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");
        template.execute(CDC_POLARX_RESET_SQL_1);
        template.execute(CDC_POLARX_RESET_SQL_2);
        logger.info("polarx is reset.");

        //
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaTemplate.execute(CDC_METADB_RESET_1);
        metaTemplate.execute(CDC_METADB_RESET_2);
        metaTemplate.execute(CDC_METADB_RESET_3);
        metaTemplate.execute(CDC_METADB_RESET_4);
        metaTemplate.execute(CDC_METADB_RESET_5);
        metaTemplate.execute(CDC_METADB_RESET_6);
        metaTemplate.execute(CDC_METADB_RESET_7);
        metaTemplate.execute(CDC_METADB_RESET_8);
        metaTemplate.execute(CDC_METADB_RESET_9);
        metaTemplate.execute(CDC_METADB_RESET_10);
        metaTemplate.execute(CDC_METADB_RESET_11);
        metaTemplate.execute(CDC_METADB_RESET_12);
        metaTemplate.execute(CDC_METADB_RESET_13);
        logger.info("metadb is reset.");

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

        return "成功";
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
}
