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

package com.aliyun.polardbx.binlog.heartbeat;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(1,
        r -> new Thread(r, "binlog_heartbeat"));
    private Future<?> future;
    private final long interval;
    private final AtomicBoolean heartbeatTableInitFlag = new AtomicBoolean(false);
    private final JdbcTemplate template;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Alarm alarm;
    private final AtomicInteger errorCount = new AtomicInteger(0);

    public TsoHeartbeat(long interval) {
        this.interval = interval;
        this.template = SpringContextHolder.getObject("polarxJdbcTemplate");
        this.transactionTemplate = SpringContextHolder.getObject("polarxTransactionTemplate");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        logger.info("start heartbeat now!");
        future = scheduledExecutorService.scheduleAtFixedRate(this, 0, interval, TimeUnit.MILLISECONDS);
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

    @Override
    public void run() {
        if (!running.get()) {
            return;
        }
        try {
            heartbeat();
            errorCount.set(0);
        } catch (Throwable e) {
            logger.error(" execute heartbeat failed ! ", e);
            if (alarm != null && errorCount.incrementAndGet() > 15) {
                alarm.send(e);
            }
        }
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public void setAlarm(Alarm alarm) {
        this.alarm = alarm;
    }

    private void heartbeat() {
        if (RuntimeLeaderElector.isDaemonLeader()) {
            try {
                if (heartbeatTableInitFlag.compareAndSet(false, true)) {
                    template.execute(CREATE_HEARTBEAT_TABLE_SQL);
                    Optional<Map<String, Object>> optional = template.queryForList("desc __cdc_ddl_record__").stream()
                        .filter(e -> StringUtils.equals(e.get("Field").toString(), "META_INFO")).findFirst();
                    if (optional.isPresent() && "text".equals(optional.get().get("Type").toString())) {
                        template.execute(
                            "alter table `__cdc_ddl_record__` modify column `META_INFO` MEDIUMTEXT DEFAULT NULL");
                        template.execute(
                            "alter table `__cdc_ddl_record__` modify column `EXT` MEDIUMTEXT DEFAULT NULL");
                        logger.info("Change column type from text to mediumtext for column META_INFO and EXT");
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

    public interface Alarm {
        void send(Throwable t);
    }
}
