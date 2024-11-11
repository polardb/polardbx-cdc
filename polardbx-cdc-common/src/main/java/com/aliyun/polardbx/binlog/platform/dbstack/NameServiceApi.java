/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.platform.dbstack;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.lang3.StringUtils;

public class NameServiceApi {

    private static String nsIp;
    private static String nsPort;

    static {
        nsIp = getProperties("NAME_SERVICE_SERVICE_HOST");
        nsPort = getProperties("NAME_SERVICE_SERVICE_PORT_NS_PORT");
    }

    private static String getProperties(String key) {
        String v = System.getenv(key);
        if (StringUtils.isBlank(v)) {
            v = System.getProperty(key);
        }
        return v;
    }

    public static NsServiceType queryLindormFileService() {
        String reponse = HttpHelper.doGet(String
            .format("http://%s:%s/v1/services/findByConditions?serviceTypeName=BACKUP_LINDORM_FILESERVICE", nsIp,
                nsPort), null, null);
        NsResponse response = JSON.parseObject(reponse, NsResponse.class);
        return response.getData().get(0);
    }

    public static void main(String[] args) {
        String reponse =
            "{\"code\":200,\"message\":\"successful\",\"data\":[{\"id\":20,\"serviceTypeId\":200,\"serviceTypeName\":\"BACKUP_LINDORM_FILESERVICE\",\"name\":\"BACKUP_LINDORM_FILESERVICE\",\"description\":\"\",\"versionCode\":\"default\",\"bizType\":\"default\",\"status\":1,\"protocol\":\"http\",\"gwCrossDomain\":0,\"priority\":0,\"locationId\":1,\"customConfig\":\"\",\"gwSubDomain\":\"lfs-for-dbstack-fileservice.lindorm.paas.dbstackhybridpolarxmysql0501.com:9190\",\"gwUpstream\":\"lfs-for-dbstack-fileservice.lindorm.paas.dbstackhybridpolarxmysql0501.com.com\",\"gmtCreated\":\"2021-05-07T12:56:39Z\",\"gmtModified\":\"2021-05-07T12:56:39Z\",\"tags\":null,\"locationCode\":\"cn-qd-vpaas000-d01\",\"registries\":null}]}";
        NsResponse response = JSON.parseObject(reponse, NsResponse.class);
        System.out.println(JSON.toJSONString(response));
    }
}
