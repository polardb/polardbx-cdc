/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;

public class NasDownloadAction extends DbsDownloadCmd{
    public NasDownloadAction(String garethConfig) {
        super(garethConfig);
    }


    @Override
    protected JSONObject buildSrcInfo(){
        JSONObject jsonObject = JSONObject.parseObject(garethConfig);
        String originalIp = jsonObject.getString("originalIp");
        String originalPort = jsonObject.getString("originalPort");
        String region = jsonObject.getString("region");
        JSONObject srcConfig = new JSONObject();
        srcConfig.put("type", "nas");
        srcConfig.put("region", region);
        srcConfig.put("encrypt", false);
        srcConfig.put("ip", originalIp);
        srcConfig.put("port", originalPort);
        for (String key : jsonObject.keySet()){
            String value = jsonObject.getString(key);
            srcConfig.put(key, value);
        }
        return srcConfig;
    }
}
