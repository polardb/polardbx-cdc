/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.columnar.version.ColumnarVersions;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.mockStatic;

@RunWith(MockitoJUnitRunner.class)
public class ColumnarSystemResourceTest {

    @Test
    public void testGetVersion() {
        try (final MockedStatic<ColumnarVersions> columnarVersionsMocked = mockStatic(ColumnarVersions.class)) {
            columnarVersionsMocked.when(ColumnarVersions::getVersionsByHttp).thenReturn("{\n"
                + "  \"polardbx_columnar_image_version\": \"5.4.19-20240320_17109018-5322410_20240321_7c453d3c-cloud-normal\",\n"
                + "  \"polardbx_columnar_rpm_version\": \"t-polardbx-columnar-5.4.19-20240320_17109018.noarch.rpm\",\n"
                + "  \"polardbx_columnar_daemon_version\": \"t-polardbx-cdc-5.4.19-20240319_17108478.noarch.rpm\",\n"
                + "  \"polardbx_columnar_fw_branch_name\": \"master\",\n"
                + "  \"polardbx_version\": \"5.4.19\",\n"
                + "  \"polardbx_sql_version\": \"5.4.19-SNAPSHOT\",\n"
                + "  \"polardbx_cdc_client_version\": \"2.0.32\"\n"
                + "}");

            final ColumnarSystemResource columnarSystemResource = new ColumnarSystemResource();
            final String version = columnarSystemResource.getVersion();
            MatcherAssert.assertThat(version, CoreMatchers.containsString("5.4.19-20240320_17109018"));
        }
    }
}