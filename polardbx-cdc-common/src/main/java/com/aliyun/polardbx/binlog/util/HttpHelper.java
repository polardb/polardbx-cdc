/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Lists;
import io.grpc.netty.shaded.io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * http相关的工具类 Created by hanxuan.mh on 14-6-24.
 */
public class HttpHelper {

    private static final int READ_TIMEOUT = 50000;
    private static final int CONNECT_TIMEOUT = 20000;
    private static Logger logger = LoggerFactory.getLogger(HttpHelper.class);

    public static String get(String url, int timeout) {
        // logger.info("get url is :" + url);
        // 支持采用https协议，忽略证书
        url = url.trim();
        if (url.startsWith("https")) {
            return getIgnoreCerf(url, null, null, timeout);
        }
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        CloseableHttpClient httpclient = builder.build();
        CloseableHttpResponse response = null;
        HttpGet httpGet = null;
        try {
            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
            httpGet = new HttpGet(uri);
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);
            response = httpclient.execute(httpGet, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return EntityUtils.toString(response.getEntity());
            } else {
                String errorMsg = EntityUtils.toString(response.getEntity());
                throw new PolardbxException("requestGet remote error, url=" + uri.toString() + ", code=" + statusCode
                    + ", error msg=" + errorMsg);
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, null, null, cost);
            throw new PolardbxException("requestGet remote error, request : " + curlRequest, t);
        } finally {
            try {
                long end = System.currentTimeMillis();
                long cost = end - start;
                printCurlRequest(url, null, null, cost);
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                    }
                }
                httpGet.releaseConnection();
            } catch (Exception e) {
            }
        }
    }

    private static String getIgnoreCerf(String url, CookieStore cookieStore, Map<String, String> params, int timeout) {
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        HttpGet httpGet = null;
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getHttpClient();

            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
            httpGet = new HttpGet(uri);
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);
            response = httpClient.execute(httpGet, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return EntityUtils.toString(response.getEntity());
            } else {
                String errorMsg = EntityUtils.toString(response.getEntity());
                throw new PolardbxException("requestGet remote error, url=" + uri.toString() + ", code=" + statusCode
                    + ", error msg=" + errorMsg);
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, cookieStore, params, cost);
            throw new PolardbxException("requestPost(Https) remote error, request : " + curlRequest, t);
        } finally {
            long end = System.currentTimeMillis();
            long cost = end - start;
            printCurlRequest(url, null, null, cost);
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                }
            }
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
    }

    private static String postIgnoreCerf(String url, CookieStore cookieStore, Map<String, String> params, int timeout) {
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        HttpPost httpPost = null;
        CloseableHttpResponse response = null;
        try {

            CloseableHttpClient httpClient = getHttpClient();

            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
            httpPost = new HttpPost(uri);
            List<NameValuePair> parameters = Lists.newArrayList();
            for (String key : params.keySet()) {
                NameValuePair nameValuePair = new BasicNameValuePair(key, params.get(key));
                parameters.add(nameValuePair);
            }
            httpPost.setEntity(new UrlEncodedFormEntity(parameters, Charset.forName("UTF-8")));
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);
            context.setCookieStore(cookieStore);

            response = httpClient.execute(httpPost, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                long end = System.currentTimeMillis();
                long cost = end - start;
                printCurlRequest(url, cookieStore, params, cost);
                return EntityUtils.toString(response.getEntity());
            } else {
                long end = System.currentTimeMillis();
                long cost = end - start;
                String curlRequest = getCurlRequest(url, cookieStore, params, cost);
                throw new PolardbxException(
                    "requestPost(Https) remote error, request : " + curlRequest + ", statusCode=" + statusCode + "");
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, cookieStore, params, cost);
            throw new PolardbxException("requestPost(Https) remote error, request : " + curlRequest, t);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                }
            }
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
        }
    }

    public static String post(String url, CookieStore cookieStore, Map<String, String> params, int timeout) {
        url = url.trim();
        // 支持采用https协议，忽略证书
        if (url.startsWith("https")) {
            return postIgnoreCerf(url, cookieStore, params, timeout);
        }
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        HttpPost httpPost = null;
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpclient = builder.build();
            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
            httpPost = new HttpPost(uri);
            List<NameValuePair> parameters = Lists.newArrayList();
            for (String key : params.keySet()) {
                NameValuePair nameValuePair = new BasicNameValuePair(key, params.get(key));
                parameters.add(nameValuePair);
            }
            httpPost.setEntity(new UrlEncodedFormEntity(parameters, Charset.forName("UTF-8")));
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);
            context.setCookieStore(cookieStore);

            response = httpclient.execute(httpPost, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                long end = System.currentTimeMillis();
                long cost = end - start;
                printCurlRequest(url, cookieStore, params, cost);
                return EntityUtils.toString(response.getEntity());
            } else {
                long end = System.currentTimeMillis();
                long cost = end - start;
                String curlRequest = getCurlRequest(url, cookieStore, params, cost);
                throw new PolardbxException("requestPost remote error, request : " + curlRequest + ", statusCode="
                    + statusCode + ";" + EntityUtils.toString(response.getEntity()));
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, cookieStore, params, cost);
            throw new PolardbxException("requestPost remote error, request : " + curlRequest, t);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                }
            }
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
        }
    }

    public static void printCurlRequest(String url, CookieStore cookieStore, Map<String, String> params, long cost) {
        logger.warn(getCurlRequest(url, cookieStore, params, cost));
    }

    private static String getCurlRequest(String url, CookieStore cookieStore, Map<String, String> params, long cost) {
        if (params == null) {
            return "curl '" + url + "'\ncost : " + cost;
        } else {
            StringBuilder paramsStr = new StringBuilder();
            Iterator<Entry<String, String>> iterator = params.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, String> entry = iterator.next();
                paramsStr.append(entry.getKey() + "=" + entry.getValue());
                if (iterator.hasNext()) {
                    paramsStr.append("&");
                }
            }
            if (cookieStore == null) {
                return "curl '" + url + "' -d '" + paramsStr.toString() + "'\ncost : " + cost;
            } else {
                StringBuilder cookieStr = new StringBuilder();
                List<Cookie> cookies = cookieStore.getCookies();
                Iterator<Cookie> iteratorCookie = cookies.iterator();
                while (iteratorCookie.hasNext()) {
                    Cookie cookie = iteratorCookie.next();
                    cookieStr.append(cookie.getName() + "=" + cookie.getValue());
                    if (iteratorCookie.hasNext()) {
                        cookieStr.append(";");
                    }
                }
                return "curl '" + url + "' -b '" + cookieStr + "' -d '" + paramsStr.toString() + "'\ncost : " + cost;
            }
        }
    }

    public static String doGet(String url, Map<String, String> params, Map<String, String> headers) {
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        HttpGet httpGet = null;
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getHttpClient();

            if (params != null && params.size() > 0) {
                List<NameValuePair> paramList = Lists.newArrayList();
                for (Entry<String, String> pair : params.entrySet()) {
                    paramList.add(new BasicNameValuePair(pair.getKey(), pair.getValue()));
                }
                String param = URLEncodedUtils.format(paramList, "UTF-8");
                url += "?" + param;
            }
            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                .setSocketTimeout(READ_TIMEOUT)
                .build();
            httpGet = new HttpGet(uri);
            if (headers != null && headers.size() > 0) {
                for (Entry<String, String> header : headers.entrySet()) {
                    httpGet.addHeader(header.getKey(), header.getValue());
                }
            }
            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);
            response = httpClient.execute(httpGet, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return EntityUtils.toString(response.getEntity());
            } else {
                String errorMsg = EntityUtils.toString(response.getEntity());
                throw new PolardbxException("requestGet remote error, url=" + uri.toString() + ", code=" + statusCode
                    + ", error msg=" + errorMsg);
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, null, params, cost);
            throw new PolardbxException("requestPost(Https) remote error, request : " + curlRequest, t);
        } finally {
            long end = System.currentTimeMillis();
            long cost = end - start;
            printCurlRequest(url, null, null, cost);
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                }
            }
            if (httpGet != null) {
                httpGet.releaseConnection();
            }
        }
    }

    public static String doPost(String url, String paramStr, Map<String, String> headers) {
        return doPost(url, paramStr, headers, "POST", READ_TIMEOUT, CONNECT_TIMEOUT);
    }

    public static String doPost(String url, String paramStr, Map<String, String> headers, String method,
                                int readTimeout, int connectTimeout) {
        long start = System.currentTimeMillis();
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnPerRoute(50);
        builder.setMaxConnTotal(100);
        HttpRequestBase httpRequest = null;
        CloseableHttpResponse response = null;
        try {
            CloseableHttpClient httpClient = getHttpClient();
            URI uri = new URIBuilder(url).build();
            RequestConfig config = RequestConfig.custom().setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .build();
            if ("POST".equalsIgnoreCase(method)) {
                httpRequest = new HttpPost(uri);
                ((HttpPost) httpRequest).setEntity(new StringEntity(paramStr));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                httpRequest = new HttpDelete(uri);
            } else {
                throw new PolardbxException("method not supported: " + method);
            }

            if (headers != null && headers.size() > 0) {
                for (Entry<String, String> header : headers.entrySet()) {
                    httpRequest.addHeader(header.getKey(), header.getValue());
                }
            }

            HttpClientContext context = HttpClientContext.create();
            context.setRequestConfig(config);

            response = httpClient.execute(httpRequest, context);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                long end = System.currentTimeMillis();
                long cost = end - start;
                printCurlRequest(url, null, null, cost);
                return EntityUtils.toString(response.getEntity());
            } else {
                long end = System.currentTimeMillis();
                long cost = end - start;
                String curlRequest = getCurlRequest(url, null, null, cost);
                throw new PolardbxException(
                    "requestPost(Https) remote error, request : " + curlRequest + ", statusCode=" + statusCode + "");
            }
        } catch (Throwable t) {
            long end = System.currentTimeMillis();
            long cost = end - start;
            String curlRequest = getCurlRequest(url, null, null, cost);
            throw new PolardbxException("requestPost(Https) remote error, request : " + curlRequest, t);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                }
            }
            if (httpRequest != null) {
                httpRequest.releaseConnection();
            }
        }
    }

    public static void download(String urlStr, String targetName) throws Exception {
        WgetCmd cmd = new WgetCmd(urlStr, targetName);
        cmd.execute();
    }

    private static void doDownload(String urlStr, String targetName) throws IOException, URISyntaxException {
        BufferedOutputStream bw = new BufferedOutputStream(new FileOutputStream(targetName));
        download(urlStr, bw);
    }

    public static void download(String urlStr, OutputStream os) throws IOException,
        URISyntaxException {
        urlStr = urlStr.trim();
        URL url = new URL(urlStr);
        CloseableHttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(50).setMaxConnTotal(100).build();
        HttpGet httpGet = new HttpGet(url.toURI());
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(100000)
            .setConnectionRequestTimeout(10000)
            .setSocketTimeout(10000)
            .build();
        httpGet.setConfig(requestConfig);
        HttpResponse response = httpClient.execute(httpGet);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpResponseStatus.OK.code() && statusCode != HttpResponseStatus.PARTIAL_CONTENT.code()) {
            String result = EntityUtils.toString(response.getEntity());
            if (logger != null) {
                logger.error("please curl " + httpGet.getURI());
            }
            throw new RuntimeException("return error !" + response.getStatusLine().getReasonPhrase() + ", " + result);
        }
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("http request rds describeBinlogFiles failed! ");
        }
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(response.getEntity().getContent());
            byte[] cache = new byte[1024 * 10];
            int len;
            while ((len = bis.read(cache)) != -1) {
                os.write(cache, 0, len);//
            }
        } finally {
            if (bis != null) {
                bis.close();
            }
        }
    }

    private static CloseableHttpClient getHttpClient() throws CertificateException, NoSuchAlgorithmException,
        KeyStoreException, IOException, UnrecoverableKeyException,
        KeyManagementException {
        DefaultHttpClient httpclient = new DefaultHttpClient();
        SSLContext ctx = SSLContext.getInstance("TLS");
        X509TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain,
                                           String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain,
                                           String authType) throws CertificateException {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        ctx.init(null, new TrustManager[] {tm}, null);
        SSLSocketFactory ssf = new SSLSocketFactory(ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        ClientConnectionManager ccm = httpclient.getConnectionManager();
        SchemeRegistry sr = ccm.getSchemeRegistry();
        sr.register(new Scheme("https", 443, ssf));
        return httpclient;
    }

    public static interface IDownloadHandle {

        void handle(InputStream inputStream);
    }
}
