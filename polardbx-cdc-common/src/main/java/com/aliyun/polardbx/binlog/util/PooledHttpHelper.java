/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * http相关的工具类，带连接池配置
 */
@Slf4j
public class PooledHttpHelper {
    private static final PoolingHttpClientConnectionManager CM = new PoolingHttpClientConnectionManager();

    static {
        // Increase max total connection to 3
        CM.setMaxTotal(3);
        // Increase default max connection per route to 2
        CM.setDefaultMaxPerRoute(2);
        CM.setValidateAfterInactivity(10000);
    }

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
        .setConnectionManager(CM)
        .build();

    public static String doPost(String url, ContentType contentType, String params, int timeout)
        throws URISyntaxException, IOException {
        URI uri = new URIBuilder(url).build();
        RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            .setSocketTimeout(timeout)
            .build();
        HttpPost httpPost = new HttpPost(uri);
        HttpEntity entity = EntityBuilder.create()
            .setContentType(contentType)
            .setText(params).build();
        httpPost.setEntity(entity);
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(config);
        CloseableHttpResponse response = HTTP_CLIENT.execute(httpPost, context);
        int statusCode = response.getStatusLine().getStatusCode();
        String result = EntityUtils.toString(response.getEntity());
        if (statusCode != HttpStatus.SC_OK) {
            log.warn("doPost fail {} {} {} {}", url, contentType, params, statusCode);
        }
        if (log.isDebugEnabled()) {
            log.debug("doPost result {} {} {} {} {}", url, contentType, params, statusCode, result);
        }
        response.close();
        return result;
    }

    public static String doGetWithoutParam(String url, ContentType contentType, int timeout)
        throws URISyntaxException, IOException {
        URI uri = new URIBuilder(url).build();
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            .setSocketTimeout(timeout)
            .build();
        HttpGet httpGet = new HttpGet(uri);
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(config);
        CloseableHttpResponse response = HTTP_CLIENT.execute(httpGet, context);
        int statusCode = response.getStatusLine().getStatusCode();
        String result = EntityUtils.toString(response.getEntity());
        if (statusCode != HttpStatus.SC_OK) {
            log.warn("doGet fail {} {} {}", url, contentType, statusCode);
        }
        response.close();
        return result;
    }
}
