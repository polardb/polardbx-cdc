/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean.barrier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;

import java.util.Optional;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * @author chengjin, yudong
 */
public class UploadFinishedBarrier implements ICleanBarrier {

    private final BinlogOssRecordService service;
    private final String gid;
    private final String sid;
    private final String cid;

    public UploadFinishedBarrier(String gid, String sid) {
        this.gid = gid;
        this.sid = sid;
        this.cid = getString(ConfigKeys.CLUSTER_ID);
        this.service = getObject(BinlogOssRecordService.class);
    }

    @Override
    public boolean canClean(String fileName) {
        Optional<BinlogOssRecord> recordOptional = service.getRecordByName(gid, sid, cid, fileName);
        return recordOptional.map(
                binlogOssRecord -> BinlogUploadStatus.fromValue(binlogOssRecord.getUploadStatus()).uploadFinished())
            .orElse(true);
    }
}
