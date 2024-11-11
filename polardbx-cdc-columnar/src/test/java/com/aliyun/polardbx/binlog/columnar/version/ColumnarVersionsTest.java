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
import com.aliyun.polardbx.binlog.util.AddressUtil;
import com.amazonaws.util.StringInputStream;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@NotThreadSafe
@RunWith(MockitoJUnitRunner.class)
public class ColumnarVersionsTest {
    private static final String TEST_IP = "127.0.0.1";
    private static final String TEST_PORT = "3070";
    private static final String TEST_NAME = "testName";
    private static final String VERSIONS_STRING =
        "{\"polardbx_columnar_image_version\":\"5.4.19-20240320_17109018-5322410_20240321_7c453d3c-cloud-normal\",\"polardbx_columnar_rpm_version\":\"t-polardbx-columnar-5.4.19-20240320_17109018.noarch.rpm\",\"polardbx_columnar_daemon_version\":\"t-polardbx-cdc-5.4.19-20240319_17108478.noarch.rpm\",\"polardbx_columnar_fw_branch_name\":\"master\",\"polardbx_version\":\"5.4.19\",\"polardbx_sql_version\":\"5.4.19-SNAPSHOT\",\"polardbx_cdc_client_version\":\"2.0.32\"}";
    private static final ColumnarNodeInfo TEST_LEADER_INFO = new ColumnarNodeInfo(TEST_IP, TEST_PORT, TEST_NAME);
    private static final String COLUMNAR_VERSIONS_URL = ColumnarVersions.getUrlForColumnarVersions(TEST_LEADER_INFO);
    private final AtomicBoolean emptyLeaderInfo = new AtomicBoolean(false);
    private final AtomicInteger responseCode = new AtomicInteger(HttpURLConnection.HTTP_OK);
    @Mock
    private ColumnarMetaManager columnarMetaManager;
    @Mock
    private HttpURLConnection httpURLConnection;

    @Before
    public void setUp() throws IOException {
        emptyLeaderInfo.set(false);
        responseCode.set(HttpURLConnection.HTTP_OK);

        when(columnarMetaManager.getNodeInfo(any())).then(invocationOnMock -> {
            if (emptyLeaderInfo.get()) {
                return ColumnarNodeInfo.EMPTY;
            } else {
                return TEST_LEADER_INFO;
            }
        });

        when(httpURLConnection.getResponseCode()).then(invocationOnMock -> {
            final int code = responseCode.get();
            if (code == HttpURLConnection.HTTP_BAD_GATEWAY) {
                throw new IOException("mock io exception");
            }
            return code;
        });
    }

    @Test
    public void testGetVersionsByHttp() throws Exception {
        final String hostAddress = AddressUtil.getHostAddress().getHostAddress();
        try (final MockedStatic<DynamicApplicationConfig> dynamicApplicationConfigMocked = mockStatic(
            DynamicApplicationConfig.class);
            final MockedStatic<ColumnarMetaManager> columnarMetaManagerMocked = mockStatic(ColumnarMetaManager.class);
            final MockedConstruction urlMocked = mockConstruction(URL.class,
                (mock, context) -> when(mock.openConnection()).thenReturn(httpURLConnection));
        ) {
            dynamicApplicationConfigMocked.when(() -> DynamicApplicationConfig.getString(matches(INST_IP)))
                .thenReturn(hostAddress);
            columnarMetaManagerMocked.when(ColumnarMetaManager::getInstant).thenReturn(columnarMetaManager);

            when(httpURLConnection.getInputStream()).thenReturn(new StringInputStream(VERSIONS_STRING));
            MatcherAssert.assertThat(ColumnarVersions.getVersionsByHttp(), CoreMatchers.is(VERSIONS_STRING));

            // Test http request failed
            responseCode.set(HttpURLConnection.HTTP_INTERNAL_ERROR);
            MatcherAssert.assertThat(ColumnarVersions.getVersionsByHttp(), CoreMatchers.is(""));

            // Test http request failed by IOException
            responseCode.set(HttpURLConnection.HTTP_BAD_GATEWAY);
            MatcherAssert.assertThat(ColumnarVersions.getVersionsByHttp(), CoreMatchers.is(""));

            // Test get leader info failed
            emptyLeaderInfo.set(true);
            MatcherAssert.assertThat(ColumnarVersions.getVersionsByHttp(), CoreMatchers.is(""));
        }
    }

}