/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

/**
 * @author yudong
 * @since 2023/7/19 17:28
 **/
public interface BinlogDumpRotateObserver {
    void onRotate();
}
