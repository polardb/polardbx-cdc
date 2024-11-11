/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.lindorm;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * @author chengjin
 */
@Data
public class LindormConfig {
    private String accessKey;
    private String accessSecret;
    /**
     * 1. Must be between 3 and 63 characters long.
     * 2. Can consist only of lowercase letters, numbers, dots (.), and hyphens (-)
     * 3. must begin and end with a letter or number
     * 4. Must not be formatted as an IP address
     */
    private String bucket;
    private String ip;
    private Integer thriftPort;
    private Integer s3Port;
    private String polardbxInstance;

    public boolean isAvailable() {
        return StringUtils.isNotBlank(accessSecret)
                && StringUtils.isNotBlank(accessKey)
                && StringUtils.isNotBlank(bucket)
                && StringUtils.isNotBlank(ip)
                && StringUtils.isNotBlank(polardbxInstance)
                && thriftPort != null
                && s3Port != null;
    }
}
