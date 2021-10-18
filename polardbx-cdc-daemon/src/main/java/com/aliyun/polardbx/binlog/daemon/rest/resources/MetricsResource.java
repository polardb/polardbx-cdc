/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.daemon.rest.resources;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

@Path("/cdc")
@Singleton
public class MetricsResource {

    private Cache<String, CommonMetrics> cache = CacheBuilder.newBuilder()
        .maximumSize(256)
        .expireAfterWrite(10, TimeUnit.SECONDS)
        .build();

    @GET
    @Path("/metrics")
    @Produces("text/plain;charset=utf-8")
    public String data() throws IOException {
        CollectorRegistry registry = new CollectorRegistry();
        StringWriter writer = new StringWriter();
        cache.asMap().forEach((k, v) -> {
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
        cache.put(metrics.getKey(), metrics);
        return "success";
    }

    @POST
    @Path("/reports")
    @Produces(MediaType.TEXT_PLAIN)
    public String reports(List<CommonMetrics> metricsList) {
        metricsList.forEach(metrics -> {
            cache.put(metrics.getKey(), metrics);
        });
        return "success";
    }
}
