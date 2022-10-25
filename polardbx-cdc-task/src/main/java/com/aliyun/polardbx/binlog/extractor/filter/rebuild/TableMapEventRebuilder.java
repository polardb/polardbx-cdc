/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.utils.BitMap;

public class TableMapEventRebuilder {

    public static TableMapEventBuilder convert(TableMapLogEvent event, long serverId, String charSet) {
        LogHeader lg = event.getHeader();
        TableMapEventBuilder tableMapEventBuilder = new TableMapEventBuilder((int) event.getWhen(),
            serverId,
            event.getTableId(),
            event.getDbName(),
            event.getTableName(),
            charSet);
        tableMapEventBuilder.setFlags((short) lg.getFlags());
        tableMapEventBuilder.set_flags(event.getFlags());

        tableMapEventBuilder.setNullBitmap(new BitMap(event.getColumnCnt(), event.getNullBits()));
        TableMapLogEvent.ColumnInfo[] columnInfos = event.getColumnInfo();
        byte[] columnType = new byte[columnInfos.length];
        byte[][] columnMeta = new byte[columnInfos.length][];
        int i = 0;
        for (TableMapLogEvent.ColumnInfo columnInfo : columnInfos) {
            columnType[i] = (byte) columnInfo.type;
            columnMeta[i++] = columnInfo.metaBuff;
        }
        tableMapEventBuilder.setColumnDefType(columnType);
        tableMapEventBuilder.setColumnMetaData(columnMeta);
        return tableMapEventBuilder;
    }
}
