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
 * mariadb10的BINLOG_CHECKPOINT_EVENT类型
 *
 * @author jianghang 2014-1-20 下午2:22:04
 * @since 1.0.17
 */
public class BinlogCheckPointLogEvent extends IgnorableLogEvent {

    public BinlogCheckPointLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);
        // do nothing , just mariadb binlog checkpoint
    }

}
