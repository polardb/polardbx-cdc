/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

import java.io.File;

/**
 * @author ziyang.lb
 */
public interface ILocalBinlogEventListener {
    void onEnd();

    void onFinishFile(File binlogFile, BinlogPosition pos);
}
