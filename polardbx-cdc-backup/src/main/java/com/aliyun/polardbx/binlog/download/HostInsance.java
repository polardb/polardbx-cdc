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
package com.aliyun.polardbx.binlog.download;

import com.aliyun.polardbx.binlog.download.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Data;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class HostInsance {
    private Long instanceId;
    private Long begin;
    private Long end;
    private List<BinlogFile> binlogFiles = new ArrayList<>();

    private String format(Long time) {
        if (time == null) {
            return "";
        }
        return DateFormatUtils.formatUTC(time, "YYYY-MM-dd HH:mm:ss");
    }

    public int size() {
        return binlogFiles.size();
    }

    public void addBinlog(BinlogFile binlogFile) throws ParseException {
        if (instanceId == null) {
            instanceId = binlogFile.getInstanceID();
        } else if (!instanceId.equals(binlogFile.getInstanceID())) {
            throw new PolardbxException("can not concat different host binlog files");
        }
        binlogFile.initRegionTime();
        binlogFiles.add(binlogFile);
        if (begin == null) {
            begin = binlogFile.getBeginTime();
        } else {
            begin = Math.min(begin, binlogFile.getBeginTime());
        }
        if (end == null) {
            end = binlogFile.getEndTime();
        } else {
            end = Math.max(end, binlogFile.getEndTime());
        }

    }

    public List<BinlogFile> sortList() {
        this.binlogFiles.sort(Comparator.comparing(BinlogFile::getLogname));
        return binlogFiles;
    }

    @Override
    public String toString() {
        return "HostInsance{" +
            "instanceId=" + instanceId +
            ", begin=" + format(begin) +
            ", end=" + format(end) +
            ", binlogFiles=" + binlogFiles.size() +
            '}';
    }
}
