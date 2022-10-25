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
package com.aliyun.polardbx.binlog.cleaner.barrier;

import com.aliyun.polardbx.binlog.BinlogUploadStatusEnum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.cleaner.ICleanerBarrier;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Optional;

public class RemoteCleanBarrier implements ICleanerBarrier {

    private final BinlogOssRecordMapper ossRecordMapper;

    public RemoteCleanBarrier() {
        ossRecordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
    }

    @Override
    public boolean canClean(String binlogName) {
        Optional<BinlogOssRecord> recordOptional = ossRecordMapper.selectOne(s -> s.where(
            BinlogOssRecordDynamicSqlSupport.binlogFile, SqlBuilder.isEqualTo(binlogName)));

        // 如果未检索到record记录，说明record已经被清理了(备份存储中只保留一定时间内的文件，binlog.backup.expire.days参数控制)
        // 如果检索到了record记录，则记录的上传状态必须是成功或者忽略
        if (recordOptional.isPresent()) {
            BinlogOssRecord record = recordOptional.get();
            return record.getUploadStatus() == BinlogUploadStatusEnum.IGNORE.getValue()
                || record.getUploadStatus() == BinlogUploadStatusEnum.SUCCESS.getValue();
        } else {
            return true;
        }
    }
}
