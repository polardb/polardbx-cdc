/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import lombok.Setter;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Setter
public class DescribeStorageInfoRequest {
    private String endPoint;
    private String callerBid;
    private String uid;
    private String storageEntityId;
    private String region;

    /**
     * 系统请求访问标识
     * Dukang、YaoChi
     * 非必须
     */
    private String AccessKey;

    private String AccessSecretKey;

    public static String createHmacSha1(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec sec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(sec);
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return new String(new Hex().encode(digest), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
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

    public DescribeStorageInfoResult doRequest() {
        StringBuilder apiBuilder = new StringBuilder();
        apiBuilder.append("/service?");
        Map<String, String> paramMap = new TreeMap<>();
        paramMap.put("Action", "DescribeStorageInfo");
        paramMap.put("CallerBid", callerBid);
        paramMap.put("UserId", uid);
        paramMap.put("StorageEntityId", urlEncode(storageEntityId));
        paramMap.put("Region", urlEncode(region));


        apiBuilder.append(
            paramMap.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + "&" + b).get());
        String data =
            String.format("%s&accessKey=%s&timestamp=%d", apiBuilder, AccessKey, System.currentTimeMillis());
        String signature = createHmacSha1(data, AccessSecretKey);
        String requestUrl = String.format("%s%s&signature=%s", endPoint, data, signature);
        String result = HttpHelper.doGet(requestUrl, null, null);
        DescribeStorageInfoResult resultObj = JSON.parseObject(result, DescribeStorageInfoResult.class);
        JSONObject resultJson = JSON.parseObject(result);
        resultObj.setDataJson(resultJson.getString("Data"));
        return resultObj;
    }
}
