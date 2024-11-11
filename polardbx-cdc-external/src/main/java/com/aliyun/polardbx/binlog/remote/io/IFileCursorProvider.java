/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.io;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;

public interface IFileCursorProvider {

    /**
     * 获得指定流的最新cursor（binlog文件位点）
     *
     * @param stream stream name
     * @return Cursor
     */
    BinlogCursor getCursor(String stream);
}
