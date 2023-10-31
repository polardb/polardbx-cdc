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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 监控是否有下游消费，记录最近一次消费的时间，主要用于报警
 */
@Slf4j
public class BinlogConsumerMonitor extends AbstractBinlogTimerTask {
    private final JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");

    public BinlogConsumerMonitor(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        try {
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                if (log.isDebugEnabled()) {
                    log.debug("current daemon is not a leader, skip the cluster's topology project!");
                }
                return;
            }
            checkIfExistsConsumer();
        } catch (Throwable th) {
            throw new PolardbxException("check consumer fail", th);
        }
    }

    private void checkIfExistsConsumer() {
        List<Map<String, Object>> list =
            polarxJdbcTemplate.queryForList("show processlist where Command='Binlog Dump'");
        if (!list.isEmpty()) {
            String time = Long.valueOf(System.currentTimeMillis()).toString();
            log.info("checkIfExistsConsumer: latest consume time : {}", time);
            DynamicApplicationConfig.setValue(ConfigKeys.ALARM_LATEST_CONSUME_TIME_MS, time);
        }
    }
}
