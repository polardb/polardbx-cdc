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
package com.aliyun.polardbx.binlog.remote.oss;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class OssConfig {

    public String endpoint = "";
    /**
     * 阿里云主账号AccessKey拥有所有API的访问权限，风险很高。强烈建议您创建并使用RAM用户进行API访问或日常运维，请登录RAM控制台创建RAM用户。
     */
    public String accessKeyId = "<yourAccessKeyId>";
    public String accessKeySecret = "<yourAccessKeySecret>";
    public String bucketName = "<polarx-cdc/polarxInstanceId>";
    public String polardbxInstance;

    public boolean isAvailable() {
        return StringUtils.isNotBlank(endpoint) &&
            StringUtils.isNotBlank(accessKeyId) &&
            StringUtils.isNotBlank(accessKeySecret) &&
            StringUtils.isNotBlank(bucketName) &&
            StringUtils.isNotBlank(polardbxInstance);
    }

}
