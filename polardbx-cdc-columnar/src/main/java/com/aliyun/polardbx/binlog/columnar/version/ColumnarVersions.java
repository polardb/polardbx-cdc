/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar.version;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.columnar.ColumnarMetaManager;
import com.aliyun.polardbx.binlog.columnar.ColumnarNodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;

@Slf4j
public class ColumnarVersions {
    private static final String COLUMNAR_VERSIONS_URL = "http://%s:%s/columnarServer/version";

    public static String getVersionsByHttp() {
        final String localIp = DynamicApplicationConfig.getString(INST_IP);
        final ColumnarNodeInfo columnarNode = ColumnarMetaManager.getInstant().getNodeInfo(localIp);
        if (StringUtils.isBlank(columnarNode.getIp()) || StringUtils.isBlank(columnarNode.getPort())) {
            return "";
        }

        final String url = getUrlForColumnarVersions(columnarNode);
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return response.toString();
            } else {
                log.error("Query version from columnar server failed! " + responseCode);
            }
        } catch (IOException e) {
            log.error("Query version from columnar server failed!", e);
        }
        return "";
    }

    public static String getUrlForColumnarVersions(ColumnarNodeInfo columnarLeader) {
        return String.format(COLUMNAR_VERSIONS_URL, "127.0.0.1", columnarLeader.getPort());
    }
}
