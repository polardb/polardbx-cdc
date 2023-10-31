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
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.cdc.meta.MetaType;
import com.aliyun.polardbx.binlog.dao.BinlogLabEventMapper;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creatd by chengjin
 */
@Slf4j
public class LabTestJob extends AbstractBinlogTimerTask {

    private List<AbstractTestJob> jobList = new ArrayList<>();

    public LabTestJob(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
        jobList.add(new RandomFlushJob(TimeUnit.MINUTES.toMillis(5), false));
    }

    @Override
    public void exec() {
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            return;
        }
        if (!StringUtils.equals(DynamicApplicationConfig.getClusterRole(), ClusterRole.master.name())) {
            return;
        }
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String startCmd = CommonUtils.buildStartCmd();
        String instructionId = clusterId + ":" + startCmd;
        BinlogLogicMetaHistoryMapper binlogLogicMetaHistoryMapper =
            SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        List<BinlogLogicMetaHistory> historyList =
            binlogLogicMetaHistoryMapper.select(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.instructionId,
                    SqlBuilder.isEqualTo(instructionId))
                .and(BinlogLogicMetaHistoryDynamicSqlSupport.type, SqlBuilder.isEqualTo(
                    MetaType.SNAPSHOT.getValue())));
        log.info("lab test job query logic meta history size : " + historyList.size() + ", instructionId : "
            + instructionId);
        if (historyList.isEmpty()) {
            log.warn("cdc cluster " + instructionId + " not start, ignore lab test job");
            // cluster not init
            return;
        }
        long now = System.currentTimeMillis();
        for (AbstractTestJob job : jobList) {
            job.exec(now);
        }

    }

    abstract class AbstractTestJob {
        long jobInterval;
        boolean random;
        private long lastExecTimestamp;

        /**
         * 加个次数判断，保障任务至少会被执行一次
         */
        private AtomicLong execCount = new AtomicLong();

        public AbstractTestJob(long jobInterval, boolean random) {
            this.jobInterval = jobInterval;
            this.random = random;
        }

        final void exec(long now) {
            if (now - lastExecTimestamp < jobInterval) {
                return;
            }
            if (random && execCount.get() > 0 && !RandomUtils.nextBoolean()) {
                return;
            }
            try {
                doExec();
                execCount.incrementAndGet();
            } catch (Throwable t) {
                log.error("exec sub test job error !", t);
            }
            lastExecTimestamp = now;
        }

        abstract void doExec();
    }

    class RandomFlushJob extends AbstractTestJob {

        public RandomFlushJob(long jobInterval, boolean random) {
            super(jobInterval, random);
        }

        @Override
        void doExec() {
            if (!DynamicApplicationConfig.getBoolean(ConfigKeys.DAEMON_AUTO_FLUSH_LOG_TEST)) {
                return;
            }
            String clusterType = DynamicApplicationConfig.getClusterType();

            if (StringUtils.equals(clusterType, ClusterType.BINLOG.name())) {
                if (checkBefore(clusterType)) {
                    return;
                }
                execOnCn("flush logs");
                LabEventManager.logEvent(LabEventType.SCHEDULE_TRIGGER_FLUSH_LOGS, clusterType);
            } else if (StringUtils.equalsIgnoreCase(clusterType, ClusterType.BINLOG_X.name())) {
                String group = DynamicApplicationConfig.getString(ConfigKeys.BINLOGX_STREAM_GROUP_NAME);
                if (checkBefore(clusterType)) {
                    return;
                }
                execOnCn("flush logs with " + group);
                LabEventManager.logEvent(LabEventType.SCHEDULE_TRIGGER_FLUSH_LOGS, clusterType);
            }
        }

        boolean checkBefore(String params) {
            BinlogLabEventMapper mapper = SpringContextHolder.getObject(BinlogLabEventMapper.class);
            int triggerCount = mapper.countEventWithParams(LabEventType.SCHEDULE_TRIGGER_FLUSH_LOGS.ordinal(), params);
            int flushCount = mapper.countEventWithParams(LabEventType.FLUSH_LOGS.ordinal(), params);
            if (StringUtils.equals(DynamicApplicationConfig.getClusterType(), ClusterType.BINLOG_X.name())) {
                int streamCount = DynamicApplicationConfig.getInt(ConfigKeys.BINLOGX_STREAM_COUNT);
                triggerCount *= streamCount;
            }
            return triggerCount > flushCount;
        }

        void execOnCn(String ddl) {
            JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
            jdbcTemplate.execute(ddl);
        }
    }

}
