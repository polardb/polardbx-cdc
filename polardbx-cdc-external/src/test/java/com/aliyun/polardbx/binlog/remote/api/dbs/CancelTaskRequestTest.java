/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs;

import com.aliyun.polardbx.binlog.api.dbs.CancelTaskRequest;
import com.aliyun.polardbx.binlog.api.dbs.CancelTaskResult;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.crypto.Mac;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class CancelTaskRequestTest {

    private CancelTaskRequest cancelTaskRequest;

    @Before
    public void setUp() {
        cancelTaskRequest = new CancelTaskRequest();
        cancelTaskRequest.setEndPoint("http://example.com");
        cancelTaskRequest.setInstanceName("testInstance");
        cancelTaskRequest.setRegionCode("cn-hangzhou");
        cancelTaskRequest.setCallerBid("26842");
        cancelTaskRequest.setUserId("1788966360195792");
        cancelTaskRequest.setAccessKey("testAccessKey");
        cancelTaskRequest.setAccessSecretKey("testSecretKey");
        cancelTaskRequest.setTaskId("testTaskId");
    }

    @Test
    public void testDoRequest() {
        String expectedResult = "{\"Data\":{\"status\":\"success\"}}";
        try (MockedStatic<HttpHelper> mockedHttpHelper = mockStatic(HttpHelper.class)) {
            mockedHttpHelper.when(() -> HttpHelper.doGet(anyString(), isNull(), isNull()))
                .thenReturn(expectedResult);

            CancelTaskResult result = cancelTaskRequest.doRequest();

            assertEquals("success", result.getData().getStatus());
        }
    }

    @Test
    public void testCreateHmacSha1() throws NoSuchAlgorithmException, InvalidKeyException {
        String data = "testData";
        String secret = "testSecret";
        byte[] databytes = data.getBytes(StandardCharsets.UTF_8);
        String expectedSignature = new String(new Hex().encode(databytes), StandardCharsets.UTF_8);
        try(MockedStatic<Mac> macMockedStatic = mockStatic(Mac.class)){
            Mac mac = mock(Mac.class);
            Mockito.when(mac.doFinal(any(byte[].class))).thenReturn(databytes);
            Mockito.doNothing().when(mac).init(any());
            macMockedStatic.when(()->Mac.getInstance(anyString())).thenReturn(mac);
            String actualSignature = CancelTaskRequest.createHmacSha1(data, secret);
            Mockito.verify(mac, times(1)).init(any());
            Assert.assertEquals(expectedSignature, actualSignature);
        }

    }

    @Test
    public void testUrlEncode() {
        String value = "testValue=Encoded";
        String expectedEncodedValue = "testValue%3DEncoded";

        String actualEncodedValue = cancelTaskRequest.urlEncode(value);

        assertEquals(expectedEncodedValue, actualEncodedValue);
    }
}