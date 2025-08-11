/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;

public class S3DownloadAction extends DbsDownloadCmd{
    public S3DownloadAction(String garethConfig) {
        super(garethConfig);
    }

    @Override
    protected JSONObject buildSrcInfo() {
        JSONObject jsonObject = JSONObject.parseObject(garethConfig);
        JSONObject srcConfig = new JSONObject();
        srcConfig.put("type", "s3");
        for (String key : jsonObject.keySet()){
            String value = jsonObject.getString(key);
            srcConfig.put(key, value);
        }
        return srcConfig;
    }
}
