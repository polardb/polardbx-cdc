/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;

import java.util.Map;
import java.util.Set;

public interface EventReformater<T extends LogEvent> {
    Set<Integer> interest();

    boolean accept(T event);

    void register(Map<Integer, EventReformater> map);

    boolean reformat(T event, TxnItemRef txnItemRef, ReformatContext context, EventData eventData) throws Exception;
}
