/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.extern.java.Log;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Data
@Log
public class HostInstance {
    private Long instanceId;
    private Long begin;
    private Long end;
    private List<BinlogFile> binlogFiles = new ArrayList<>();
    private Set<String> binlogFileSet = Sets.newHashSet();

    private String format(Long time) {
        if (time == null) {
            return "";
        }
        return DateFormatUtils.formatUTC(time, "YYYY-MM-dd HH:mm:ss");
    }

    public HttpURLConnection getConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }

    public long extractServerId(BinlogFile binlogFile) {
        if (binlogFile.getServerId() != null) {
            return binlogFile.getServerId();
        }
        String url = binlogFile.getIntranetDownloadLink();
        InputStream is = null;
        HttpURLConnection connection = null;
        Long serverId = null;
        try {
            connection = getConnection(url);
            connection.connect();
            is = connection.getInputStream();
            byte[] buf = new byte[20];
            int len = 0;
            int totalRead = 0;
            while ((len = is.read(buf, totalRead, 20 - totalRead)) != -1) {
                totalRead += len;
                if (totalRead >= 20) {
                    break;
                }
            }
            int position = 9;
            serverId = ((long) (0xff & buf[position++])) | ((long) (0xff & buf[position++]) << 8) | (
                (long) (0xff & buf[position++]) << 16) | ((long) (0xff & buf[position++]) << 24);
        } catch (Exception e) {
            throw new PolardbxException("connect to url failed!" + binlogFile.getLogname(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        log.info("extractor remote binlog instance. id  : " + binlogFile.getInstanceID() + ", server Id : " + serverId
            + " link : " + binlogFile.getIntranetDownloadLink());
        return serverId;
    }

    public void prepareServerId() {
        Long serverId = extractServerId(binlogFiles.get(0));
        binlogFiles.forEach(b -> b.setServerId(serverId));
    }

    public Long getServerId() {
        if (binlogFiles.isEmpty()) {
            throw new PolardbxException("binlog file for " + instanceId + " should not be empty!");
        }
        return binlogFiles.get(0).getServerId();
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
        if (!binlogFileSet.add(binlogFile.getLogname())) {
            return;
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
        this.binlogFiles.sort((o1, o2) -> BinlogFileUtil.compareBinlogFileName(o1.getLogname(), o2.getLogname()));
        return binlogFiles;
    }

    @Override
    public String toString() {
        return "HostInstance{" + "instanceId=" + instanceId + ", begin=" + format(begin) + ", end=" + format(end)
            + ", binlogFiles=" + binlogFiles.size() + '}';
    }
}
