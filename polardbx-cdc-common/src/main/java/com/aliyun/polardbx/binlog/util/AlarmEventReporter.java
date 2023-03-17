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
