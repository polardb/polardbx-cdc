/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.api.rds.sts.Credentials;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

/**
 * rds 备份策略查询
 *
 * @author chengjin.lyf on 2018/3/28 下午3:41
 * @since 3.2.6
 */
public class DescribeBackupPolicyRequest extends AbstractRequest<RdsBackupPolicy> {

    public DescribeBackupPolicyRequest() {
        setVersion("2014-08-15");
        setEndPoint(DEFAULT_RDS_API_DOMAIN);
        putQueryString("Action", "DescribeBackupPolicy");

    }

    public void setCredentials(Credentials credentials) {
        putQueryString("SecurityToken", credentials.getSecurityToken());
        setAccessKeyId(credentials.getAccessKeyId());
        setAccessKeySecret(credentials.getAccessKeySecret());
    }

    public void setRdsInstanceId(String rdsInstanceId) {
        putQueryString("DBInstanceId", rdsInstanceId);
    }

    @Override
    protected RdsBackupPolicy processResult(HttpResponse response) throws Exception {
        String result = EntityUtils.toString(response.getEntity());
        JSONObject jsonObj = JSON.parseObject(result);
        RdsBackupPolicy policy = new RdsBackupPolicy();
        policy.setBackupRetentionPeriod(jsonObj.getString("BackupRetentionPeriod"));
        policy.setBackupLog(jsonObj.getString("BackupLog").equalsIgnoreCase("Enable"));
        policy.setLogBackupRetentionPeriod(jsonObj.getIntValue("LogBackupRetentionPeriod"));
        policy.setPreferredBackupPeriod(jsonObj.getString("PreferredBackupPeriod"));
        policy.setPreferredBackupTime(jsonObj.getString("PreferredBackupTime"));
        return policy;
    }
}
