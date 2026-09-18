/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sun.jersey.spi.resource.Singleton;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Path("/cdc")
@Singleton
@Slf4j
public class MetricsResource {

    /**
     * Replica multi-stream metrics are reported to the daemon leader with a task-id dimension.
     * Keep enough entries to avoid evicting metrics from other tasks between report cycles.
     */
    private static final int METRICS_CACHE_MAX_SIZE = 16 * 1024;

    private static final Cache<String, CommonMetrics> CACHE = CacheBuilder.newBuilder()
        .maximumSize(METRICS_CACHE_MAX_SIZE)
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build();

    private static final Cache<String, Map<String, CommonMetrics>> BINLOG_X_CACHE = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build();

    private static final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor((r) -> new Thread(r, "daemon-metrics-reporter"));

    static {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                List<CommonMetrics> list = new ArrayList<>();

                // jvm
                CommonMetricsHelper.addJvmMetrics(list, JvmUtils.buildJvmSnapshot(), "polardbx_cdc_daemon_");

                // tso heartbeat
                String sql = "select gmt_modified from `__cdc__`.`__cdc_heartbeat__` where id = '1'";
                JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");
                Date latestHeartbeat = template.queryForObject(sql, Date.class);
                if (latestHeartbeat != null) {
                    list.add(CommonMetrics.builder()
                        .key("polardbx_cdc_daemon_tso_heartbeat_delay")
                        .type(1)
                        .value(Math.abs(latestHeartbeat.getTime() - System.currentTimeMillis()))
                        .build());
                }

                //put
                list.forEach(metrics -> {
                    CACHE.put(metrics.getKey(), metrics);
                });

            } catch (Throwable e) {
                log.error("report daemon metrics error!", e);
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    @GET
    @Path("/metrics")
    @Produces("text/plain;charset=utf-8")
    public String data() throws IOException {
        CollectorRegistry registry = new CollectorRegistry();
        StringWriter writer = new StringWriter();
        if (ClusterType.REPLICA.name().equals(DynamicApplicationConfig.getClusterType())) {
            aggregatedReplicaMetrics();
        }
        CACHE.asMap().forEach((k, v) -> {
            CommonMetrics mark = CommonMetricsHelper.getALL().get(k);
            Gauge gauge = Gauge.build().name(k).help(mark == null ? k : mark.getDesc()).register(registry);
            gauge.set(v.getValue());
        });
        TextFormat.writeOpenMetrics100(writer, registry.metricFamilySamples());
        return writer.toString();
    }

    @POST
    @Path("/report")
    @Produces(MediaType.TEXT_PLAIN)
    public String report(CommonMetrics metrics) {
        CACHE.put(metrics.getKey(), metrics);
        return "success";
    }

    @POST
    @Path("/reports")
    @Produces(MediaType.TEXT_PLAIN)
    public String reports(List<CommonMetrics> metricsList) {
        metricsList.forEach(metrics -> {
            CACHE.put(metrics.getKey(), metrics);
        });
        return "success";
    }

    @POST
    @Path("/binlogx/reports")
    @Produces(MediaType.TEXT_PLAIN)
    public String binlogxReports(Map<String, List<CommonMetrics>> metricsMap) {
        metricsMap.forEach((k, v) -> {
            Map<String, CommonMetrics> map = new HashMap<>();
            v.forEach(c -> map.put(c.getKey(), c));
            BINLOG_X_CACHE.put(k, map);
        });
        return "success";
    }

    /**
     * 指标聚合计算类型
     */
    public enum AggregationType {
        /**
         * 取最大值
         */
        MAX,
        /**
         * 求和
         */
        SUM
    }

    /**
     * 聚合查询 replica 指标，按 task type 分组。
     * <p>
     * 仅在 daemon leader 节点上执行全集群聚合，非 leader 节点直接返回空列表。
     * 对每组内所有任务的指标进行聚合计算，输出 key 为 "replica_{type}_{suffix}"。
     * 如果某个 type 组存在任一任务的指标缺失（进程异常/缓存过期），则该组不返回结果。
     * 聚合成功后将结果 put 回 CACHE，供 /metrics 接口对外暴露。
     */
    @GET
    @Path("/replica/aggregatedMetrics")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CommonMetrics> aggregatedReplicaMetrics() {
        // 非 leader 节点直接返回空列表，聚合仅在 leader 执行
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            return new ArrayList<>();
        }

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);

        // 查询全集群所有 RUNNING 和 READY 状态的任务
        List<RplTask> runningTasks = DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, clusterId);
        runningTasks.addAll(DbTaskMetaManager.listClusterTask(TaskStatus.READY, clusterId));

        // 按 type 分组
        Map<String, List<RplTask>> typeToTasks = new HashMap<>();
        for (RplTask task : runningTasks) {
            typeToTasks.computeIfAbsent(task.getType(), k -> new ArrayList<>()).add(task);
        }

        List<CommonMetrics> result = new ArrayList<>();

        CommonMetrics aggregated = aggregateMetric(typeToTasks.get(ServiceType.REPLICA_INC.name()),
            ServiceType.REPLICA_INC.name(), AggregationType.MAX, "trueDelayMills");
        if (aggregated != null) {
            result.add(aggregated);
        }

        return result;
    }

    public void invalidateAll() {
        CACHE.invalidateAll();
    }

    /**
     * 对指定 type 的任务列表，按给定聚合方式计算单个指标的聚合值。
     * <p>
     * 从 CACHE 中按 "replica_{type}_{taskId}_{suffix}" 逐个读取每个任务的指标值，
     * 任一缺失则返回 null；全部齐全则按 aggregationType 聚合，
     * 聚合结果以 "replica_{type}_{suffix}" 为 key 写回 CACHE 并返回。
     *
     * @param tasks 同一 type 下的任务列表
     * @param type 任务类型，如 "REPLICA_INC"
     * @param aggregationType 聚合计算方式（MAX / SUM）
     * @param suffix 指标后缀，如 "trueDelayMills"
     * @return 聚合后的 CommonMetrics，若任一任务指标缺失则返回 null
     */
    static CommonMetrics aggregateMetric(List<RplTask> tasks, String type,
                                         AggregationType aggregationType, String suffix) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        double aggregatedValue = aggregationType == AggregationType.MAX ? -Double.MAX_VALUE : 0;
        String targetKey = "replica_" + type + "_" + suffix;

        for (RplTask task : tasks) {
            String cacheKey = "replica_" + type + "_" + task.getId() + "_" + suffix;
            CommonMetrics metrics = CACHE.getIfPresent(cacheKey);
            if (metrics == null) {
                // 如果存在某一个 task 没有对应的监控值，那么整个聚合结果都算无效，将 Cache 内的值抹除
                CACHE.invalidate(targetKey);
                return null;
            }
            switch (aggregationType) {
            case MAX:
                aggregatedValue = Math.max(aggregatedValue, metrics.getValue());
                break;
            case SUM:
                aggregatedValue += metrics.getValue();
                break;
            default:
                break;
            }
        }

        CommonMetrics result = CommonMetrics.builder()
            .key("replica_" + type + "_" + suffix)
            .type(1)
            .value(aggregatedValue)
            .build();
        CACHE.put(result.getKey(), result);
        return result;
    }

    public static CommonMetrics getMetricsByKey(String key) {
        return CACHE.getIfPresent(key);
    }
}
