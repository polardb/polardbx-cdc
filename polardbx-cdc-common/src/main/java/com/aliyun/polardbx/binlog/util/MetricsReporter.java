/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * http相关的工具类，带连接池配置
 */
@Slf4j
public class MetricsReporter {

    /**
     * 将 metrics 发送到 本地 daemon 节点。
     */
    public static void report(List<CommonMetrics> metricsList) {
        try {
            int daemonPort = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT);
            PooledHttpHelper.doPost("http://127.0.0.1:" + daemonPort + "/cdc/reports",
                ContentType.APPLICATION_JSON,
                JSON.toJSONString(metricsList), 1000);
        } catch (URISyntaxException e) {
            log.error("metrics report fail,invalid uri", e);
        } catch (IOException e) {
            log.error("metrics report fail", e);
        }
    }

    /**
     * 将 metrics 发送到 daemon leader 节点。
     * 所有 replica 进程的指标都汇聚到 leader，由 leader 进行全集群聚合。
     */
    public static void leaderReport(List<CommonMetrics> metricsList) {
        try {
            NodeInfoMapper mapper = getObject(NodeInfoMapper.class);
            List<NodeInfo> nodeInfoList = mapper.select(
                s -> s.where(NodeInfoDynamicSqlSupport.clusterId,
                        isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID)))
                    .and(NodeInfoDynamicSqlSupport.role, isEqualTo(NodeRole.MASTER.getName())));
            if (nodeInfoList.isEmpty()) {
                log.error("daemon leader not found, skip metrics report");
                return;
            }
            String url = "http://" + nodeInfoList.get(0).getIp() + ":" + nodeInfoList.get(0).getDaemonPort()
                + "/cdc/reports";
            PooledHttpHelper.doPost(url, ContentType.APPLICATION_JSON,
                JSON.toJSONString(metricsList), 1000);
        } catch (URISyntaxException e) {
            log.error("metrics report fail,invalid uri", e);
        } catch (IOException e) {
            log.error("metrics report fail", e);
        }
    }

    public static void binlogxReport(Map<String, List<CommonMetrics>> metricsList) {
        try {
            int daemonPort = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT);
            PooledHttpHelper.doPost("http://127.0.0.1:" + daemonPort + "/cdc/binlogx/reports",
                ContentType.APPLICATION_JSON,
                JSON.toJSONString(metricsList), 1000);
        } catch (URISyntaxException e) {
            log.error("metrics report fail,invalid uri", e);
        } catch (IOException e) {
            log.error("metrics report fail", e);
        }
    }
}
