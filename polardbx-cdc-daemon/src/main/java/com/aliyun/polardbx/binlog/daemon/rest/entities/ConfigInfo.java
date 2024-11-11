/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.entities;

import lombok.Data;

/**
 * @author Timmy
 */
@Data
public class ConfigInfo {
    private String key, value;
}
