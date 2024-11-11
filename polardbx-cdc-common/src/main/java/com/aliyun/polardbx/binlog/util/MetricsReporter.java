/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * http相关的工具类，带连接池配置
 */
@Slf4j
public class MetricsReporter {
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
