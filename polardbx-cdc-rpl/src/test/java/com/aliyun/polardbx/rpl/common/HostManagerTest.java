/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * 验证CN节点列表为空时，抛出的异常能直接指向真实原因，
 * 而不是 Random.nextInt 的 "bound must be positive"
 */
public class HostManagerTest extends BaseTest {

    private static final String EXPECT_ERROR_MSG = "no alive polarx cn node found in metadb table node_info";

    @Test
    public void testGetDstPolarxHost_EmptyNodeList_ThrowsMeaningfulException() {
        try (MockedStatic<DbTaskMetaManager> mocked = mockStatic(DbTaskMetaManager.class)) {
            mocked.when(DbTaskMetaManager::listPolarxCNodeInfo).thenReturn(Collections.emptyList());

            try {
                HostManager.getDstPolarxHost();
                Assert.fail("should throw exception when no alive cn node");
            } catch (PolardbxException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains(EXPECT_ERROR_MSG));
            }
        }
    }

    @Test
    public void testGetDstPolarxHost_NullNodeList_ThrowsMeaningfulException() {
        try (MockedStatic<DbTaskMetaManager> mocked = mockStatic(DbTaskMetaManager.class)) {
            mocked.when(DbTaskMetaManager::listPolarxCNodeInfo).thenReturn(null);

            try {
                HostManager.getDstPolarxHost();
                Assert.fail("should throw exception when no alive cn node");
            } catch (PolardbxException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains(EXPECT_ERROR_MSG));
            }
        }
    }

    @Test
    public void testGetDstPolarxHost_WithAliveNode_ReturnsHostInfo() {
        mockConfig(ConfigKeys.POLARX_USERNAME, "test_user");
        mockConfig(ConfigKeys.POLARX_PASSWORD, "test_password");

        PolarxCNodeInfo node = new PolarxCNodeInfo();
        node.setIp("127.0.0.1");
        node.setPort(3306);
        List<PolarxCNodeInfo> nodes = Collections.singletonList(node);

        try (MockedStatic<DbTaskMetaManager> mocked = mockStatic(DbTaskMetaManager.class)) {
            mocked.when(DbTaskMetaManager::listPolarxCNodeInfo).thenReturn(nodes);

            HostInfo hostInfo = HostManager.getDstPolarxHost();

            Assert.assertEquals("127.0.0.1", hostInfo.getHost());
            Assert.assertEquals(3306, hostInfo.getPort());
            Assert.assertEquals("test_user", hostInfo.getUserName());
            Assert.assertEquals("test_password", hostInfo.getPassword());
            Assert.assertEquals(HostType.POLARX2, hostInfo.getType());
        }
    }
}
