/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
