/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event.mariadb;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IgnorableLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;

/**
 * mariadb的GTID_EVENT类型
 *
 * @author jianghang 2014-1-20 下午4:49:10
 * @since 1.0.17
 */
public class MariaGtidLogEvent extends IgnorableLogEvent {

    public MariaGtidLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);
        // do nothing , just ignore log event
    }

}
