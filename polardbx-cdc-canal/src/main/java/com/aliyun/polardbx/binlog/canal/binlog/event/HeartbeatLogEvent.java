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

package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

/**
 * <pre>
 * Replication event to ensure to slave that master is alive.
 *   The event is originated by master's dump thread and sent straight to
 *   slave without being logged. Slave itself does not store it in relay log
 *   but rather uses a data for immediate checks and throws away the event.
 *
 *   Two members of the class log_ident and Log_event::log_pos comprise
 *   @see the event_coordinates instance. The coordinates that a heartbeat
 *   instance carries correspond to the last event master has sent from
 *   its binlog.
 * </pre>
 *
 * @author jianghang 2013-4-8 上午12:36:29
 * @version 1.0.3
 * @since mysql 5.6
 */
public class HeartbeatLogEvent extends LogEvent {

    public static final int FN_REFLEN = 512; /* Max length of full path-name */
    private int identLen;
    private String logIdent;

    public HeartbeatLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        identLen = buffer.limit() - commonHeaderLen;
        if (identLen > FN_REFLEN - 1) {
            identLen = FN_REFLEN - 1;
        }

        logIdent = buffer.getFullString(commonHeaderLen, identLen, LogBuffer.ISO_8859_1);
    }

    public int getIdentLen() {
        return identLen;
    }

    public String getLogIdent() {
        return logIdent;
    }

}
