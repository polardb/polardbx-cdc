/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ziyang.lb
 **/
public class BinlogParameter {

    private String storageInstId;
    private String ignoreDbFilter;
    private String ignoreTbFilter;
    private String doDbFilter;
    private String doTbFilter;

    private boolean hasTsoHeartbeat = true;

    public String getStorageInstId() {
        return storageInstId;
    }

    public void setStorageInstId(String storageInstId) {
        this.storageInstId = storageInstId;
    }

    public String getIgnoreDbFilter() {
        return ignoreDbFilter;
    }

    public void setIgnoreDbFilter(String ignoreDbFilter) {
        this.ignoreDbFilter = ignoreDbFilter;
    }

    public String getIgnoreTbFilter() {
        return ignoreTbFilter;
    }

    public void setIgnoreTbFilter(String ignoreTbFilter) {
        this.ignoreTbFilter = ignoreTbFilter;
    }

    public String getDoDbFilter() {
        return doDbFilter;
    }

    public void setDoDbFilter(String doDbFilter) {
        this.doDbFilter = doDbFilter;
    }

    public String getDoTbFilter() {
        return doTbFilter;
    }

    public void setDoTbFilter(String doTbFilter) {
        this.doTbFilter = doTbFilter;
    }

    public boolean isHasTsoHeartbeat() {
        return hasTsoHeartbeat;
    }

    public void setHasTsoHeartbeat(boolean hasTsoHeartbeat) {
        this.hasTsoHeartbeat = hasTsoHeartbeat;
    }
}
