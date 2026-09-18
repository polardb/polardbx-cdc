/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile;

/**
 * 用于快速从binlog 中搜索各种东西，目前仅搜索last tso
 *
 * @author zm
 */
public interface BinlogFileSeekHandler {
    /**
     * 获取最后一个tso
     */
    SeekResult seekLastTso(BinlogFile file, int mode, int seekBufferSize, long startPos);
}
