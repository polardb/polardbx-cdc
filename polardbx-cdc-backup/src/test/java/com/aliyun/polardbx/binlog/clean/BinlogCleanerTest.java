/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BinlogCleanerTest extends BaseTestWithGmsTables {
    @Test
    public void testClean() throws ParseException {
        setConfig(ConfigKeys.BINLOG_DISK_SPACE_MAX_SIZE_MB, "1024");
        setConfig(ConfigKeys.DISK_SIZE, "102400");
        setConfig(ConfigKeys.BINLOG_PURGE_DISK_USE_RATIO, "0.7");
        setConfig(ConfigKeys.BINLOG_DIR_PATH, this.getClass().getResource("/").getPath());
        StreamContext context = new StreamContext(CommonConstants.GROUP_NAME_GLOBAL, Arrays.asList(CommonConstants.STREAM_NAME_GLOBAL), DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID), "", TaskType.Dumper, 1);
        BinlogCleaner cleaner = new BinlogCleaner(CommonConstants.STREAM_NAME_GLOBAL, context);
        MockedStatic<GmsTimeUtil> gmsTimeUtilMockedStatic = Mockito.mockStatic(GmsTimeUtil.class);
        gmsTimeUtilMockedStatic.when(GmsTimeUtil::getCurrentTimeMillis).thenReturn(System.currentTimeMillis());
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        int recordExpireDays =
            DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_PURGED_RECORD_PRESERVE_DAYS);

        Calendar calendar = Calendar.getInstance();
        int deleteNum = recordExpireDays/2;
        long now = GmsTimeUtil.getCurrentTimeMillis();
        calendar.setTimeInMillis(now - TimeUnit.DAYS.toMillis(deleteNum + recordExpireDays));

        for (int i=0; i<10; i++){
            BinlogOssRecord record1 = new BinlogOssRecord();
            Date date = calendar.getTime();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            record1.setGmtCreated(date);
            record1.setGmtModified(date);
            record1.setPurgeStatus(BinlogPurgeStatus.COMPLETE.getValue());
            record1.setBinlogFile("binlog.00000"+i);
            record1.setGroupId(CommonConstants.GROUP_NAME_GLOBAL);
            record1.setStreamId(CommonConstants.STREAM_NAME_GLOBAL);
            record1.setClusterId(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID));
            mapper.insert(record1);
        }

        cleaner.cleanOssRecords();
        List<BinlogOssRecord> records =  mapper.select(s->s);
        Assert.assertEquals(10 - deleteNum, records.size());
        System.out.println(JSON.toJSONString(records));
    }
}
