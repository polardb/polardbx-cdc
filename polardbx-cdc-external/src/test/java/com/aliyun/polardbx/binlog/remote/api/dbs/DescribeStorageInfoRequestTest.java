/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs;

import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoRequest;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

public class DescribeStorageInfoRequestTest {
    private DescribeStorageInfoRequest request;

    @Before
    public void setUp() {
        request = new DescribeStorageInfoRequest();
        request.setEndPoint("https://example.com");
        request.setCallerBid("testBid");
        request.setUid("testUid");
        request.setStorageEntityId("testEntityId");
        request.setRegion("testRegion");
        request.setAccessKey("testAccessKey");
        request.setAccessSecretKey("testSecretKey");
    }

    @Test
    public void testCreateHmacSha1() throws UnsupportedEncodingException, InvalidKeyException {
        String data = "testData";
        String secret = "testSecret";
        byte []digest = data.getBytes("UTF-8");
        try(MockedStatic<Mac> mockedMac = mockStatic(Mac.class)){
            Mac mac = Mockito.mock(Mac.class);
            Mockito.doNothing().when(mac).init(any());
            Mockito.when(mac.doFinal(any())).thenReturn(digest);
            mockedMac.when(() -> Mac.getInstance("HmacSHA1")).thenReturn(mac);
            String actualSignature = DescribeStorageInfoRequest.createHmacSha1(data, secret);
            String expectedSignature = new String(new Hex().encode(digest), "UTF-8");
            assertEquals(expectedSignature, actualSignature);
        }
    }

    @Test
    public void testUrlEncode() {
        String value = "testValue=Encoded";
        String expectedEncodedValue = "testValue%3DEncoded";

        String actualEncodedValue = request.urlEncode(value);

        assertEquals(expectedEncodedValue, actualEncodedValue);
    }

    @Test
    public void testDoRequest() {
        String expectedResult = "{\"success\":\"true\"}";
        try (MockedStatic<HttpHelper> mockedHttpHelper = mockStatic(HttpHelper.class)) {
            mockedHttpHelper.when(() -> HttpHelper.doGet(anyString(), isNull(), isNull()))
                .thenReturn(expectedResult);

            DescribeStorageInfoResult result = request.doRequest();

            assertTrue(result.getSuccess());
        }
    }
}
