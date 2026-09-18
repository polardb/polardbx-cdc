/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogEnvConfigHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogEnvConfigHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackModeUtil;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistCleanPointMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogSemiSnapshotMapper;
import com.aliyun.polardbx.binlog.dao.SemiSnapshotInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistCleanPoint;
import com.aliyun.polardbx.binlog.domain.po.SemiSnapshotInfo;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_SCHEDULE_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MetaDataMonitorTest extends BaseTest {

    @Test
    public void testDoCleanCdcDdlRecord() {
        MetaDataMonitor metaDataMonitor = new MetaDataMonitor();
        metaDataMonitor.purgeCdcDdlRecordSql = "delete from __cdc_ddl_record__ where id in "
            + "(select id from __cdc_ddl_record__ order by `gmt_created` asc limit ?)";
        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");

        //prepare data
        metaJdbcTemplate.execute(
            "create table __cdc_ddl_record__(id bigint,`gmt_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        for (int i = 1; i <= 10000; i++) {
            metaJdbcTemplate.update("insert into __cdc_ddl_record__(id,`gmt_created`)values (?,?)",
                i, DateTime.parse("2024-12-12").minusDays(10000 - i).toDate());
        }

        int count = metaDataMonitor.doCleanCdcDdlRecord(10000, 20000, metaJdbcTemplate);
        Assert.assertEquals(0, count);

        count = metaDataMonitor.doCleanCdcDdlRecord(10000, 7995, metaJdbcTemplate);
        Assert.assertEquals(2005, count);
        Assert.assertEquals(7995,
            (int) metaJdbcTemplate.queryForObject("select count(1) from __cdc_ddl_record__", Integer.class));
        Assert.assertEquals(DateTime.parse("2024-12-12").minusDays(10000 - 2006).toDate(),
            metaJdbcTemplate.queryForObject("select min(gmt_created) from __cdc_ddl_record__", Date.class));
    }

    @Test
    public void testTryCleanScheduleHistory() {
        MetaDataMonitor metaDataMonitor = new MetaDataMonitor();
        metaDataMonitor.purgeScheduleHistorySql = "delete from binlog_schedule_history where id in "
            + "(select id from binlog_schedule_history order by id asc limit ?)";
        metaDataMonitor.metaJdbcTemplate = getObject("metaJdbcTemplate");
        BinlogScheduleHistoryMapper mapper = getObject(BinlogScheduleHistoryMapper.class);
        for (int i = 0; i < 10000; i++) {
            BinlogScheduleHistory history = new BinlogScheduleHistory();
            history.setId((long) i + 1);
            history.setGmtModified(new Date());
            history.setVersion((long) i);
            history.setContent("test");
            mapper.insertSelective(history);
        }

        mockConfig(META_PURGE_SCHEDULE_HISTORY_THRESHOLD, "20000");
        int count = metaDataMonitor.tryCleanScheduleHistory();
        Assert.assertEquals(0, count);

        mockConfig(META_PURGE_SCHEDULE_HISTORY_THRESHOLD, "1000");
        count = metaDataMonitor.tryCleanScheduleHistory();
        Assert.assertEquals(9000, count);
        Assert.assertEquals(1000, mapper.count(s -> s));
        Assert.assertEquals(9001,
            mapper.select(s -> s.orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.id).limit(1)).get(0).getId().intValue());
    }

    @Test
    public void testTryCleanEnvConfigHistory() {
        MetaDataMonitor metaDataMonitor = new MetaDataMonitor();
        metaDataMonitor.purgeEvnConfigHistorySql = "delete from binlog_env_config_history where id in "
            + "(select id from binlog_env_config_history order by id asc limit ?)";
        metaDataMonitor.metaJdbcTemplate = getObject("metaJdbcTemplate");
        BinlogEnvConfigHistoryMapper mapper = getObject(BinlogEnvConfigHistoryMapper.class);
        for (int i = 0; i < 10000; i++) {
            BinlogEnvConfigHistory history = new BinlogEnvConfigHistory();
            history.setId((long) i + 1);
            history.setGmtModified(new Date());
            history.setTso(i + "");
            history.setChangeEnvContent("");
            mapper.insertSelective(history);
        }

        mockConfig(META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD, "20000");
        int count = metaDataMonitor.tryCleanEnvConfigHistory();
        Assert.assertEquals(0, count);

        mockConfig(META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD, "1000");
        count = metaDataMonitor.tryCleanEnvConfigHistory();
        Assert.assertEquals(9000, count);
        Assert.assertEquals(1000, mapper.count(s -> s));
        Assert.assertEquals(9001,
            mapper.select(s -> s.orderBy(BinlogEnvConfigHistoryDynamicSqlSupport.id).limit(1)).get(0).getId()
                .intValue());
    }

    @Test
    public void testCleanExpiredSemiSnapshot_emptyPreservedList() {
        MetaDataMonitor monitor = new MetaDataMonitor();
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = mock(BinlogSemiSnapshotMapper.class);
        registerSpringObject(BinlogSemiSnapshotMapper.class, binlogSemiSnapshotMapper);
        mockConfig(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS, "24");

        when(binlogSemiSnapshotMapper.getPreservedSnapshot("inst1", 24))
            .thenReturn(Collections.emptyList());

        monitor.cleanExpiredSemiSnapshot("inst1", 100);
    }

    @Test
    public void testCleanExpiredSemiSnapshot_notSnapshotSemi_skipPhyCount() {
        MetaDataMonitor monitor = new MetaDataMonitor();

        SemiSnapshotInfoMapper semiMapper = mock(SemiSnapshotInfoMapper.class);
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = mock(BinlogSemiSnapshotMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = mock(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = mock(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = mock(TransactionTemplate.class);

        registerSpringObject(SemiSnapshotInfoMapper.class, semiMapper);
        registerSpringObject(BinlogSemiSnapshotMapper.class, binlogSemiSnapshotMapper);
        registerSpringObject(BinlogPhyDdlHistoryMapper.class, phyHistMapper);
        registerSpringObject(BinlogPhyDdlHistCleanPointMapper.class, cleanPointMapper);
        registerSpringObject("metaTransactionTemplate", transTemplate);

        mockConfig(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS, "24");

        SemiSnapshotInfo info = new SemiSnapshotInfo();
        info.setTso("123456");
        when(binlogSemiSnapshotMapper.getPreservedSnapshot("inst1", 24))
            .thenReturn(Collections.singletonList(info));
        when(semiMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(5);

        try (MockedStatic<RollbackModeUtil> rollbackModeMock = mockStatic(RollbackModeUtil.class)) {
            rollbackModeMock.when(RollbackModeUtil::getRollbackMode).thenReturn(RollbackMode.SNAPSHOT_EXACTLY);

            monitor.cleanExpiredSemiSnapshot("inst1", 100);

            // Key optimization: phyHistMapper.count() should NOT be called when rollbackMode != SNAPSHOT_SEMI
            verify(phyHistMapper, never()).count(any(CountDSLCompleter.class));
            verify(transTemplate, never()).execute(any(TransactionCallback.class));
        }
    }

    @Test
    public void testCleanExpiredSemiSnapshot_snapshotSemi_phyCountBelowThreshold() {
        MetaDataMonitor monitor = new MetaDataMonitor();

        SemiSnapshotInfoMapper semiMapper = mock(SemiSnapshotInfoMapper.class);
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = mock(BinlogSemiSnapshotMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = mock(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = mock(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = mock(TransactionTemplate.class);

        registerSpringObject(SemiSnapshotInfoMapper.class, semiMapper);
        registerSpringObject(BinlogSemiSnapshotMapper.class, binlogSemiSnapshotMapper);
        registerSpringObject(BinlogPhyDdlHistoryMapper.class, phyHistMapper);
        registerSpringObject(BinlogPhyDdlHistCleanPointMapper.class, cleanPointMapper);
        registerSpringObject("metaTransactionTemplate", transTemplate);

        mockConfig(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS, "24");

        SemiSnapshotInfo info = new SemiSnapshotInfo();
        info.setTso("123456");
        when(binlogSemiSnapshotMapper.getPreservedSnapshot("inst1", 24))
            .thenReturn(Collections.singletonList(info));
        when(semiMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(5);
        when(phyHistMapper.count(any(CountDSLCompleter.class))).thenReturn(50L);

        try (MockedStatic<RollbackModeUtil> rollbackModeMock = mockStatic(RollbackModeUtil.class)) {
            rollbackModeMock.when(RollbackModeUtil::getRollbackMode).thenReturn(RollbackMode.SNAPSHOT_SEMI);

            monitor.cleanExpiredSemiSnapshot("inst1", 100);

            // phyHistMapper.count IS called (rollbackMode == SNAPSHOT_SEMI)
            verify(phyHistMapper).count(any(CountDSLCompleter.class));
            // But transTemplate.execute NOT called (phyCount 50 <= threshold 100)
            verify(transTemplate, never()).execute(any(TransactionCallback.class));
        }
    }

    @Test
    public void testCleanExpiredSemiSnapshot_snapshotSemi_phyCountAboveThreshold_withCleanPoint() {
        MetaDataMonitor monitor = new MetaDataMonitor();

        SemiSnapshotInfoMapper semiMapper = mock(SemiSnapshotInfoMapper.class);
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = mock(BinlogSemiSnapshotMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = mock(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = mock(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = mock(TransactionTemplate.class);

        registerSpringObject(SemiSnapshotInfoMapper.class, semiMapper);
        registerSpringObject(BinlogSemiSnapshotMapper.class, binlogSemiSnapshotMapper);
        registerSpringObject(BinlogPhyDdlHistoryMapper.class, phyHistMapper);
        registerSpringObject(BinlogPhyDdlHistCleanPointMapper.class, cleanPointMapper);
        registerSpringObject("metaTransactionTemplate", transTemplate);

        mockConfig(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS, "24");

        SemiSnapshotInfo info = new SemiSnapshotInfo();
        info.setTso("123456");
        when(binlogSemiSnapshotMapper.getPreservedSnapshot("inst1", 24))
            .thenReturn(Collections.singletonList(info));
        when(semiMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(5);
        when(phyHistMapper.count(any(CountDSLCompleter.class))).thenReturn(200L);

        // Existing cleanPoint
        BinlogPhyDdlHistCleanPoint existingCleanPoint = new BinlogPhyDdlHistCleanPoint();
        existingCleanPoint.setId(1);
        existingCleanPoint.setTso("000000");
        when(cleanPointMapper.selectOne(any(SelectDSLCompleter.class)))
            .thenReturn(Optional.of(existingCleanPoint));
        when(phyHistMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(10);
        when(cleanPointMapper.updateByPrimaryKeySelective(any(BinlogPhyDdlHistCleanPoint.class))).thenReturn(1);

        // Execute transTemplate callback directly
        when(transTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        try (MockedStatic<RollbackModeUtil> rollbackModeMock = mockStatic(RollbackModeUtil.class)) {
            rollbackModeMock.when(RollbackModeUtil::getRollbackMode).thenReturn(RollbackMode.SNAPSHOT_SEMI);

            monitor.cleanExpiredSemiSnapshot("inst1", 100);

            verify(phyHistMapper).count(any(CountDSLCompleter.class));
            verify(transTemplate).execute(any(TransactionCallback.class));
            // Inside transTemplate callback: phyHistMapper.delete and cleanPointMapper.updateByPrimaryKeySelective
            verify(phyHistMapper).delete(any(DeleteDSLCompleter.class));
            verify(cleanPointMapper).updateByPrimaryKeySelective(any(BinlogPhyDdlHistCleanPoint.class));
        }
    }

    @Test
    public void testCleanExpiredSemiSnapshot_snapshotSemi_phyCountAboveThreshold_withoutCleanPoint() {
        MetaDataMonitor monitor = new MetaDataMonitor();

        SemiSnapshotInfoMapper semiMapper = mock(SemiSnapshotInfoMapper.class);
        BinlogSemiSnapshotMapper binlogSemiSnapshotMapper = mock(BinlogSemiSnapshotMapper.class);
        BinlogPhyDdlHistoryMapper phyHistMapper = mock(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistCleanPointMapper cleanPointMapper = mock(BinlogPhyDdlHistCleanPointMapper.class);
        TransactionTemplate transTemplate = mock(TransactionTemplate.class);

        registerSpringObject(SemiSnapshotInfoMapper.class, semiMapper);
        registerSpringObject(BinlogSemiSnapshotMapper.class, binlogSemiSnapshotMapper);
        registerSpringObject(BinlogPhyDdlHistoryMapper.class, phyHistMapper);
        registerSpringObject(BinlogPhyDdlHistCleanPointMapper.class, cleanPointMapper);
        registerSpringObject("metaTransactionTemplate", transTemplate);

        mockConfig(META_BUILD_SEMI_SNAPSHOT_PRESERVE_HOURS, "24");

        SemiSnapshotInfo info = new SemiSnapshotInfo();
        info.setTso("123456");
        when(binlogSemiSnapshotMapper.getPreservedSnapshot("inst1", 24))
            .thenReturn(Collections.singletonList(info));
        when(semiMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(5);
        when(phyHistMapper.count(any(CountDSLCompleter.class))).thenReturn(200L);

        // No existing cleanPoint
        when(cleanPointMapper.selectOne(any(SelectDSLCompleter.class)))
            .thenReturn(Optional.empty());
        when(phyHistMapper.delete(any(DeleteDSLCompleter.class))).thenReturn(10);
        when(cleanPointMapper.insert(any(BinlogPhyDdlHistCleanPoint.class))).thenReturn(1);

        // Execute transTemplate callback directly
        when(transTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        try (MockedStatic<RollbackModeUtil> rollbackModeMock = mockStatic(RollbackModeUtil.class)) {
            rollbackModeMock.when(RollbackModeUtil::getRollbackMode).thenReturn(RollbackMode.SNAPSHOT_SEMI);

            monitor.cleanExpiredSemiSnapshot("inst1", 100);

            verify(phyHistMapper).count(any(CountDSLCompleter.class));
            verify(transTemplate).execute(any(TransactionCallback.class));
            // Inside transTemplate callback: cleanPointMapper.insert instead of updateByPrimaryKeySelective
            verify(cleanPointMapper).insert(any(BinlogPhyDdlHistCleanPoint.class));
        }
    }
}
