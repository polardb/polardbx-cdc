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
}
