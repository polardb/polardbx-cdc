/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryExtractorConfig;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

public class RecoveryExtractorTest extends RplWithGmsTablesBaseTest {

    private long logBegin = System.currentTimeMillis() - 1000;
    private long logEnd = System.currentTimeMillis();

    @Before
    public void init() {
        BinlogOssRecordMapper recordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        BinlogOssRecord record1 = new BinlogOssRecord();
        record1.setBinlogFile("binlog.00001");
        record1.setGmtCreated(new Date());
        record1.setGmtModified(new Date());
        record1.setClusterId("cc1");
        record1.setLogBegin(new Date());
        record1.setLogEnd(new Date());
        record1.setLogSize(1024L);
        record1.setLastTso("1001");
        recordMapper.insert(record1);

        BinlogOssRecord record2 = new BinlogOssRecord();
        record2.setBinlogFile("binlog.00002");
        record2.setGmtCreated(new Date());
        record2.setGmtModified(new Date());
        record2.setClusterId("cc1");
        record2.setLogBegin(new Date());
        record2.setLogSize(2048L);
        recordMapper.insert(record2);


        BinlogOssRecord record3 = new BinlogOssRecord();
        record3.setBinlogFile("binlog.00003");
        record3.setGmtCreated(new Date());
        record3.setGmtModified(new Date());
        record3.setClusterId("cc1");
        record3.setLogBegin(new Date(logBegin));
        record3.setLogSize(2048L);
        recordMapper.insert(record3);
    }

    @Test
    public void findStartPosTest1() {
        RecoveryExtractorConfig config = new RecoveryExtractorConfig();
        config.setBinlogList(Arrays.asList("binlog.00001"));
        RecoveryExtractor extractor = new RecoveryExtractor(config, null);

        BinlogPosition position = extractor.findStartPosition();
        Assert.assertEquals(ExecutionConfig.ORIGIN_TSO,position.getRtso());
    }

    @Test
    public void findStartPosTest2() {
        RecoveryExtractorConfig config = new RecoveryExtractorConfig();
        config.setBinlogList(Arrays.asList("binlog.00002"));
        RecoveryExtractor extractor = new RecoveryExtractor(config, null);

        BinlogPosition position = extractor.findStartPosition();
        Assert.assertEquals("1001", position.getRtso());
    }

    @Test
    public void findStartPosTest3() {
        RecoveryExtractorConfig config = new RecoveryExtractorConfig();
        config.setBinlogList(Arrays.asList("binlog.00003"));
        RecoveryExtractor extractor = new RecoveryExtractor(config, null);

        BinlogPosition position = extractor.findStartPosition();
        Assert.assertEquals(CommonUtils.generateTSO(logBegin, StringUtils.leftPad("0", 29, "0"), null), position.getRtso());
    }

    @Test
    public void testBeforeFile(){
        RecoveryExtractorConfig config = new RecoveryExtractorConfig();
        config.setBinlogList(Arrays.asList("binlog.00003"));
        RecoveryExtractor extractor = new RecoveryExtractor(config, null);
        Assert.assertEquals("binlog.00002", extractor.preFileName("binlog.00003"));
        Assert.assertEquals("binlog.00001", extractor.preFileName("binlog.00002"));
        Assert.assertNull(extractor.preFileName("binlog.00001"));
    }
}
