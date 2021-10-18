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
 * Logs random seed used by the next RAND(), and by PASSWORD() in 4.1.0. 4.1.1 does not need it (it's repeatable again)
 * so this event needn't be written in 4.1.1 for PASSWORD() (but the fact that it is written is just a waste, it does
 * not cause bugs). The state of the random number generation consists of 128 bits, which are stored internally as two
 * 64-bit numbers. Binary Format The Post-Header for this event type is empty. The Body has two components:
 * <table>
 * <caption>Body for Rand_log_event</caption>
 * <tr>
 * <th>Name</th>
 * <th>Format</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>seed1</td>
 * <td>8 byte unsigned integer</td>
 * <td>64 bit random seed1.</td>
 * </tr>
 * <tr>
 * <td>seed2</td>
 * <td>8 byte unsigned integer</td>
 * <td>64 bit random seed2.</td>
 * </tr>
 * </table>
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class RandLogEvent extends LogEvent {

    /* Rand event data */
    public static final int RAND_SEED1_OFFSET = 0;
    public static final int RAND_SEED2_OFFSET = 8;
    /**
     * Fixed data part: Empty
     * <p>
     * Variable data part:
     * <ul>
     * <li>8 bytes. The value for the first seed.</li>
     * <li>8 bytes. The value for the second seed.</li>
     * </ul>
     * Source : http://forge.mysql.com/wiki/MySQL_Internals_Binary_Log
     */
    private final long seed1;
    private final long seed2;

    public RandLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        /* The Post-Header is empty. The Variable Data part begins immediately. */
        buffer.position(descriptionEvent.commonHeaderLen + descriptionEvent.postHeaderLen[RAND_EVENT - 1]
            + RAND_SEED1_OFFSET);
        seed1 = buffer.getLong64(); // !uint8korr(buf+RAND_SEED1_OFFSET);
        seed2 = buffer.getLong64(); // !uint8korr(buf+RAND_SEED2_OFFSET);
    }

    public final String getQuery() {
        return "SET SESSION rand_seed1 = " + seed1 + " , rand_seed2 = " + seed2;
    }
}
