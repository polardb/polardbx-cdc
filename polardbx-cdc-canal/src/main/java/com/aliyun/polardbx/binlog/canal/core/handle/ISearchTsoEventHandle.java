/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

public interface ISearchTsoEventHandle extends EventHandle {
    public String getLastSearchFile();

    public void setEndPosition(BinlogPosition endPosition);

    public BinlogPosition searchResult();

    public String getTopologyContext();

    public String getCommandId();

    public BinlogPosition getCommandPosition();

    public String region();
}
