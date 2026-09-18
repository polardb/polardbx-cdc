/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.BucketVersioningConfiguration;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.DeleteVersionsRequest;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ListVersionsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.OSSVersionSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.VersionListing;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OssManagerTest extends BaseTest {

    private OssManager ossManager;
    private ObjectMetadata mockObjectMetadata;

    @Test
    public void testGetBucketVersioningEnabled() {
        OssConfig ossConfig = mock(OssConfig.class);
        OSSClient ossClient = mock(OSSClient.class);

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

    @Test
    public void testListVersions() {
        OssConfig ossConfig = mock(OssConfig.class);
        OSSClient ossClient = mock(OSSClient.class);

        OssManager ossManager = buildOssManager(ossConfig, ossClient);

        List<OSSVersionSummary> mockReturnList = new ArrayList<>();
        mockReturnList.add(new OSSVersionSummary());
        mockReturnList.add(new OSSVersionSummary());
        VersionListing versionListing = new VersionListing();
        versionListing.setVersionSummaries(mockReturnList);

        BucketVersioningConfiguration versioningConfiguration = new BucketVersioningConfiguration();
        versioningConfiguration.setStatus(BucketVersioningConfiguration.ENABLED);

        mockConfig(ConfigKeys.CLUSTER_ROLE, "master");
        when(ossConfig.getBucketName()).thenReturn("bucket1");
        when(ossClient.getBucketVersioning("bucket1")).thenReturn(versioningConfiguration);
        when(ossClient.listVersions(any())).thenReturn(versionListing);
        List<OSSVersionSummary> result = ossManager.listVersions("xxx");
        assertEquals(mockReturnList.get(0), result.get(0));
        assertEquals(mockReturnList.get(1), result.get(1));
    }

    private OssManager buildOssManager(OssConfig ossConfig, OSSClient ossClient) {
        return new OssManager(ossConfig, false) {
            @Override
            public OSSClient getOssClient() {
                return ossClient;
            }
        };
    }

    private void buildAnotherOssManager() {
        OssConfig ossConfig = new OssConfig();
        ossConfig.bucketName = "a";
        ossConfig.polardbxInstance = "inst";
        OSSClient mockOssClient = mock(OSSClient.class);
        // 初始化测试对象
        ossManager = buildOssManager(ossConfig, mockOssClient);

        // mockOssClient = mock(OSS.class);
        mockObjectMetadata = mock(ObjectMetadata.class);

        // 设置 Mock 行为
        when(mockOssClient.getObjectMetadata(anyString(), anyString())).thenReturn(mockObjectMetadata);
        when(mockObjectMetadata.getETag()).thenReturn("expected-etag");
        when(mockObjectMetadata.getContentLength()).thenReturn(1024L);
    }

    /**
     * 测试 getMd5 方法正常情况下的返回值。
     */
    @Test
    public void testGetMd5NormalCase() {
        buildAnotherOssManager();
        // 准备: 设置文件名
        String fileName = "test.txt";

        // 执行: 调用 getMd5 方法
        String md5 = ossManager.getMd5(fileName);

        // 验证: 确保返回值正确
        assertEquals("expected-etag", md5);
    }

    /**
     * 测试 getSize 方法正常情况下的返回值。
     */
    @Test
    public void testGetSizeNormalCase() {
        buildAnotherOssManager();
        // 准备: 设置文件名
        String fileName = "test.txt";

        // 执行: 调用 getSize 方法
        long size = ossManager.getSize(fileName);

        // 验证: 确保返回值正确
        assertEquals(1024L, size);
    }

    @Test
    public void testDeleteAll() {
        OssConfig ossConfig = mock(OssConfig.class);
        OSS ossClient = mock(OSS.class);

        OssManager ossManager = new OssManager(ossConfig, false) {
            @Override
            public OSS getOssClient() {
                return ossClient;
            }

            @Override
            boolean getBucketVersioningEnabled() {
                return true;
            }
        };

        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(ossConfig.getPolardbxInstance()).thenReturn("test-instance");

        // Mock deleteObjects behavior
        ListObjectsV2Result listObjectsResult = mock(ListObjectsV2Result.class);
        when(listObjectsResult.getObjectSummaries()).thenReturn(Collections.emptyList());
        when(listObjectsResult.isTruncated()).thenReturn(false);
        when(ossClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsResult);

        // Mock deleteVersions behavior
        VersionListing versionListing = mock(VersionListing.class);
        when(versionListing.getVersionSummaries()).thenReturn(Collections.emptyList());
        when(versionListing.isTruncated()).thenReturn(false);
        when(ossClient.listVersions(any(ListVersionsRequest.class))).thenReturn(versionListing);

        // Execute the method
        ossManager.deleteAll("test-prefix");

        // Verify interactions
        verify(ossClient, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(ossClient, times(1)).listVersions(any(ListVersionsRequest.class));
        verify(ossClient, times(2)).shutdown();
    }

    /**
     * 测试 deleteAll 方法删除对象的情况
     */
    @Test
    public void testDeleteAllWithObjects() {
        OssConfig ossConfig = mock(OssConfig.class);
        OSS ossClient = mock(OSS.class);

        OssManager ossManager = new OssManager(ossConfig, false) {
            @Override
            public OSS getOssClient() {
                return ossClient;
            }

            @Override
            boolean getBucketVersioningEnabled() {
                return true;
            }
        };

        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(ossConfig.getPolardbxInstance()).thenReturn("test-instance");

        // Mock deleteObjects behavior with objects to delete
        OSSObjectSummary objectSummary1 = mock(OSSObjectSummary.class);
        when(objectSummary1.getKey()).thenReturn("key1");
        OSSObjectSummary objectSummary2 = mock(OSSObjectSummary.class);
        when(objectSummary2.getKey()).thenReturn("key2");

        ListObjectsV2Result listObjectsResult = mock(ListObjectsV2Result.class);
        when(listObjectsResult.getObjectSummaries()).thenReturn(Arrays.asList(objectSummary1, objectSummary2));
        when(listObjectsResult.isTruncated()).thenReturn(false);
        when(ossClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsResult);

        // Mock deleteVersions behavior
        VersionListing versionListing = mock(VersionListing.class);
        when(versionListing.getVersionSummaries()).thenReturn(Collections.emptyList());
        when(versionListing.isTruncated()).thenReturn(false);
        when(ossClient.listVersions(any(ListVersionsRequest.class))).thenReturn(versionListing);

        // Execute the method
        ossManager.deleteAll("test-prefix");

        // Verify interactions
        verify(ossClient, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(ossClient, times(1)).deleteObjects(any(DeleteObjectsRequest.class));
        verify(ossClient, times(1)).listVersions(any(ListVersionsRequest.class));
        verify(ossClient, times(2)).shutdown();
    }

    /**
     * 测试 deleteAll 方法删除版本的情况
     */
    @Test
    public void testDeleteAllWithVersions() {
        OssConfig ossConfig = mock(OssConfig.class);
        OSS ossClient = mock(OSS.class);

        OssManager ossManager = new OssManager(ossConfig, false) {
            @Override
            public OSS getOssClient() {
                return ossClient;
            }

            @Override
            boolean getBucketVersioningEnabled() {
                return true;
            }
        };

        when(ossConfig.getBucketName()).thenReturn("test-bucket");
        when(ossConfig.getPolardbxInstance()).thenReturn("test-instance");

        // Mock deleteObjects behavior
        ListObjectsV2Result listObjectsResult = mock(ListObjectsV2Result.class);
        when(listObjectsResult.getObjectSummaries()).thenReturn(Collections.emptyList());
        when(listObjectsResult.isTruncated()).thenReturn(false);
        when(ossClient.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listObjectsResult);

        // Mock deleteVersions behavior with versions to delete
        OSSVersionSummary versionSummary1 = mock(OSSVersionSummary.class);
        when(versionSummary1.getKey()).thenReturn("key1");
        when(versionSummary1.getVersionId()).thenReturn("version1");
        OSSVersionSummary versionSummary2 = mock(OSSVersionSummary.class);
        when(versionSummary2.getKey()).thenReturn("key2");
        when(versionSummary2.getVersionId()).thenReturn("version2");

        VersionListing versionListing = mock(VersionListing.class);
        when(versionListing.getVersionSummaries()).thenReturn(Arrays.asList(versionSummary1, versionSummary2));
        when(versionListing.isTruncated()).thenReturn(false);
        when(ossClient.listVersions(any(ListVersionsRequest.class))).thenReturn(versionListing);

        // Execute the method
        ossManager.deleteAll("test-prefix");

        // Verify interactions
        verify(ossClient, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(ossClient, times(1)).listVersions(any(ListVersionsRequest.class));
        verify(ossClient, times(1)).deleteVersions(any(DeleteVersionsRequest.class));
        verify(ossClient, times(3)).shutdown();
    }
}
