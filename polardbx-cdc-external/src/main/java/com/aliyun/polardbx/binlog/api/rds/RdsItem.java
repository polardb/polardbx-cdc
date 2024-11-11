/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

import java.util.List;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:52
 * @since 3.2.6
 */
public class RdsItem {
    private List<BinlogFile> BinLogFile;

    public List<BinlogFile> getBinLogFile() {
        return BinLogFile;
    }

    public void setBinLogFile(List<BinlogFile> binLogFile) {
        BinLogFile = binLogFile;
    }
}
