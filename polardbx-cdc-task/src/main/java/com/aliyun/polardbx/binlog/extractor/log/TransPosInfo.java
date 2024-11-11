/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * created by ziyang.lb
 **/
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class TransPosInfo {
    private String binlogFile;
    private long pos;
    private long when;

    @Override
    public String toString() {
        return binlogFile + ":" + pos + ":" + when;
    }
}
