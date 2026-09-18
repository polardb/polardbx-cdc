/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.repository;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.IS_REPLICA;
import static com.aliyun.polardbx.binlog.ConfigKeys.MEM_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_MEMORY_THRESHOLD_MB;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PERSIST_SCHEMA_META_ENABLED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * created by ziyang.lb
 **/
public class CdcSchemaStoreProviderTest extends BaseTest {

    /**
     * 测试 IS_REPLICA 为 true 且 RPL_PERSIST_SCHEMA_META_ENABLED 为 true 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_ReplicaMode_Enabled() {
        System.setProperty(IS_REPLICA, "true");
        mockConfig(RPL_PERSIST_SCHEMA_META_ENABLED, "true");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertTrue("Replica mode with persist enabled should return true", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 true 且 RPL_PERSIST_SCHEMA_META_ENABLED 为 false 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_ReplicaMode_Disabled() {
        System.setProperty(IS_REPLICA, "true");
        mockConfig(RPL_PERSIST_SCHEMA_META_ENABLED, "false");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertFalse("Replica mode with persist disabled should return false", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false 且 META_PERSIST_ENABLED 为 true 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_MetaPersistEnabled() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "true");
        mockConfig(MEM_SIZE, "1024");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertTrue("Non-replica mode with META_PERSIST_ENABLED true should return true", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false，META_PERSIST_ENABLED 为 false，但 memSize <= memThreshold 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_MemoryThresholdMet() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "false");
        mockConfig(MEM_SIZE, "1024");
        mockConfig(META_PERSIST_MEMORY_THRESHOLD_MB, "2048");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertTrue("Non-replica mode with memSize <= memThreshold should return true", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false，META_PERSIST_ENABLED 为 false，且 memSize > memThreshold 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_MemoryThresholdNotMet() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "false");
        mockConfig(MEM_SIZE, "4096");
        mockConfig(META_PERSIST_MEMORY_THRESHOLD_MB, "2048");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertFalse("Non-replica mode with memSize > memThreshold should return false", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false，META_PERSIST_ENABLED 为 false，且 memThreshold 为 0 的情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_ZeroThreshold() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "false");
        mockConfig(MEM_SIZE, "1024");
        mockConfig(META_PERSIST_MEMORY_THRESHOLD_MB, "0");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertFalse("Non-replica mode with memThreshold = 0 should return false", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false，META_PERSIST_ENABLED 为 false，且 memThreshold 为负数的情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_NegativeThreshold() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "false");
        mockConfig(MEM_SIZE, "1024");
        mockConfig(META_PERSIST_MEMORY_THRESHOLD_MB, "-1");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertFalse("Non-replica mode with negative memThreshold should return false", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 false，META_PERSIST_ENABLED 为 true 时的边界情况
     */
    @Test
    public void testGetMetaPersistEnabled_NonReplicaMode_MetaPersistEnabled_WithMemoryCheck() {
        System.setProperty(IS_REPLICA, "false");
        mockConfig(META_PERSIST_ENABLED, "true");
        mockConfig(MEM_SIZE, "8192");
        mockConfig(META_PERSIST_MEMORY_THRESHOLD_MB, "1024");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertTrue("Non-replica mode with META_PERSIST_ENABLED true should always return true", result);

        System.clearProperty(IS_REPLICA);
    }

    /**
     * 测试 IS_REPLICA 为 null 时的默认行为（按 false 处理）
     */
    @Test
    public void testGetMetaPersistEnabled_NullReplicaProperty() {
        System.clearProperty(IS_REPLICA);
        mockConfig(META_PERSIST_ENABLED, "true");
        mockConfig(MEM_SIZE, "1024");

        CdcSchemaStoreProvider provider = CdcSchemaStoreProvider.getInstance();
        boolean result = provider.getMetaPersistEnabled();

        assertTrue("Null IS_REPLICA should be treated as false with META_PERSIST_ENABLED true", result);
    }
}