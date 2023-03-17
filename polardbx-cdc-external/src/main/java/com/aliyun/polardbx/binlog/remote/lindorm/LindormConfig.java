/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
