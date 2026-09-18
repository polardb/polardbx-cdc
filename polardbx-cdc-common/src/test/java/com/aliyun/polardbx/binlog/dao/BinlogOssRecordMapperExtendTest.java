/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 测试 binlog_oss_record 查询使用 binlog_file_seq 列（替代 SUBSTRING_INDEX）
 */
public class BinlogOssRecordMapperExtendTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BinlogOssRecordMapperExtendTest.class);

    private static final String GROUP_ID = "group_global";
    private static final String STREAM_ID = "stream_global";
    private static final String CLUSTER_ID = "test-cluster-001";

    private BinlogOssRecordMapper mapper;
    private BinlogOssRecordMapperExtend mapperExtend;

    @Before
    public void setUp() {
        mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        mapperExtend = SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class);

        // 插入测试数据：模拟 binlog.000001 ~ binlog.000005
        for (int i = 1; i <= 5; i++) {
            BinlogOssRecord record = new BinlogOssRecord();
            record.setBinlogFile(String.format("binlog.%06d", i));
            record.setGroupId(GROUP_ID);
            record.setStreamId(STREAM_ID);
            record.setClusterId(CLUSTER_ID);
            record.setUploadStatus(2); // 上传成功
            record.setPurgeStatus(0);  // 未清理
            record.setBinlogFileSeq((long) i);
            mapper.insertSelective(record);
        }
    }

    @Test
    public void testGetRecordsForBinlogDump_FiltersBySeqAndOrders() {
        // binlog_file_seq >= 3 应返回 3,4,5 按升序
        List<BinlogOssRecord> results = mapperExtend.getRecordsForBinlogDump(
            GROUP_ID, STREAM_ID, CLUSTER_ID, 3);

        assertEquals(3, results.size());
        assertEquals("binlog.000003", results.get(0).getBinlogFile());
        assertEquals("binlog.000005", results.get(2).getBinlogFile());
    }

    @Test
    public void testGetRecordsBefore_FiltersBySeqDescWithLimit() {
        // binlog_file_seq <= 4, limit 2 应返回 4,3 按降序
        List<BinlogOssRecord> results = mapperExtend.getRecordsBefore(
            GROUP_ID, STREAM_ID, CLUSTER_ID, 4, 2);

        assertEquals(2, results.size());
        assertEquals("binlog.000004", results.get(0).getBinlogFile());
        assertEquals("binlog.000003", results.get(1).getBinlogFile());
    }

    @Test
    public void testGetLastUploadSuccessRecords_OrdersBySeqDesc() {
        // 取最新2条，应返回 5,4
        List<BinlogOssRecord> results = mapperExtend.getLastUploadSuccessRecords(
            GROUP_ID, STREAM_ID, CLUSTER_ID, 2);

        assertEquals(2, results.size());
        assertEquals("binlog.000005", results.get(0).getBinlogFile());
        assertEquals("binlog.000004", results.get(1).getBinlogFile());
    }

    @Test
    public void testGetRecordsInFileRange_FiltersBySeqRange() {
        // binlog_file_seq between 2 and 4，应返回 2,3,4
        List<BinlogOssRecord> results = mapperExtend.getRecordsInFileRange(
            GROUP_ID, STREAM_ID, CLUSTER_ID, 2, 4);

        assertEquals(3, results.size());
    }

    @Test
    public void testGetRecordsForBinlogDump_NoMatch_ReturnsEmpty() {
        // binlog_file_seq >= 100 应返回空
        List<BinlogOssRecord> results = mapperExtend.getRecordsForBinlogDump(
            GROUP_ID, STREAM_ID, CLUSTER_ID, 100);

        assertTrue(results.isEmpty());
    }

    @Test
    public void testGetRecordsForBinlogDump_DifferentCluster_ReturnsEmpty() {
        // 不同 cluster_id 应返回空
        List<BinlogOssRecord> results = mapperExtend.getRecordsForBinlogDump(
            GROUP_ID, STREAM_ID, "other-cluster", 1);

        assertTrue(results.isEmpty());
    }

    // ==================== 索引存在性验证 ====================
    // 注意：H2Util.convertSql() 会主动剥离 CREATE TABLE 中的普通 KEY（非 UNIQUE/PRIMARY），
    // 因此索引命中验证必须在真实 MySQL 环境执行，此处仅验证功能正确性。
    // 生产环境索引验证方法：
    //   EXPLAIN DELETE FROM binlog_phy_ddl_history WHERE tso < ? LIMIT 200;
    //   EXPLAIN SELECT * FROM binlog_oss_record WHERE group_id=? AND stream_id=? AND cluster_id=? AND binlog_file_seq >= ?;
    //   EXPLAIN SELECT count(*) FROM binlog_phy_ddl_history WHERE storage_inst_id = ?;
}
