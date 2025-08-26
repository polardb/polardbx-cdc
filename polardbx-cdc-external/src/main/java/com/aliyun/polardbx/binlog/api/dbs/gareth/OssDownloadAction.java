/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;

public class OssDownloadAction extends DbsDownloadCmd{
    public OssDownloadAction(String garethConfig) {
        super(garethConfig);
    }

    @Override
    protected JSONObject buildSrcInfo() {
        JSONObject srcConfig = new JSONObject();
        JSONObject jsonObject = JSONObject.parseObject(garethConfig);
        srcConfig.put("type", "oss");
        srcConfig.put("encrypt", false);
        for (String key : jsonObject.keySet()){
            String value = jsonObject.getString(key);
            srcConfig.put(key, value);
        }
        return srcConfig;
    }
}
