/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackModeUtil;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogSemiSnapshotMapper;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistCleanPoint;
import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.IScheduleJob;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_HISTORY_RESOURCE_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ALARM_LOGIC_DDL_COUNT_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ALARM_PHYSICAL_DDL_COUNT_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_BATCH_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_MARK_DDL_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_PHYSICAL_DDL_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_SCHEDULE_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_RETRIEVE_INSTANT_CREATE_TABLE_MODES;
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
public class MetaDataMonitor implements IScheduleJob {
    private static final String HISTORY_MONITOR_LOCK = "CDC_HISTORY_MONITOR_LOCK";
    private final ScheduledExecutorService monitor;
    JdbcTemplate polarxJdbcTemplate;
    JdbcTemplate metaJdbcTemplate;

    String purgeCdcDdlRecordSql = "delete from __cdc_ddl_record__ order by `gmt_created` asc limit ?";
    String purgeScheduleHistorySql = "delete from binlog_schedule_history order by id asc limit ?";
    String purgeEvnConfigHistorySql = "delete from binlog_env_config_history order by id asc limit ?";

    public MetaDataMonitor() {
        this.monitor = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "history-monitor-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start() {
        long period = DynamicApplicationConfig.getLong(DAEMON_WATCH_HISTORY_RESOURCE_INTERVAL_MINUTE);
        this.polarxJdbcTemplate = getObject("polarxJdbcTemplate");
        this.metaJdbcTemplate = getObject("metaJdbcTemplate");
        this.monitor.scheduleAtFixedRate(() -> {
            try {
                if (!RuntimeLeaderElector.isDaemonLeader()) {
                    return;
                }
                if (!RuntimeLeaderElector.isLeader(HISTORY_MONITOR_LOCK)) {
                    return;
                }

                tryAlarmLogicDdlCount();
                tryAlarmPhyDdlCount();
                tryCleanCdcDdlRecord();
                tryCleanScheduleHistory();
                tryCleanEnvConfigHistory();
                if (StringUtils
                    .contains(DynamicApplicationConfig.getString(META_RETRIEVE_INSTANT_CREATE_TABLE_MODES),
                        RollbackMode.SNAPSHOT_SEMI.name())) {
                    tryCleanExpiredSemiSnapshot();
                }
            } catch (Throwable e) {
                log.error("check ddl record error!", e);
            }
        }, 0, period, TimeUnit.MINUTES);
        log.info("history monitor started!");
    }

    @Override
    @SneakyThrows
    public void stop() {
        monitor.shutdownNow();
        monitor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        log.info("history monitor stopped!");
    }

    private void tryCleanCdcDdlRecord() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            String cdcPhyTableName = getCdcPhyTableName();
            long threshold = DynamicApplicationConfig.getLong(META_PURGE_MARK_DDL_THRESHOLD);
            long totalCount = polarxJdbcTemplate.queryForObject(
                "/!+TDDL:node(0)*/select count(id) from __cdc___000000." + cdcPhyTableName, Long.class);
            doCleanCdcDdlRecord(totalCount, threshold, polarxJdbcTemplate);
        } catch (Throwable t) {
            log.error("check cdc ddl record count for clean failed.", t);
        }
    }

    int doCleanCdcDdlRecord(long totalCount, long threshold, JdbcTemplate polarxJdbcTemplate) {
        return loopDelete(purgeCdcDdlRecordSql, totalCount, threshold, polarxJdbcTemplate,
            "cdc ddl records is cleaned, delete count is {}, with execute count {}");
    }

    private String getCdcPhyTableName() {
        List<Map<String, Object>> list =
            polarxJdbcTemplate.queryForList("show topology from __cdc__.__cdc_ddl_record__");
        return list.get(0).get("TABLE_NAME").toString();
    }

    private void tryAlarmLogicDdlCount() {
        try {
            long threshold = DynamicApplicationConfig.getLong(META_ALARM_LOGIC_DDL_COUNT_THRESHOLD);
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
            long threshold = DynamicApplicationConfig.getLong(META_ALARM_PHYSICAL_DDL_COUNT_THRESHOLD);
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

    int tryCleanScheduleHistory() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            long threshold = DynamicApplicationConfig.getInt(META_PURGE_SCHEDULE_HISTORY_THRESHOLD);
            long count = metaJdbcTemplate.queryForObject("select count(id) from binlog_schedule_history", Long.class);
            return loopDelete(purgeScheduleHistorySql, count, threshold, metaJdbcTemplate,
                "binlog schedule history is cleaned, delete count is {}, with execution count {}");
        } catch (Throwable t) {
            log.error("check binlog schedule history count for clean failed.", t);
        }
        return 0;
    }

    int tryCleanEnvConfigHistory() {
        try {
            //未触发阈值，不予清理；触发阈值后，后续会一直维持这个阈值对应的数据量
            long threshold = DynamicApplicationConfig.getInt(META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD);
            long count = metaJdbcTemplate.queryForObject("select count(id) from binlog_env_config_history", Long.class);
            return loopDelete(purgeEvnConfigHistorySql, count, threshold, metaJdbcTemplate,
                "binlog env config history is cleaned, delete count is {}, with execution count {}.");
        } catch (Throwable t) {
            log.error("check binlog env config history count for clean failed.", t);
        }
        return 0;
    }

    private int loopDelete(String purgeSql, long count, long threshold, JdbcTemplate jdbcTemplate,
                           String logStr) {
        int deleteCount = 0;
        int executeCount = 0;
        while (count > threshold) {
            long purgeBatchSize = DynamicApplicationConfig.getInt(META_PURGE_BATCH_SIZE);
            long limitSize = Math.min(purgeBatchSize, count - threshold);
            int c = jdbcTemplate.update(purgeSql, limitSize);
            if (c > 0) {
                count -= c;
                deleteCount += c;
                executeCount++;
            } else {
                break;
            }
        }
        if (executeCount > 0) {
            log.info(logStr, deleteCount, executeCount);
        }
        return deleteCount;
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
                DynamicApplicationConfig.getInt(META_PURGE_PHYSICAL_DDL_THRESHOLD) / storageInstIds.size();
            storageInstIds.forEach(k -> cleanExpiredSemiSnapshot(k, cleanPhyDdlThreshold));
        } catch (Throwable t) {
            log.error("clean semi snapshot error!", t);
        }
    }

    private void cleanExpiredSemiSnapshot(String storageInstId, int cleanThreshold) {
        int preserveHours = DynamicApplicationConfig.getInt(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS);
        SemiSnapshotInfoMapper semiMapper = getObject(SemiSnapshotInfoMapper.class);
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = getObject(BinlogSemiSnapshotMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = getObject(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = getObject("metaTransactionTemplate");

        List<SemiSnapshotInfo> list = binlogSemiSnapshotMapper.getPreservedSnapshot(storageInstId, preserveHours);
        if (!list.isEmpty()) {
            int count = semiMapper.delete(
                s -> s.where(SemiSnapshotInfoDynamicSqlSupport.storageInstId, SqlBuilder.isEqualTo(storageInstId))
                    .and(SemiSnapshotInfoDynamicSqlSupport.tso, SqlBuilder.isLessThan(list.get(0).getTso())));
            log.info("successfully deleted expired semi snapshot records which tso is less than {}, delete count: "
                + "{}. ", list.get(0).getTso(), count);

            RollbackMode rollbackMode = RollbackModeUtil.getRollbackMode();
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
