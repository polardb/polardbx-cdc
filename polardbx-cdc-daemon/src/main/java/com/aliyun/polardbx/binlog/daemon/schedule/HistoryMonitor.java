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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistCleanPoint;
import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_HISTORY_CHECK_INTERVAL_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_RECORD_LOGIC_COUNT_ALARM_THRESHOD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_RECORD_MARK_COUNT_CLEAN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_RECORD_PHY_COUNT_ALARM_THRESHOD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_RECORD_PHY_COUNT_CLEAN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ROLLBACK_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_SEMI_SNAPSHOT_HOLDING_TIME;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThan;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class HistoryMonitor {
    private final ScheduledExecutorService monitor;
    private final JdbcTemplate polarxJdbcTemplate;
    private final JdbcTemplate metaJdbcTemplate;

    public HistoryMonitor() {
        this.monitor = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "history-monitor-thread");
            t.setDaemon(true);
            return t;
        });
        this.polarxJdbcTemplate = getObject("polarxJdbcTemplate");
        this.metaJdbcTemplate = getObject("metaJdbcTemplate");
    }

    public void start() {
        long period = DynamicApplicationConfig.getLong(DAEMON_HISTORY_CHECK_INTERVAL_MINUTES);
        monitor.scheduleAtFixedRate(() -> {
            try {
                if (!RuntimeLeaderElector.isDaemonLeader()) {
                    if (log.isDebugEnabled()) {
                        log.debug("current daemon is not a leader, skip the ddl record check!");
                    }
                    return;
                }

                tryAlarmLogicDdlCount();
                tryAlarmPhyDdlCount();
                tryCleanCdcDdlRecord();
                tryCleanScheduleHistory();
                tryCleanEnvConfigHistory();
                tryCleanExpiredSemiSnapshot();
            } catch (Throwable e) {
                log.error("check ddl record error!", e);
            }
        }, 0, period, TimeUnit.MINUTES);
    }

    public void stop() {
        monitor.shutdownNow();
    }

    private void tryCleanCdcDdlRecord() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            String cdcPhyTableName = getCdcPhyTableName();
            long threshold = DynamicApplicationConfig.getLong(META_DDL_RECORD_MARK_COUNT_CLEAN_THRESHOLD);
            Long count = polarxJdbcTemplate.queryForObject("/!+TDDL:node(0)*/select count(id) from __cdc___000000." +
                cdcPhyTableName, Long.class);

            if (count > threshold) {
                Long minId = polarxJdbcTemplate.queryForObject(String.format(
                    "/!+TDDL:node(0)*/select min(id) from (select id from __cdc___000000.%s order by id desc limit %s) t",
                    cdcPhyTableName, threshold), Long.class);
                polarxJdbcTemplate.execute("delete from __cdc_ddl_record__ where id < " + minId);
                log.info("cdc ddl records is cleaned, ");
            }
        } catch (Throwable t) {
            log.error("check cdc ddl record count for clean failed.", t);
        }
    }

    private String getCdcPhyTableName() {
        List<Map<String, Object>> list =
            polarxJdbcTemplate.queryForList("show topology from __cdc__.__cdc_ddl_record__");
        return list.get(0).get("TABLE_NAME").toString();
    }

    private void tryAlarmLogicDdlCount() {
        try {
            long threshold = DynamicApplicationConfig.getLong(META_DDL_RECORD_LOGIC_COUNT_ALARM_THRESHOD);
            BinlogLogicMetaHistoryMapper mapper = getObject(BinlogLogicMetaHistoryMapper.class);
            long count = mapper.count(s -> s);
            if (count > threshold) {
                log.info("send alarm for logic ddl record count " + count);
                MonitorManager.getInstance().triggerAlarm(MonitorType.META_LOGIC_DDLRECORD_COUNT_WARNNIN, count);
            }
        } catch (Throwable t) {
            log.error("check logic ddl count for alarm failed.", t);
        }
    }

    private void tryAlarmPhyDdlCount() {
        try {
            long threshold = DynamicApplicationConfig.getLong(META_DDL_RECORD_PHY_COUNT_ALARM_THRESHOD);
            BinlogPhyDdlHistoryMapper mapper = getObject(BinlogPhyDdlHistoryMapper.class);
            long count = mapper.count(s -> s);
            if (count > threshold) {
                log.info("send alarm for physical ddl record count " + count);
                MonitorManager.getInstance().triggerAlarm(MonitorType.META_PHY_DDLRECORD_COUNT_WARNNIN, count);
            }
        } catch (Throwable t) {
            log.error("check phy ddl count for alarm failed.", t);
        }
    }

    private void tryCleanScheduleHistory() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            long threshold = 1000;
            Long count = metaJdbcTemplate.queryForObject("select count(id) from binlog_schedule_history", Long.class);

            if (count > threshold) {
                Long minId = metaJdbcTemplate.queryForObject(String.format(
                    "select min(id) from (select id from binlog_schedule_history order by id desc limit %s) t",
                    threshold), Long.class);
                metaJdbcTemplate.execute("delete from binlog_schedule_history where id < " + minId);
                log.info("binlog schedule history is cleaned, ");
            }
        } catch (Throwable t) {
            log.error("check binlog schedule history count for clean failed.", t);
        }
    }

    private void tryCleanEnvConfigHistory() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            long threshold = 1000;
            Long count = metaJdbcTemplate.queryForObject("select count(id) from binlog_env_config_history", Long.class);

            if (count > threshold) {
                Long minId = metaJdbcTemplate.queryForObject(String.format(
                    "select min(id) from (select id from binlog_env_config_history order by id desc limit %s) t",
                    threshold), Long.class);
                metaJdbcTemplate.execute("delete from binlog_env_config_history where id < " + minId);
                log.info("binlog env config history is cleaned, ");
            }
        } catch (Throwable t) {
            log.error("check binlog env config history count for clean failed.", t);
        }
    }

    private void tryCleanExpiredSemiSnapshot() {
        try {
            StorageInfoMapper storageInfoMapper = getObject(StorageInfoMapper.class);
            List<StorageInfo> storageInfos = storageInfoMapper.select(c ->
                c.where(instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                    .and(status, isNotEqualTo(2))//0:storage ready, 1:prepare offline, 2:storage offline
                    .orderBy(id));
            Set<String> storageInstIds = storageInfos.stream().map(StorageInfo::getStorageInstId)
                .collect(Collectors.toSet());

            int cleanPhyDdlThreshold =
                DynamicApplicationConfig.getInt(META_DDL_RECORD_PHY_COUNT_CLEAN_THRESHOLD) / storageInstIds.size();
            storageInstIds.forEach(k -> cleanExpiredSemiSnapshot(k, cleanPhyDdlThreshold));
        } catch (Throwable t) {
            log.error("clean semi snapshot error!", t);
        }
    }

    private void cleanExpiredSemiSnapshot(String storageInstId, int cleanThreshold) {
        int holdingTime = DynamicApplicationConfig.getInt(META_SEMI_SNAPSHOT_HOLDING_TIME);
        Date expireTime = DateTime.now().minusHours(holdingTime).toDate();

        SemiSnapshotInfoMapper semiMapper = getObject(SemiSnapshotInfoMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = getObject(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = getObject("metaTransactionTemplate");

        List<SemiSnapshotInfo> list = semiMapper
            .select(s -> s.where(SemiSnapshotInfoDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                .and(SemiSnapshotInfoDynamicSqlSupport.gmtCreated, SqlBuilder.isLessThanOrEqualTo(expireTime))
                .orderBy(SemiSnapshotInfoDynamicSqlSupport.tso.descending())
                .limit(1));

        if (!list.isEmpty()) {
            int count = semiMapper.delete(
                s -> s.where(SemiSnapshotInfoDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                    .and(SemiSnapshotInfoDynamicSqlSupport.tso, SqlBuilder.isLessThan(list.get(0).getTso())));
            log.info("successfully deleted expired semi snapshot records which tso is less than {}, delete count: "
                + "{}. ", list.get(0).getTso(), count);

            String rollbackModeStr = DynamicApplicationConfig.getString(META_ROLLBACK_MODE);
            RollbackMode rollbackMode = RollbackMode.valueOf(rollbackModeStr);

            long phyCount = phyHistMapper
                .count(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, isEqualTo(storageInstId)));
            if (rollbackMode == RollbackMode.SNAPSHOT_SEMI && phyCount > cleanThreshold) {
                transTemplate.execute(t -> {
                    int cleanCount = phyHistMapper.delete(
                        s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, isEqualTo(storageInstId))
                            .and(BinlogPhyDdlHistoryDynamicSqlSupport.tso, isLessThan(list.get(0).getTso())));

                    Optional<BinlogPhyDdlHistCleanPoint> optional = cleanPointMapper.selectOne(s -> s
                        .where(BinlogPhyDdlHistCleanPointDynamicSqlSupport.storageInstId, isEqualTo(storageInstId)));
                    if (optional.isPresent()) {
                        BinlogPhyDdlHistCleanPoint cleanPoint = new BinlogPhyDdlHistCleanPoint();
                        cleanPoint.setTso(list.get(0).getTso());
                        cleanPoint.setId(optional.get().getId());
                        cleanPointMapper.updateByPrimaryKeySelective(cleanPoint);
                    } else {
                        BinlogPhyDdlHistCleanPoint cleanPoint = new BinlogPhyDdlHistCleanPoint();
                        cleanPoint.setGmtCreated(new Date());
                        cleanPoint.setGmtModified(new Date());
                        cleanPoint.setStorageInstId(storageInstId);
                        cleanPoint.setTso(list.get(0).getTso());
                        cleanPointMapper.insert(cleanPoint);
                    }
                    log.info("phy ddl history is cleaned, clean point is {}, clean count is {}",
                        list.get(0).getTso(), cleanCount);
                    return null;
                });
            }
        }
    }
}
