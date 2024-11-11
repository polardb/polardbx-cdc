/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.api.rds.sts.Credentials;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.util.Date;

/**
 * @author chengjin.lyf on 2018/3/28 下午3:41
 * @since 3.2.6
 */
public class DescribeBinlogFilesRequest extends AbstractRequest<DescribeBinlogFileResult> {

    public DescribeBinlogFilesRequest() {
        setVersion("2014-08-15");
        setEndPoint(DEFAULT_RDS_API_DOMAIN);
        putQueryString("Action", "DescribeBinlogFiles");

    }

    public void setCredentials(Credentials credentials) {
        putQueryString("SecurityToken", credentials.getSecurityToken());
        setAccessKeyId(credentials.getAccessKeyId());
        setAccessKeySecret(credentials.getAccessKeySecret());
    }

    public void setRdsInstanceId(String rdsInstanceId) {
        putQueryString("DBInstanceId", rdsInstanceId);
    }

    public void setPageSize(int pageSize) {
        putQueryString("PageSize", String.valueOf(pageSize));
    }

    public void setPageNumber(int pageNumber) {
        putQueryString("PageNumber", String.valueOf(pageNumber));
    }

    public void setStartDate(Date startDate) {
        putQueryString("StartTime", formatUTCTZ(startDate));
    }

    public void setEndDate(Date endDate) {
        putQueryString("EndTime", formatUTCTZ(endDate));
    }

    public void setResourceOwnerId(String resourceOwnerId) {
        putQueryString("ResourceOwnerId", resourceOwnerId);
    }

    @Override
    protected DescribeBinlogFileResult processResult(HttpResponse response) throws Exception {
        String result = EntityUtils.toString(response.getEntity());
        DescribeBinlogFileResult describeBinlogFileResult = JSONObject
            .parseObject(result, new TypeReference<DescribeBinlogFileResult>() {
            });
        return describeBinlogFileResult;
    }
}
