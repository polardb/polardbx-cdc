/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
