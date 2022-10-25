/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.heartbeat;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_THRESHOLD_EPS;
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
public class TsoHeartbeat implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TsoHeartbeat.class);

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
    private Future<?> future;
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

    public TsoHeartbeat() {
        this.template = SpringContextHolder.getObject("polarxJdbcTemplate");
        this.transactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
        this.currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_INTERVAL);
        this.epsHolder = new EpsHolder();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        logger.info("start heartbeat now!");
        future = executorService.submit(this);
        Runtime.getRuntime().addShutdownHook(new Thread(TsoHeartbeat.this::stop));
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (future != null) {
            logger.info("stop heartbeat");
            future.cancel(true);
            future = null;
        }
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
        if (RuntimeLeaderElector.isDaemonLeader()) {
            try {
                if (heartbeatTableInitFlag.compareAndSet(false, true)) {
                    template.execute(CREATE_HEARTBEAT_TABLE_SQL);

                    // update schema info
                    Optional<Map<String, Object>> optional = template.queryForList("desc __cdc_ddl_record__").stream()
                        .filter(e -> StringUtils.equals(e.get("Field").toString(), "META_INFO")).findFirst();
                    if (optional.isPresent() && "text".equalsIgnoreCase(optional.get().get("Type").toString())) {
                        template.execute(
                            "alter table `__cdc_ddl_record__` modify column `META_INFO` MEDIUMTEXT DEFAULT NULL");
                        template.execute(
                            "alter table `__cdc_ddl_record__` modify column `EXT` MEDIUMTEXT DEFAULT NULL");
                        logger.info("Change column type from text to mediumtext for column META_INFO and EXT");
                    }

                    Optional<Map<String, Object>> optional2 =
                        template.queryForList("show index from `__cdc_ddl_record__`").stream()
                            .filter(e -> StringUtils.equals(e.get("Key_name").toString(), "idx_gmt_created"))
                            .findFirst();
                    if (!optional2.isPresent()) {
                        template.execute("alter table `__cdc_ddl_record__` add index `idx_gmt_created`(`GMT_CREATED`)");
                    }

                    Optional<Map<String, Object>> optional3 =
                        template.queryForList("show index from `__cdc_ddl_record__`").stream()
                            .filter(e -> StringUtils.equals(e.get("Key_name").toString(), "idx_job_id"))
                            .findFirst();
                    if (!optional3.isPresent()) {
                        template.execute("alter table `__cdc_ddl_record__` add index `idx_job_id`(`JOB_ID`)");
                    }
                }
            } catch (Throwable t) {
                heartbeatTableInitFlag.compareAndSet(true, false);
                throw t;
            }

            transactionTemplate.execute((o) -> transactionTemplate.execute(transactionStatus -> {
                long now = System.currentTimeMillis();
                String nowFormat = DateFormatUtils.format(now, "yyyy-MM-dd HH:mm:ss.SSS");
                String sql = String.format(UPDATE_SQL, nowFormat);
                template.execute(TRANSACTION_POLICY);
                template.execute(sql);
                return null;
            }));
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
            if (epsAverage > getInt(DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_THRESHOLD_EPS)) {
                currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_SELF_ADAPTION_TARGET_INTERVAL);
            } else {
                currentHeartBeatInterval = getInt(DAEMON_TSO_HEARTBEAT_INTERVAL);
            }

            if (originValue != currentHeartBeatInterval) {
                logger.info("tso heartbeat interval is changed from {} to {} , with eps {}.", originValue,
                    currentHeartBeatInterval, epsAverage);
            }

            lastCheckAdjustTime = System.currentTimeMillis();
        }
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
