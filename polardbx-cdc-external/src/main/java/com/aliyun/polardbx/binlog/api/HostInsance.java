/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
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
