/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.BucketVersioningConfiguration;
import com.aliyun.oss.model.OSSVersionSummary;
import com.aliyun.oss.model.VersionListing;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class OssManagerTest {

    @Test
    public void testGetBucketVersioningEnabled() {
        OssConfig ossConfig = Mockito.mock(OssConfig.class);
        OSSClient ossClient = Mockito.mock(OSSClient.class);

        try (MockedStatic<DynamicApplicationConfig> mocked = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            OssManager ossManager = buildOssManager(ossConfig, ossClient);

            BucketVersioningConfiguration versioningConfiguration = new BucketVersioningConfiguration();
            versioningConfiguration.setStatus(BucketVersioningConfiguration.ENABLED);

            when(ossConfig.getBucketName()).thenReturn("bucket1");
            when(ossClient.getBucketVersioning("bucket1")).thenReturn(versioningConfiguration);
            boolean result = ossManager.getBucketVersioningEnabled();
            Assert.assertTrue(result);

            when(ossConfig.getBucketName()).thenReturn("bucket2");
            when(ossClient.getBucketVersioning("bucket2")).thenReturn(null);
            result = ossManager.getBucketVersioningEnabled();
            Assert.assertFalse(result);

            versioningConfiguration.setStatus(BucketVersioningConfiguration.OFF);
            when(ossConfig.getBucketName()).thenReturn("bucket3");
            when(ossClient.getBucketVersioning("bucket3")).thenReturn(versioningConfiguration);
            result = ossManager.getBucketVersioningEnabled();
            Assert.assertFalse(result);

            OSSException ossException =
                new OSSException("OperationNotSupported", "OperationNotSupported", null, null, null, null, null);
            when(ossConfig.getBucketName()).thenReturn("bucket4");
            when(ossClient.getBucketVersioning("bucket4")).thenThrow(ossException);
            result = ossManager.getBucketVersioningEnabled();
            Assert.assertFalse(result);

            when(ossConfig.getBucketName()).thenReturn("bucket5");
            when(ossClient.getBucketVersioning("bucket5")).thenThrow(new RuntimeException());
            try {
                ossManager.getBucketVersioningEnabled();
                Assert.fail();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Test
    public void testListVersions() {
        OssConfig ossConfig = Mockito.mock(OssConfig.class);
        OSSClient ossClient = Mockito.mock(OSSClient.class);

        try (MockedStatic<DynamicApplicationConfig> mocked = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            OssManager ossManager = buildOssManager(ossConfig, ossClient);

            List<OSSVersionSummary> mockReturnList = new ArrayList<>();
            mockReturnList.add(new OSSVersionSummary());
            mockReturnList.add(new OSSVersionSummary());
            VersionListing versionListing = new VersionListing();
            versionListing.setVersionSummaries(mockReturnList);

            BucketVersioningConfiguration versioningConfiguration = new BucketVersioningConfiguration();
            versioningConfiguration.setStatus(BucketVersioningConfiguration.ENABLED);

            mocked.when(DynamicApplicationConfig::getClusterRole).thenReturn("master");
            when(ossConfig.getBucketName()).thenReturn("bucket1");
            when(ossClient.getBucketVersioning("bucket1")).thenReturn(versioningConfiguration);
            when(ossClient.listVersions(any())).thenReturn(versionListing);
            List<OSSVersionSummary> result = ossManager.listVersions("xxx");
            Assert.assertEquals(mockReturnList.get(0), result.get(0));
            Assert.assertEquals(mockReturnList.get(1), result.get(1));
        }
    }

    private OssManager buildOssManager(OssConfig ossConfig, OSSClient ossClient) {
        return new OssManager(ossConfig, false) {
            @Override
            public OSSClient getOssClient() {
                return ossClient;
            }
        };
    }
}
