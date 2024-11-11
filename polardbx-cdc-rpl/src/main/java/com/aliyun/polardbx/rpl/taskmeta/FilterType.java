/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

public enum FilterType {

    // 无filter， 自行在applier中实现过滤逻辑
    NO_FILTER,

    // 标准filter，目前用于data import
    IMPORT_FILTER,

    // replica使用的filter
    RPL_FILTER,

    // 闪回使用的filter
    FLASHBACK_FILTER

}

