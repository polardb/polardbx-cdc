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

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
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

    private static final Cache<String, CommonMetrics> CACHE = CacheBuilder.newBuilder()
        .maximumSize(1024)
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

    public static CommonMetrics getMetricsByKey(String key) {
        return CACHE.getIfPresent(key);
    }
}
