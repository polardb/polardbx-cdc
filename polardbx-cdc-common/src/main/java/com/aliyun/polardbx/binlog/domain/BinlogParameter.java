/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
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
