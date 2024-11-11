/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * created by ziyang.lb
 **/
@Data
public class RecoverySearchTaskResultMeta {
    long sqlCounter;
    int injectTroubleCount;
    Map<String, String> fileMd5Map;
    Map<String, Long> fileSizeMap;

    public RecoverySearchTaskResultMeta() {
        this.sqlCounter = 0;
        this.fileMd5Map = new HashMap<>();
        this.fileSizeMap = new HashMap<>();
    }
}
