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
package com.aliyun.polardbx.binlog.download.rds.sts;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.download.rds.AbstractRequest;
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
