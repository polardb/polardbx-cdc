/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import org.apache.commons.lang3.StringUtils;

public class GarethActionFactory {
    public static DbsDownloadCmd create(String type, String garethConfig){
        switch (StringUtils.lowerCase(type)){
            case "nas":
                return new NasDownloadAction(garethConfig);
            case "oss":
                return new OssDownloadAction(garethConfig);
            case "s3":
                return new S3DownloadAction(garethConfig);
            case "lindorm":
                return new LindormDownloadAction(garethConfig);
            default:
                return new DefaultDownloadAction(type, garethConfig);
        }
    }
}
