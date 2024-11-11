/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.AlarmEvent;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class AlarmEventReporter {
    public static void report(AlarmEvent alarmEvent) {
        try {
            int daemonPort = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT);
            PooledHttpHelper.doPost("http://127.0.0.1:" + daemonPort + "/events/report",
                ContentType.APPLICATION_JSON,
                JSON.toJSONString(alarmEvent), 1000);
        } catch (URISyntaxException e) {
            log.error("alarm event report fail,invalid uri", e);
        } catch (IOException e) {
            log.error("alarm event report fail", e);
        }
    }
}
