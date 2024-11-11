/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yudong
 * @since 2023/11/15 14:32
 **/
public class RemoteFileSystemTest extends BaseTestWithGmsTables {
    private final String group = "test_group";
    private final String stream = "test_stream";
    private final String clusterId = "test_cluster";

    @Test
    public void testListFiles() {
        setConfig(ConfigKeys.CLUSTER_ID, clusterId);

        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);

        int preserveDays = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_FILE_PRESERVE_DAYS);
        Date expireTime = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(preserveDays));

        BinlogOssRecord record1 = new BinlogOssRecord();
        record1.setBinlogFile("binlog.000001");
        record1.setGmtModified(expireTime);
        record1.setGroupId(group);
        record1.setStreamId(stream);
        record1.setClusterId(clusterId);
        record1.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record1.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(record1);
        BinlogOssRecord record2 = new BinlogOssRecord();
        record2.setBinlogFile("binlog.000002");
        record2.setGroupId(group);
        record2.setStreamId(stream);
        record2.setClusterId(clusterId);
        record2.setUploadStatus(BinlogUploadStatus.SUCCESS.getValue());
        record2.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        mapper.insertSelective(record2);

        RemoteFileSystem fileSystem = new RemoteFileSystem(group, stream);
        List<CdcFile> cdcFiles = fileSystem.listFiles();
        Assert.assertEquals(1, cdcFiles.size());
        Assert.assertEquals("binlog.000002", cdcFiles.get(0).getName());
    }

}
