/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.dao;

import lombok.Data;

@Data
public class BinlogTaskInfoExt {
    /**
     * 是否支持生成tableId
     */
    private boolean tableIdEnabled = true;
}
