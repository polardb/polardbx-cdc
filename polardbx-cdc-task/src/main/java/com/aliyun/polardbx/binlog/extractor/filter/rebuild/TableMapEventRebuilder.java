/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
