/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.google.common.annotations.VisibleForTesting;
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
    private final JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");

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

    String checkIfExistsConsumer() {
        // 普通消费
        List<Map<String, Object>> list1 =
            polarxJdbcTemplate.queryForList("show processlist where Command='Binlog Dump'");
        // 列存消费
        boolean tableExists = metaTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE "
            + "table_schema = 'polardbx_meta_db' AND table_name = 'columnar_table_mapping'", Integer.class) > 0;
        int list2Num = tableExists ?
            metaTemplate.queryForObject("select count(*) from columnar_table_mapping", Integer.class) : 0;
        if (!list1.isEmpty() || list2Num > 0) {
            String time = Long.valueOf(System.currentTimeMillis()).toString();
            log.info("checkIfExistsConsumer: latest consume time : {}", time);
            DynamicApplicationConfig.setValue(ConfigKeys.ALARM_LATEST_CONSUME_TIME_MS, time);
            return time;
        }
        return null;
    }
}
