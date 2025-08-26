/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;

public class DefaultDownloadAction  extends DbsDownloadCmd{

    private String type;

    public DefaultDownloadAction(String type, String garethConfig) {
        super(garethConfig);
        this.type = type;
    }

    @Override
    protected JSONObject buildSrcInfo() {
        JSONObject jsonObject = JSONObject.parseObject(garethConfig);
        JSONObject srcConfig = new JSONObject();
        srcConfig.put("type", type);
        for (String key : jsonObject.keySet()){
            String value = jsonObject.getString(key);
            srcConfig.put(key, value);
        }
        return srcConfig;
    }
}
