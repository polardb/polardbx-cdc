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
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.BinlogPurgeStatusEnum;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.binlogFile;
import static com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport.purgeStatus;

/**
 * created by ziyang.lb
 **/
@Service
public class BinlogOssRecordService {
    /**
     * 获取purgeStatus为Complete的，binlogFileName最大的记录
     */
    public BinlogOssRecord getMaxPurgedRecord() {
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> purgeRecordList = mapper.select(s -> s.where(purgeStatus,
            SqlBuilder.isEqualTo(BinlogPurgeStatusEnum.COMPLETE.getValue())).orderBy(binlogFile.descending()).limit(1));
        return purgeRecordList.isEmpty() ? null : purgeRecordList.get(0);
    }
}
