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
package com.aliyun.polardbx.binlog.canal.unit;

import lombok.Data;

import java.util.HashSet;

@Data
public class SearchRecorder {
    private Long tid;
    private String fileName;
    private long position;
    private long timestamp;
    private String storageName;
    private boolean local = true;
    private long size;
    private boolean finish;
    private HashSet<String> unCommitXidSet = new HashSet<>();
    private HashSet<String> needStartXidSet = new HashSet<>();
    private long searchTime = -1;

    public SearchRecorder(String storageName) {
        this.storageName = storageName;
        this.tid = Thread.currentThread().getId();
        SearchRecorderMetrics.addSearchRecorder(this);
    }
}
