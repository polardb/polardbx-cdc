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
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;

public class CdcEventData {
    private final String binlogFileName;
    private final long position;
    private final DBMSEvent event;

    public CdcEventData(String binlogFileName, long position, DBMSEvent event) {
        this.binlogFileName = binlogFileName;
        this.position = position;
        this.event = event;
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public long getPosition() {
        return position;
    }

    public DBMSEvent getEvent() {
        return event;
    }
}
