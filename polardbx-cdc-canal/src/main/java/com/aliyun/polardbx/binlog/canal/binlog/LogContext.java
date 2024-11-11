/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Changyuan.lh
 * @version 1.0
 */
public final class LogContext {

    private final Map<Long, TableMapLogEvent> mapOfTable = new HashMap<Long, TableMapLogEvent>();

    private FormatDescriptionLogEvent formatDescription;

    private LogPosition logPosition;

    private ServerCharactorSet serverCharactorSet;

    public LogContext() {
        this.formatDescription = FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x;
    }

    public LogContext(FormatDescriptionLogEvent descriptionEvent) {
        this.formatDescription = descriptionEvent;
    }

    public final LogPosition getLogPosition() {
        return logPosition;
    }

    public final void setLogPosition(LogPosition logPosition) {
        this.logPosition = logPosition;
    }

    public final FormatDescriptionLogEvent getFormatDescription() {
        return formatDescription;
    }

    public final void setFormatDescription(FormatDescriptionLogEvent formatDescription) {
        this.formatDescription = formatDescription;
    }

    public ServerCharactorSet getServerCharactorSet() {
        return serverCharactorSet;
    }

    public void setServerCharactorSet(ServerCharactorSet serverCharactorSet) {
        this.serverCharactorSet = serverCharactorSet;
    }

    public final void putTable(TableMapLogEvent mapEvent) {
        mapOfTable.put(mapEvent.getTableId(), mapEvent);
    }

    public final TableMapLogEvent getTable(final long tableId) {
        return mapOfTable.get(Long.valueOf(tableId));
    }

    public final void clearAllTables() {
        mapOfTable.clear();
    }

    public void reset() {
        formatDescription = FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x;

        mapOfTable.clear();
    }
}
