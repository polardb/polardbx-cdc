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

/**
 * Log row updates with a before image. The event contain several update rows for a table. Note that each event contains
 * only rows for one table. Also note that the row data consists of pairs of row data: one row for the old data and one
 * row for the new data.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class UpdateRowsLogEvent extends RowsLogEvent {

    public UpdateRowsLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);
    }

    @Override
    public String info() {
        if (getFlags(STMT_END_F) == 1) {
            return String.format("table_id: %s flags: STMT_END_F", getTableId());
        } else {
            return String.format("table_id: %s", getTableId());
        }
    }
}
