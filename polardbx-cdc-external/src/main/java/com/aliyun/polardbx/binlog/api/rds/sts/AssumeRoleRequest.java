/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds.sts;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.api.rds.AbstractRequest;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.util.UUID;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:17
 * @since 3.2.6
 */
public class AssumeRoleRequest extends AbstractRequest<Credentials> {

    private String role;

    private String uid;

    public AssumeRoleRequest() {
        setVersion("2015-04-01");
        setProtocol("https");
        setEndPoint(DEFAULT_STS_API_DOMAIN);
        setAccessKeyId(DEFAULT_AK);
        setAccessKeySecret(DEFAULT_SK);
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    protected void processBefore() {
        String roleArn = "acs:ram::" + uid + ":role/" + role;
        putQueryString("Action", "AssumeRoleWithServiceIdentity");
        putQueryString("RoleArn", roleArn);
        putQueryString("AssumeRoleFor", uid);
        putQueryString("RoleSessionName", UUID.randomUUID().toString().toUpperCase().substring(0, 30));
    }

    @Override
    public Credentials processResult(HttpResponse response) throws Exception {
        String result = EntityUtils.toString(response.getEntity());
        JSONObject object = JSONObject.parseObject(result);
        String credstr = object.getString("Credentials");
        Credentials credentials = JSON.parseObject(credstr, Credentials.class);
        return credentials;
    }
}
