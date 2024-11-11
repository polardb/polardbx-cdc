/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import lombok.Data;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
@Data
public class StorageContent {
    /**
     * 标识storage inst id list的内容是否是经过修复后的
     */
    boolean repaired;
    /**
     * storage inst id list
     */
    List<String> storageInstIds;
}
