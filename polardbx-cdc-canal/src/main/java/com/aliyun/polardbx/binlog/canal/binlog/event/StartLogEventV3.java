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
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

/**
 * Start_log_event_v3 is the Start_log_event of binlog format 3 (MySQL 3.23 and 4.x). Format_description_log_event
 * derives from Start_log_event_v3; it is the Start_log_event of binlog format 4 (MySQL 5.0), that is, the event that
 * describes the other events' Common-Header/Post-Header lengths. This event is sent by MySQL 5.0 whenever it starts
 * sending a new binlog if the requested position is >4 (otherwise if ==4 the event will be sent naturally).
 *
 * @author Changyuan.lh
 * @version 1.0
 * @see mysql-5.1.60/sql/log_event.cc - Start_log_event_v3
 */
public class StartLogEventV3 extends LogEvent {

    /**
     * We could have used SERVER_VERSION_LENGTH, but this introduces an obscure dependency - if somebody decided to
     * change SERVER_VERSION_LENGTH this would break the replication protocol
     */
    public static final int ST_SERVER_VER_LEN = 50;

    /* start event post-header (for v3 and v4) */
    public static final int ST_BINLOG_VER_OFFSET = 0;
    public static final int ST_SERVER_VER_OFFSET = 2;

    protected int binlogVersion;
    protected String serverVersion;

    public StartLogEventV3(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        buffer.position(descriptionEvent.commonHeaderLen);
        binlogVersion = buffer.getUint16(); // ST_BINLOG_VER_OFFSET
        serverVersion = buffer.getFixString(ST_SERVER_VER_LEN); // ST_SERVER_VER_OFFSET
    }

    public StartLogEventV3() {
        super(new LogHeader(START_EVENT_V3));
    }

    public final String getServerVersion() {
        return serverVersion;
    }

    public final int getBinlogVersion() {
        return binlogVersion;
    }
}
