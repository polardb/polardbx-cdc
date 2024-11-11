/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastTimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.UUID;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_ACCESS_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_ACCESS_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_URL;

/**
 * @author chengjin.lyf on 2019/4/19 5:05 PM
 * @since 1.0.25
 */
@Slf4j
public class RdsApi {
    private static String url = DynamicApplicationConfig.getString(RDS_API_URL);

    static {
        String rdsApiUrl = getEnv("RDS_API");

        if (StringUtils.isBlank(rdsApiUrl)) {
            String service = getEnv("RDSAPI_SERVICE_HOST");
            String port = getEnv("RDSAPI_SERVICE_PORT", "80");
            if (StringUtils.isNotBlank(service)) {
                rdsApiUrl = "http://" + service + ":" + port + "/services";
            }
        }

        if (StringUtils.isNotBlank(rdsApiUrl)) {
            url = rdsApiUrl;
        }
        System.out.println("rds api : " + url);
    }

    private static String getEnv(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        String v = System.getenv(key);
        if (StringUtils.isBlank(v)) {
            v = System.getProperty(key);
        }
        return v;
    }

    private static String getEnv(String key, String defaultValue) {
        String v = getEnv(key);
        if (StringUtils.isBlank(v)) {
            return defaultValue;
        }
        return v;
    }

    /**
     * @param uid "petadata" + uid
     */
    public static DescribeBinlogFilesResult describeBinlogFiles(
        String dbInstanceName,
        String uid,
        String user_id,
        String startTime,
        String endTime,
        Integer maxRecordsPerPage,
        Integer pageNumbers) throws Exception {

        Map<String, String> params = new HashMap<>();
        params.put("Action", "DescribeBinlogFiles");
        params.put("DBInstanceName", dbInstanceName);
        params.put("UID", uid);
        params.put("User_ID", user_id);
        params.put("StartTime", startTime);
        params.put("EndTime", endTime);
        params.put("MaxRecordsPerPage", maxRecordsPerPage + "");
        params.put("PageNumbers", pageNumbers + "");
        String resp = submitFormRequest(params);
        JSONObject object = JSON.parseObject(resp);
        if (object.getString("Code").equalsIgnoreCase("200")) {
            return JSON.parseObject(object.getString("Data"), new TypeReference<DescribeBinlogFilesResult>() {
            });
        } else {
            throw new RuntimeException(resp);
        }
    }

    private static String submitFormRequest(Map<String, String> parms) throws Exception {
        String accessID = DynamicApplicationConfig.getString(RDS_API_ACCESS_ID);
        String accessKey = DynamicApplicationConfig.getString(RDS_API_ACCESS_KEY);
        parms.put("AccessId", accessID);
        parms.put("TimeStamp", getTimeStamp());
        parms.put("RequestId", (UUID.randomUUID()).toString());
        parms.put("Signature", createSignature(parms, accessKey));
        StringBuilder sb = new StringBuilder();
        sb.append(url).append("?");
        for (Map.Entry<String, String> entry : parms.entrySet()) {
            log.info("params: {}: {}", entry.getKey(), entry.getValue());
            sb.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), "utf-8")).append("&");
        }

        sb.delete(sb.length() - 1, sb.length());
        log.info("Request url for query binlog list is :" + sb.toString());

        int i = 0;
        while (true) {
            i++;
            try {
                return HttpHelper.get(sb.toString(), 20000);
            } catch (Throwable t) {
                if (i > 3) {
                    throw t;
                }
            }
        }
    }

    public static String getTimeStamp() {
        SimpleDateFormat timeStampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Calendar cal = Calendar.getInstance(new SimpleTimeZone(0, "GMT"));
        timeStampFormat.setCalendar(cal);
        return timeStampFormat.format(new Date());
    }

    public static String createSignature(Map<String, String> paramMap, String key) {
        String data = getParamString(paramMap);
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String getParamString(Map<String, String> paramMap) {
        if (paramMap == null || paramMap.isEmpty()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        List<String> keys = new ArrayList<>(paramMap.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            String value = paramMap.get(key);
            if (value != null) {
                try {
                    value = URLEncoder.encode(value, "UTF-8").replace("%2A", "*");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    value = null;
                }
            }
            buffer.append("&").append(key).append("=").append(value);
        }
        return buffer.substring(1);
    }

    public static final String formatUTCTZ(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(FastTimeZone.getGmtTimeZone());
        return sdf.format(date);
    }

}
