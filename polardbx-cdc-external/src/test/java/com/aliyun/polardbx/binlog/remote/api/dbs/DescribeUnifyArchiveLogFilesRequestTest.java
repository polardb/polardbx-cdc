/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs;

import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesRequest;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.crypto.Mac;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DescribeUnifyArchiveLogFilesRequestTest {

    @InjectMocks
    private DescribeUnifyArchiveLogFilesRequest request;

    @Mock
    private HttpHelper httpHelper;

    @Before
    public void setUp() {
        request.setEndPoint("http://example.com");
        request.setInstanceName("testInstance");
        request.setRegionCode("cn-hangzhou");
        request.setCallerBid("26842");
        request.setUserId("1788966360195792");
        request.setAccessKey("testAccessKey");
        request.setAccessSecretKey("testSecretKey");
        request.setStartTime(1640239912000L); // 2021-12-23T06:11:52Z
        request.setEndTime(1640243512000L);   // 2021-12-23T07:11:52Z
        request.setPageSize(20);
        request.setPageNumber(1);
    }

    @Test
    public void testCreateHmacSha1_Success() throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        String data = "testData";
        String secret = "testSecret";
        String expectedSignature = "expectedSignature";

        try(MockedStatic<Mac> macMockedStatic = Mockito.mockStatic(Mac.class)){
            // Mock the HmacSHA1 algorithm
            Mac mac = Mockito.mock(Mac.class);
            when(mac.doFinal(data.getBytes())).thenReturn(expectedSignature.getBytes(StandardCharsets.UTF_8));

            // Mock the Mac.getInstance and SecretKeySpec
            macMockedStatic.when(() -> Mac.getInstance("HmacSHA1")).thenReturn(mac);

            String result = request.createHmacSha1(data, secret);
            assertEquals(new String(new Hex().encode(expectedSignature.getBytes()), StandardCharsets.UTF_8), result);
        }
    }


    @Test
    public void testUrlEncode_Success() {
        String value = "testValue";
        String expectedEncodedValue = "testValueEncoded";
        try(MockedStatic<URLEncoder> encoderMockedStatic = Mockito.mockStatic(URLEncoder.class)){
            // Mock the URLEncoder.encode
            encoderMockedStatic.when(() -> URLEncoder.encode(value, "UTF-8")).thenReturn(expectedEncodedValue);

            String result = request.urlEncode(value);
            assertEquals(expectedEncodedValue, result);
        }
    }

    @Test(expected = PolardbxException.class)
    public void testUrlEncode_Exception() {
        String value = "testValue";
        try(MockedStatic<URLEncoder> encoderMockedStatic = Mockito.mockStatic(URLEncoder.class)){
            // Mock the URLEncoder.encode to throw UnsupportedEncodingException
            encoderMockedStatic.when(() -> URLEncoder.encode(value, "UTF-8")).thenThrow(new UnsupportedEncodingException());

            request.urlEncode(value);
        }
    }

    @Test
    public void testFormatDate() {
        long timestamp = 1640239912000L; // 2021-12-23T06:11:52Z
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String expectedDate = sdf.format(timestamp);

        String result = request.formatDate(timestamp);
        assertEquals(expectedDate, result);
    }

    @Test
    public void testDoRequest_Success() {
        String expectedResult = "{\"Success\":\"success\"}";
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()-> httpHelper.doGet(anyString(), any(), any())).thenReturn(expectedResult);

            DescribeUnifyArchiveLogFilesResult result = request.doRequest();
            assertEquals("success", result.getSuccess());
        }
    }

    @Test
    public void testDoRequest_SkipAuth() {
        request.setSkipAuth(true);
        String expectedResult = "{\"Success\":\"success\"}";
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()-> httpHelper.doGet(anyString(), any(), any())).thenReturn(expectedResult);

            DescribeUnifyArchiveLogFilesResult result = request.doRequest();
            assertEquals("success", result.getSuccess());
        }
    }
}
