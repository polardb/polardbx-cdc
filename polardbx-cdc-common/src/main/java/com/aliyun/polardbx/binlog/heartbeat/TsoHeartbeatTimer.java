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
package com.aliyun.polardbx.binlog.heartbeat;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.InstructionType;
import com.aliyun.polardbx.binlog.PolarxCommandType;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapperExt;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.IScheduleJob;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_EPS_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

;

/**
 * 心跳是以rds实例为单位的，每个RDS每次更新一次 create table __drds_heartbeat__( id bigint(20)
 * primary key auto_increment, sname varchar(10), gmt_modified datetime(3) )
 * dbpartition by hash(id);
 *
 * @author chengjin.lyf on 2020/8/20 7:52 下午
 * @since 1.0.25
 */
public class TsoHeartbeatTimer implements Runnable, IScheduleJob {

    private static final Logger logger = LoggerFactory.getLogger(TsoHeartbeatTimer.class);

    private static final String TSO_HEARTBEAT_LEADER_LOCK = "TSO_HEARTBEAT_LEADER_LOCK";
    private static final String TRANSACTION_POLICY = "set drds_transaction_policy='TSO'";
    private static final String CREATE_HEARTBEAT_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS `__cdc__`.`__cdc_heartbeat__` "
            + "( `id` bigint(20) NOT NULL AUTO_INCREMENT BY GROUP, "
            + "`sname` varchar(10) DEFAULT NULL, "
            + "`gmt_modified` datetime(3) DEFAULT NULL, "
            + "PRIMARY KEY (`id`) ) "
            + "BROADCAST ENGINE = InnoDB CHARSET = utf8mb4";
    private static final String UPDATE_SQL =
        "replace into `__cdc__`.`__cdc_heartbeat__`(id, sname, gmt_modified) values(1, 'heartbeat', '%s')";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(
        r -> new Thread(r, "cdc_heartbeat"));
    private final AtomicBoolean heartbeatTableInitFlag = new AtomicBoolean(false);
    private final JdbcTemplate template;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final EpsHolder epsHolder;
    private long currentHeartBeatInterval;
    private Alarm alarm;
    private MetricsProvider metricsProvider;
    private long lastCheckAdjustTime;
    private AtomicBoolean repairedCdcSystemTable = new AtomicBoolean(false);

    public TsoHeartbeatTimer() {
        this.template = SpringContextHolder.getObject("polarxJdbcTemplate");
        this.transactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
        this.currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_INTERVAL_MS);
        this.epsHolder = new EpsHolder();
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        logger.info("start tso heartbeat now!");
        executorService.submit(this);
    }

    @Override
    @SneakyThrows
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executorService.shutdownNow();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        logger.info("tso heartbeat stopped!");
    }

    @SneakyThrows
    @Override
    public void run() {
        while (true) {
            if (!running.get()) {
                return;
            }
            try {
                heartbeat();
                tryAdjustInterval();
                initCdcSystemTable();
                errorCount.set(0);
            } catch (Throwable e) {
                logger.error(" execute heartbeat failed ! ", e);
                if (alarm != null && errorCount.incrementAndGet() > 15) {
                    alarm.send(e);
                }
            } finally {
                Thread.sleep(currentHeartBeatInterval);
            }
        }
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }

    public MetricsProvider getMetricsProvider() {
        return metricsProvider;
    }

    public void setMetricsProvider(MetricsProvider metricsProvider) {
        this.metricsProvider = metricsProvider;
    }

    private void heartbeat() {
        String clusterRole = DynamicApplicationConfig.getClusterRole();
        String splitFlag = "_";
        if (RuntimeLeaderElector.isLeader(TSO_HEARTBEAT_LEADER_LOCK + splitFlag + clusterRole)) {
            try {
                if (heartbeatTableInitFlag.compareAndSet(false, true)) {
                    template.execute(CREATE_HEARTBEAT_TABLE_SQL);
                }
            } catch (Throwable t) {
                heartbeatTableInitFlag.compareAndSet(true, false);
                throw t;
            }

            if (ClusterRole.slave.name().equals(clusterRole)) {
                NodeInfoMapperExt nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapperExt.class);
                if (nodeInfoMapper
                    .checkHasAvaliableTsoHeartbeat(TimeUnit.MILLISECONDS.toMicros(currentHeartBeatInterval)) > 0) {
                    return;
                }
            }

            transactionTemplate.execute((o) -> transactionTemplate.execute(transactionStatus -> {
                long now = System.currentTimeMillis();
                String nowFormat = DateFormatUtils.format(now, "yyyy-MM-dd HH:mm:ss.SSS");
                String sql = String.format(UPDATE_SQL, nowFormat);
                template.execute(TRANSACTION_POLICY);
                template.execute(sql);
                return null;
            }));
            updateNodeHeartbeatTimestamp();

        }
    }

    private void tryAdjustInterval() {
        boolean selfAdaption = DynamicApplicationConfig.getBoolean(DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_ENABLE);
        if (selfAdaption && metricsProvider != null && System.currentTimeMillis() - lastCheckAdjustTime >= 1000) {
            double latestEps = 0;

            CommonMetrics m1 = metricsProvider.get("polardbx_cdc_dumper_m_eps");
            if (m1 == null) {
                CommonMetrics m2 = metricsProvider.get("polardbx_cdc_dumper_s_eps");
                if (m2 != null) {
                    latestEps = m2.getValue();
                }
            } else {
                latestEps = m1.getValue();
            }

            double epsAverage = epsHolder.calcAverage(latestEps);
            long originValue = currentHeartBeatInterval;
            if (epsAverage > getInt(DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_EPS_THRESHOLD)) {
                currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL);
            } else {
                currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_INTERVAL_MS);
            }

            if (originValue != currentHeartBeatInterval) {
                logger.info("tso heartbeat interval is changed from {} to {} , with eps {}.", originValue,
                    currentHeartBeatInterval, epsAverage);
            }

            lastCheckAdjustTime = System.currentTimeMillis();
        }
    }

    private void initCdcSystemTable() {
        if (repairedCdcSystemTable.get()) {
            return;
        }
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (!StringUtils
            .equalsAny(clusterType, ClusterType.BINLOG.name(), ClusterType.BINLOG_X.name())) {
            return;
        }
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            return;
        }
        // try repair cdc system table
        JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");

        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_ddl_record__").
            targetColumnType("LONGTEXT").
            defaultNull().
            modifyColumn("META_INFO").
            execute();

        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_ddl_record__").
            targetColumnType("MEDIUMTEXT").
            defaultNull().
            modifyColumn("EXT").
            execute();

        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_instruction__").
            targetColumnType("longtext").
            notNull().
            modifyColumn("INSTRUCTION_CONTENT").
            execute();

        new AlterTableAddIndexExecutor(template).
            tableName("__cdc_ddl_record__").
            indexName("idx_gmt_created").
            addIndexColumn("GMT_CREATED").
            execute();

        new AlterTableAddIndexExecutor(template).
            tableName("__cdc_ddl_record__").
            indexName("idx_job_id").
            addIndexColumn("JOB_ID").
            execute();

        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_instruction__").
            targetColumnType("VARCHAR(120)").
            notNull().
            modifyColumn("INSTRUCTION_ID").
            execute();

        repairInstruction();
        repairedCdcSystemTable.set(true);

    }

    /**
     * 修复 instruction 表兼容性
     * 1、 加cluster_id字段
     * 2、 订正数据
     * 3、 修改索引
     * 为了避免出现数据冲突和并发插入数据的情况，在发现有集群没有初始化成功，暂不进行修复
     */
    private void repairInstruction() {
        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_instruction__").
            targetColumnType("varchar(64)").
            defaultValue("0").
            addColumn("CLUSTER_ID").execute();

        new AlterTableModifyColumnExecutor(template).
            tableName("__cdc_instruction__").
            targetColumnType("varchar(64)").
            defaultValue("0").
            modifyColumn("CLUSTER_ID").execute();

        BinlogPolarxCommandMapper polarxCommandMapper = SpringContextHolder.getObject(BinlogPolarxCommandMapper.class);

        List<BinlogPolarxCommand> unSuccessCommandList = polarxCommandMapper.select(s -> s.where(
            BinlogPolarxCommandDynamicSqlSupport.cmdStatus, SqlBuilder.isNotEqualTo(1L)).and(
            BinlogPolarxCommandDynamicSqlSupport.cmdType, SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())
        ));

        if (!unSuccessCommandList.isEmpty()) {
            // 如果有当前未启动成功的集群，暂时不进行订正
            return;
        }

        List<Map<String, Object>> dataMapList = template.queryForList(
            "select ID,INSTRUCTION_ID,CLUSTER_ID from __cdc_instruction__ where INSTRUCTION_TYPE='"
                + InstructionType.CdcStart.name()
                + "' and CLUSTER_ID IS NOT NULL and CLUSTER_ID != '0' ");

        if (!dataMapList.isEmpty()) {
            for (Map<String, Object> dataMap : dataMapList) {
                String instructionId = (String) dataMap.get("INSTRUCTION_ID");
                String id = String.valueOf(dataMap.get("ID"));
                String clusterId = (String) dataMap.get("CLUSTER_ID");
                if (!instructionId.contains(":")) {
                    instructionId = clusterId + ":" + instructionId;
                    template.update(
                        "update __cdc_instruction__ set INSTRUCTION_ID = '" + instructionId
                            + "' where ID = '"
                            + id + "'");
                    logger.warn("repair  __cdc_instruction__ with instruction_id = " + instructionId);
                }
            }
        }

        new AlterTableAddIndexExecutor(template).
            tableName("__cdc_instruction__").
            indexName("uk_instruction_id_type").
            addIndexColumn("INSTRUCTION_TYPE").
            addIndexColumn("INSTRUCTION_ID").execute();
    }

    private void updateNodeHeartbeatTimestamp() {
        NodeInfoMapperExt nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapperExt.class);
        nodeInfoMapper.updateLastTsoHeartbeat(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
    }

    public interface Alarm {
        void send(Throwable t);
    }

    public interface MetricsProvider {
        CommonMetrics get(String key);
    }

    private static class EpsHolder {
        private final double[] array = new double[10];
        private double sum = 0;
        private long count = 0;

        public double calcAverage(double latestEps) {
            int index = (int) count % array.length;
            double old = array[index];
            sum -= old;
            sum += latestEps;
            array[index] = latestEps;

            count++;
            return sum / Math.min(array.length, count);
        }
    }
}
