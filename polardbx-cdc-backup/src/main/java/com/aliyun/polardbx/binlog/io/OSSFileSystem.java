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
package com.aliyun.polardbx.binlog.io;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class OSSFileSystem {

    private String localBinlogFilePath;

    public OSSFileSystem(String localBinlogFilePath) {
        this.localBinlogFilePath = localBinlogFilePath;
    }

    public boolean isLocalFileExist() {
        File localDir = new File(localBinlogFilePath);
        if (localDir.exists()) {
            File[] fs = localDir.listFiles((dir, name) -> name.matches("binlog\\.\\d+"));
            return fs != null && fs.length > 0;
        }
        return false;
    }

    public List<OSSFile> getAllLogFileNamesOrdered() {
        Map<String, OSSFile> fileMap = new HashMap<>();
        File localDir = new File(localBinlogFilePath);
        if (localDir.exists()) {
            File[] fs = localDir.listFiles((dir, name) -> name.matches("binlog\\.\\d+"));
            if (fs != null) {
                for (File f : fs) {
                    fileMap.put(f.getName(), new OSSFile(f.getName(), f));
                }
            }
        }
        List<OSSFile> lst = new ArrayList<>(fileMap.values());
        lst.sort(Comparator.naturalOrder());
        return lst;
    }

    public static List<String> getAllLogFileNamesBetweenTimeRange(long startTimeStamp, long endTimeStamp) {
        //用set，保证去重
        TreeSet<String> set = new TreeSet<>();

        Date startDate = new Date(startTimeStamp);
        Date endDate = new Date(endTimeStamp);
        BinlogOssRecordMapper mapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);

        List<BinlogOssRecord> recordList1 = mapper.select(s -> s.where(
            BinlogOssRecordDynamicSqlSupport.logEnd, SqlBuilder.isNull())
            .and(BinlogOssRecordDynamicSqlSupport.logBegin, SqlBuilder.isLessThanOrEqualTo(endDate))
        );
        List<BinlogOssRecord> recordList2 = mapper.select(s -> s.where(
            BinlogOssRecordDynamicSqlSupport.logEnd, SqlBuilder.isNotNull())
            .and(BinlogOssRecordDynamicSqlSupport.logEnd, SqlBuilder.isGreaterThanOrEqualTo(startDate))
            .and(BinlogOssRecordDynamicSqlSupport.logBegin, SqlBuilder.isLessThanOrEqualTo(endDate))
        );
        for (BinlogOssRecord record : recordList1) {
            set.add(record.getBinlogFile());
        }
        for (BinlogOssRecord record : recordList2) {
            set.add(record.getBinlogFile());
        }

        return new ArrayList<>(set);
    }
}
