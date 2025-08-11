/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import lombok.Setter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

@Setter
public class DescribeUnifyArchiveLogFilesRequest {

    private String endPoint;
    /**
     * rds 实例名
     * 必须
     */
    private String InstanceName;
    /**
     * 备份集所属区域
     * 示例
     * cn-hangzhou
     */
    private String RegionCode;
    /**
     * CallerBid	CallerBid	是	String	26842，前端控制台无需传
     * 用户ID	UserId	是	String	1788966360195792，前端控制台无需传
     */
    private String CallerBid;
    private String UserId;
    /**
     * 系统请求访问标识
     * Dukang、YaoChi
     * 非必须
     */
    private String AccessKey;

    private String AccessSecretKey;
    /**
     * 日志备份状态,
     * ● Fetching
     * ● Uploading（瞬态）
     * ● Completed
     * 非必须
     */
    private String LogStatus = "Completed";
    /**
     * 开始时间,2021-12-23T06:11:52Z，yyyy-MM-ddTHH:mmZ格式
     */
    private long StartTime;
    /**
     * 结束时间,2021-12-23T06:11:52Z，yyyy-MM-ddTHH:mmZ格式，需大于StartTime
     */
    private long EndTime;
    /**
     * 每页记录数, 默认20
     */
    private Integer PageSize;
    /**
     * 页码,从1开始，默认为1
     */
    private Integer PageNumber;

    private boolean skipAuth = false;

    public static String createHmacSha1(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec sec = new SecretKeySpec(secret.getBytes(), "HmacSHA1");
            mac.init(sec);
            byte[] digest = mac.doFinal(data.getBytes());
            return new String(new Hex().encode(digest), "UTF-8");
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            throw new PolardbxException("createHmacSha1 error", e);
        }
    }

    public String urlEncode(String values) {
        try {
            return URLEncoder.encode(values, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new PolardbxException("encode value failed!", e);
        }
    }

    public String formatDate(long timestamp) {
        return DateFormatUtils.format(timestamp, "yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"));
    }

    public DescribeUnifyArchiveLogFilesResult doRequest() {
        StringBuilder apiBuilder = new StringBuilder();
        apiBuilder.append("/service?");
        Map<String, String> paramMap = new TreeMap<>();
        paramMap.put("Action", "DescribeUnifyArchiveLogFiles");
        paramMap.put("CallerBid", CallerBid);
        paramMap.put("UserId", UserId);
        paramMap.put("InstanceName", urlEncode(InstanceName));
        paramMap.put("RegionCode", urlEncode(RegionCode));

        if (StringUtils.isNotBlank(LogStatus)) {
            paramMap.put("LogStatus", LogStatus);
        }
        paramMap.put("StartTime", urlEncode(formatDate(StartTime)));
        paramMap.put("EndTime", urlEncode(formatDate(EndTime)));
        paramMap.put("PageSize", PageSize + "");
        paramMap.put("PageNumber", PageNumber + "");
        if (skipAuth) {
            paramMap.put("__skipAuth", "1");
        }
        apiBuilder.append(
            paramMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + "&" + b).get());
        String api = apiBuilder.toString();
        String requestUrl;
        if (!skipAuth) {
            String data =
                String.format("%s&accessKey=%s&timestamp=%d", apiBuilder, AccessKey, System.currentTimeMillis());
            String signature = createHmacSha1(data, AccessSecretKey);
            requestUrl = String.format("%s%s&signature=%s", endPoint, data, signature);
        } else {
            requestUrl = String.format("%s%s", endPoint, api);
        }
        String result = HttpHelper.doGet(requestUrl, null, null);
        return JSON.parseObject(result, DescribeUnifyArchiveLogFilesResult.class);
    }

}
