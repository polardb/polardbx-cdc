/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs;

import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreRequest;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.crypto.Mac;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

public class RdsDownloadForRestoreRequestTest {

    private RdsDownloadForRestoreRequest rdsDownloadForRestoreRequest;

    @Before
    public void setUp() {
        rdsDownloadForRestoreRequest = new RdsDownloadForRestoreRequest();
        rdsDownloadForRestoreRequest.setEndPoint("http://example.com");
        rdsDownloadForRestoreRequest.setInstanceName("testInstance");
        rdsDownloadForRestoreRequest.setRegionCode("cn-hangzhou");
        rdsDownloadForRestoreRequest.setCallerBid("26842");
        rdsDownloadForRestoreRequest.setUserId("1788966360195792");
        rdsDownloadForRestoreRequest.setAccessKey("testAccessKey");
        rdsDownloadForRestoreRequest.setAccessSecretKey("testSecretKey");
        rdsDownloadForRestoreRequest.setArchiveLogId("12345,2345,3456");
        rdsDownloadForRestoreRequest.setArchiveLogLocalFolder("/home/mysql/data/mysql");
        rdsDownloadForRestoreRequest.setHostInsId(123);
        rdsDownloadForRestoreRequest.setDecompression(true);
        rdsDownloadForRestoreRequest.setEncrypted(true);
    }

    @Test
    public void testDoRequest() {
        String expectedResult = "{\"Data\":{\"status\":\"success\"}}";
        try (MockedStatic<HttpHelper> mockedHttpHelper = mockStatic(HttpHelper.class)) {
            mockedHttpHelper.when(() -> HttpHelper.doGet(anyString(), isNull(), isNull()))
                .thenReturn(expectedResult);

            RdsDownloadForRestoreResult result = rdsDownloadForRestoreRequest.doRequest();

            assertEquals("success", result.getData().getStatus());
        }
    }

    @Test
    public void testCreateHmacSha1() throws InvalidKeyException, UnsupportedEncodingException {
        String data = "testData";
        String secret = "testSecret";
        byte []digest = data.getBytes("UTF-8");
        try(MockedStatic<Mac> mockedMac = mockStatic(Mac.class)){
            Mac mac = Mockito.mock(Mac.class);
            Mockito.doNothing().when(mac).init(any());
            Mockito.when(mac.doFinal(any())).thenReturn(digest);
            mockedMac.when(() -> Mac.getInstance("HmacSHA1")).thenReturn(mac);
            String actualSignature = RdsDownloadForRestoreRequest.createHmacSha1(data, secret);
            String expectedSignature = new String(new Hex().encode(digest), "UTF-8");
            assertEquals(expectedSignature, actualSignature);
        }

    }

    @Test
    public void testUrlEncode() {
        String value = "testValue=Encoded";
        String expectedEncodedValue = "testValue%3DEncoded";

        String actualEncodedValue = rdsDownloadForRestoreRequest.urlEncode(value);

        assertEquals(expectedEncodedValue, actualEncodedValue);
    }
}