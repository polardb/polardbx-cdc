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

import java.util.Date;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_ENV_CONFIG_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_SCHEDULE_HISTORY_THRESHOLD;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

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
}
