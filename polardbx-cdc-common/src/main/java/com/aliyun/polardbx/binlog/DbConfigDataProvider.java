/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;

public class DbConfigDataProvider implements IConfigDataProvider {
    @Override
    public String getValue(String key) {
        if (!SpringContextHolder.isInitialize()) {
            throw new PolardbxException(
                "spring context not initialize , can not use system db config get config by key : " + key);
        }
        return SystemDbConfig.getCachedSystemDbConfig(key);
    }
}
