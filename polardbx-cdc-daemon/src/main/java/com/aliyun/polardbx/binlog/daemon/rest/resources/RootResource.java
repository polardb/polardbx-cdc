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

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.rest.entities.ServerInfo;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Path("/")
@Singleton
public class RootResource {

    private static final Logger logger = LoggerFactory.getLogger(RootResource.class);

    @GET
    @Path("/")
    public String serverInfo() {
        logger.info("receive a request for root.");
        return "";
    }

    @GET
    @Path("/getCursors")
    public List<Map<String, Object>> getCursors() {
        logger.info("receive a request for getting cursors.");

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        List<NodeInfo> nodeInfoList = nodeInfoMapper.select(s -> s
            .where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        return nodeInfoList.stream()
            .map(n -> {
                Map<String, Object> map = new HashMap<>();
                Cursor cursor = StringUtils.isNotBlank(n.getLatestCursor()) ?
                    JSONObject.parseObject(n.getLatestCursor(), Cursor.class) : null;
                map.put("fileName", cursor != null ? cursor.getFileName() : "");
                map.put("filePosition", cursor != null ? cursor.getFilePosition() : "");
                map.put("containerId", n.getContainerId());
                return map;
            }).collect(Collectors.toList());
    }

    @GET
    @Path("/status")
    public String status() {
        logger.info("receive a request for status.");
        String clusterType = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_TYPE);
        if (StringUtils.isBlank(clusterType) || ClusterTypeEnum.BINLOG.name().equals(clusterType)) {
            return check4GlobalBinlog();
        } else {
            return "OK";
        }
    }

    private String check4GlobalBinlog() {
        boolean status;
        String cause = "";

        try {
            ServerInfo serverInfo = buildServerInfo();

            // 兼容性逻辑，调度重构之前的版本，NodeInfo的role字段为空，为了保证平滑升级，验证时将role为空的node排除
            List<String> roleEmptyNodes =
                serverInfo.getNodeInfo().stream().map(NodeInfo::getRole).filter(StringUtils::isBlank)
                    .collect(Collectors.toList());
            if (!roleEmptyNodes.isEmpty()) {
                return "OK";
            }

            //topology config size
            status = serverInfo.getTopologyConfig().size() > 0;
            if (!status) {
                cause += "Topology config is not ready;";
            }

            //node info
            status = serverInfo.getTopologyConfig().stream()
                .anyMatch(n -> StringUtils
                    .equals(n.getContainerId(), DynamicApplicationConfig.getString(ConfigKeys.INST_ID)));
            if (!status) {
                cause += "Node is not in topology config;";
            }

            //dumper count
            Set<String> expectedDumpers = serverInfo.getTopologyConfig().stream()
                .filter(c -> TaskType.Dumper.name().equals(c.getRole())).map(BinlogTaskConfig::getTaskName)
                .collect(Collectors.toSet());
            Set<String> actualDumpers =
                serverInfo.getDumperInfo().stream().map(DumperInfo::getTaskName).collect(Collectors.toSet());
            status = expectedDumpers.equals(actualDumpers);
            if (!status) {
                cause += String.format("Dumper count is not ready, current running dumpers is %s ,"
                    + " expected dumpers is %s;", actualDumpers, expectedDumpers);
            }

            //task count
            Set<String> expectedTasks = serverInfo.getTopologyConfig().stream()
                .filter(t -> TaskType.Final.name().equals(t.getRole()) || TaskType.Relay.name().equals(t.getRole()))
                .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
            Set<String> actualTasks = serverInfo.getTaskInfo().stream().map(RelayFinalTaskInfo::getTaskName).collect(
                Collectors.toSet());
            status = expectedTasks.equals(actualTasks);
            if (!status) {
                cause += String.format("Task count is not ready, current running tasks is %s , expected tasks is %s ;",
                    actualTasks, expectedTasks);
            }

            //dumper heartbeat
            long time1 = System.currentTimeMillis();
            status = serverInfo.getDumperInfo().stream()
                .allMatch(d -> (time1 - d.getGmtHeartbeat().getTime())
                    < DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_TASK_HEARTBEAT_INTERVAL) * 2);
            if (!status) {
                cause +=
                    "Dumper heartbeat time is abnormal, current time is " + time1 + ", heartbeat time is "
                        + serverInfo
                        .getDumperInfo().stream().map(DumperInfo::getGmtHeartbeat).map(
                            Date::getTime).collect(Collectors.toList()) + ";";
            }

            //task heartbeat
            long time2 = System.currentTimeMillis();
            status = serverInfo.getTaskInfo().stream()
                .allMatch(t -> (time2 - t.getGmtHeartbeat().getTime())
                    < DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_TASK_HEARTBEAT_INTERVAL) * 2);
            if (!status) {
                cause +=
                    "Task heartbeat is abnormal, current time is " + time2 + ", heartbeat time is " + serverInfo
                        .getTaskInfo().stream().map(
                            RelayFinalTaskInfo::getGmtHeartbeat).map(Date::getTime).collect(Collectors.toList())
                        + ";";
            }

            // 如果前面已经失败了，那么下面更高阶的判断就没必要继续了(主要是比较耗时)
            if (StringUtils.isBlank(cause)) {
                checkDumperCursorAligned();
                JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
                jdbcTemplate.execute("show binary logs");
            }

            if (StringUtils.isNotBlank(cause)) {
                logger.info("server status is abnormal, the cause is " + cause);
            } else {
                logger.info("server status is ok.");
            }

            return StringUtils.isBlank(cause) ? "OK" : "ERROR : " + cause;
        } catch (Throwable t) {
            logger.info("something goes wrong when build status.", t);
            cause += t.getMessage();
            return "ERROR : " + cause;
        }
    }

    private void checkDumperCursorAligned() throws Throwable {
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
        int timeoutThresholdMs = DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_HEARTBEAT_TIMEOUT_MS);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        RetryTemplate template = RetryTemplate.builder().maxAttempts(15).fixedBackoff(1000)
            .retryOn(RetryableException.class).build();

        final Set<Cursor> allCursors = template.execute((RetryCallback<Set<Cursor>, Throwable>) retryContext -> {
            List<NodeInfo> nodeInfoList = nodeInfoMapper
                .select(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(NodeInfoDynamicSqlSupport.gmtHeartbeat,
                        SqlBuilder.isGreaterThan(DateTime.now().minusMillis(timeoutThresholdMs).toDate())));

            if (!nodeInfoList.stream().allMatch(s -> StringUtils.isNotBlank(s.getLatestCursor()))) {
                log.warn("wait for log cursor ready failed, ready dumpers are {}",
                    nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toList()));
                throw new RetryableException("wait for all dumpers ready failed.");
            }

            return nodeInfoList.stream()
                .map(NodeInfo::getLatestCursor)
                .map(i -> JSONObject.parseObject(i, Cursor.class))
                .collect(Collectors.toSet());
        });

        template.execute((RetryCallback<Boolean, Throwable>) retryContext -> {
            Cursor lastMaxCursor = allCursors.stream().max(Comparator.comparing(s -> s)).get();
            Cursor thisCursor = nodeInfoMapper
                .select(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(instId)))
                .stream().map(NodeInfo::getLatestCursor).map(i -> JSONObject.parseObject(i, Cursor.class))
                .findFirst().get();

            if (thisCursor.compareTo(lastMaxCursor) >= 0) {
                return true;
            } else {
                throw new RetryableException(
                    "cursor is not ready, max cursor is {" + lastMaxCursor + "}, min cursor is {" + thisCursor
                        + "}.");
            }
        });
    }

    private ServerInfo buildServerInfo() {
        BinlogTaskConfigMapper binlogTaskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        RelayFinalTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(RelayFinalTaskInfoMapper.class);
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String polarxInstanceId = DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID);

        List<BinlogTaskConfig> topologyConfigs =
            binlogTaskConfigMapper.select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId,
                SqlBuilder.isEqualTo(clusterId)));
        List<NodeInfo> nodeInfoList = nodeInfoMapper
            .select(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(0)));
        List<RelayFinalTaskInfo> taskInfoList = taskInfoMapper
            .select(s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
        List<DumperInfo> dumperInfoList = dumperInfoMapper
            .select(s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setTopologyConfig(topologyConfigs);
        serverInfo.setClusterId(clusterId);
        serverInfo.setPolarxInstanceId(polarxInstanceId);
        serverInfo.setNodeInfo(nodeInfoList);
        serverInfo.setTaskInfo(taskInfoList);
        serverInfo.setDumperInfo(dumperInfoList);

        return serverInfo;
    }
}
