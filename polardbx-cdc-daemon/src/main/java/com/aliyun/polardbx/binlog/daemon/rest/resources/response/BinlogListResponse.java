/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.response;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.Date;
import java.util.List;

public class BinlogListResponse {
    private List<BinlogInfo> binlogInfoList;
    private Integer totalCount;

    public List<BinlogInfo> getBinlogInfoList() {
        return binlogInfoList;
    }

    public void setBinlogInfoList(
        List<BinlogInfo> binlogInfoList) {
        this.binlogInfoList = binlogInfoList;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public static class BinlogInfo {
        @JSONField(name = "ID")
        private Long id;
        @JSONField(name = "GmtCreated")
        private Date gmtCreated;
        @JSONField(name = "GmtModified")
        private Date gmtModified;
        @JSONField(name = "BinlogFile")
        private String binlogFile;
        @JSONField(name = "UploadStatus")
        private Integer uploadStatus;
        @JSONField(name = "PurgeStatus")
        private Integer purgeStatus;
        @JSONField(name = "UploadHost")
        private String uploadHost;
        @JSONField(name = "LogBegin")
        private Date logBegin;
        @JSONField(name = "LogEnd")
        private Date logEnd;
        @JSONField(name = "LogSize")
        private Long logSize;
        @JSONField(name = "DownloadLink")
        private String downloadLink;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Date getGmtCreated() {
            return gmtCreated;
        }

        public void setGmtCreated(Date gmtCreated) {
            this.gmtCreated = gmtCreated;
        }

        public Date getGmtModified() {
            return gmtModified;
        }

        public void setGmtModified(Date gmtModified) {
            this.gmtModified = gmtModified;
        }

        public String getBinlogFile() {
            return binlogFile;
        }

        public void setBinlogFile(String binlogFile) {
            this.binlogFile = binlogFile;
        }

        public Integer getUploadStatus() {
            return uploadStatus;
        }

        public void setUploadStatus(Integer uploadStatus) {
            this.uploadStatus = uploadStatus;
        }

        public Integer getPurgeStatus() {
            return purgeStatus;
        }

        public void setPurgeStatus(Integer purgeStatus) {
            this.purgeStatus = purgeStatus;
        }

        public String getUploadHost() {
            return uploadHost;
        }

        public void setUploadHost(String uploadHost) {
            this.uploadHost = uploadHost;
        }

        public Date getLogBegin() {
            return logBegin;
        }

        public void setLogBegin(Date logBegin) {
            this.logBegin = logBegin;
        }

        public Date getLogEnd() {
            return logEnd;
        }

        public void setLogEnd(Date logEnd) {
            this.logEnd = logEnd;
        }

        public Long getLogSize() {
            return logSize;
        }

        public void setLogSize(Long logSize) {
            this.logSize = logSize;
        }

        public String getDownloadLink() {
            return downloadLink;
        }

        public void setDownloadLink(String downloadLink) {
            this.downloadLink = downloadLink;
        }
    }
}
