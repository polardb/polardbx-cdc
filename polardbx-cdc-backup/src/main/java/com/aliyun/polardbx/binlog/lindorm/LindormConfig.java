/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.lindorm;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class LindormConfig {
    private String accessKey;
    private String accessSecret;
    private String bucket;
    private String ip;
    private Integer port;
    private Integer downloadPort;
    private String polardbxInstance;

    public boolean isAvaliable() {
        return StringUtils.isNotBlank(accessSecret) &&
            StringUtils.isNotBlank(accessKey) &&
            StringUtils.isNotBlank(bucket) &&
            StringUtils.isNotBlank(ip) &&
            StringUtils.isNotBlank(polardbxInstance) &&
            port != null &&
            downloadPort != null;
    }
}
