/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.request;

import lombok.Data;

/**
 * Created by ziyang.lb
 **/
@Data
public class FlashBackCleanFileParameter {
    String pathPrefix;
}
