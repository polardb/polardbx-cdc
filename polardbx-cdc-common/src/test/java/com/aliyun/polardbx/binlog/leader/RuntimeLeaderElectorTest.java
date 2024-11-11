/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.leader;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author yudong
 * @since 2024/7/24 13:58
 **/
@RunWith(MockitoJUnitRunner.class)
public class RuntimeLeaderElectorTest extends BaseTestWithGmsTables {

    private final String globalBinlogClusterSnapshot =
        "{\"containers\":[\"c841309e-11bd-4b71-8e8b-a3cc4b9dff4c\",\"fe037f7e-6bf9-4ad8-a3c5-8714ed0f4db5\"],\"dumperMaster\":\"Dumper-2\",\"dumperMasterNode\":\"fe037f7e-6bf9-4ad8-a3c5-8714ed0f4db5\",\"new\":false,\"serverId\":1235254397,\"storageHistoryTso\":\"000000000000000000000000000000000000000000000000000000\",\"storages\":[\"xrelease-240724133642-f560-nh7n-dn-0\",\"xrelease-240724133642-f560-nh7n-dn-1\"],\"timestamp\":1721804211009,\"version\":2}";

    private final String binlogxClusterSnapshot =
        "{\"containers\":[\"9843b50a-b7f8-4ff5-ac0d-6bea5a9ff046\",\"081671a4-6707-40eb-b751-d3b7e10a6b21\"],\"dumperMaster\":\"\",\"dumperMasterNode\":\"\",\"new\":false,\"serverId\":1128,\"storageHistoryTso\":\"000000000000000000000000000000000000000000000000000000\",\"storages\":[\"xrelease-240724032030-b110-gxkc-dn-0\",\"xrelease-240724032030-b110-gxkc-dn-1\"],\"timestamp\":1721793504008,\"version\":2}";

    @Test
    public void testIsDumperMaster_config_empty() throws Exception {
        truncateGmsTables();
        DynamicApplicationConfig.invalidateCache();
        assertFalse(RuntimeLeaderElector.isDumperMaster(1, "Dumper-1"));
    }

    @Test
    public void testIsDumperMaster_version_not_equal() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, globalBinlogClusterSnapshot);
        assertFalse(RuntimeLeaderElector.isDumperMaster(1, "Dumper-2"));
    }

    @Test
    public void testIsDumperMaster_task_name_not_equal() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, globalBinlogClusterSnapshot);
        assertFalse(RuntimeLeaderElector.isDumperMaster(2, "Dumper-1"));
    }

    @Test
    public void testIsDumperMaster_all_ok() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, globalBinlogClusterSnapshot);
        assertTrue(RuntimeLeaderElector.isDumperMaster(2, "Dumper-2"));
    }

    @Test
    public void testIsDumperX_config_empty() throws Exception {
        truncateGmsTables();
        DynamicApplicationConfig.invalidateCache();
        assertFalse(RuntimeLeaderElector.isDumperX(1, TaskType.DumperX));
    }

    @Test
    public void testIsDumperX_version_not_equal() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, binlogxClusterSnapshot);
        assertFalse(RuntimeLeaderElector.isDumperX(1, TaskType.DumperX));
    }

    @Test
    public void testIsDumperX_task_type_not_equal() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, binlogxClusterSnapshot);
        assertFalse(RuntimeLeaderElector.isDumperX(2, TaskType.Dumper));
    }

    @Test
    public void testIsDumperX_all_ok() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, binlogxClusterSnapshot);
        assertTrue(RuntimeLeaderElector.isDumperX(2, TaskType.DumperX));
    }

    @Test
    public void testIsDumperMasterOrX_dumper_master() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, globalBinlogClusterSnapshot);
        assertTrue(RuntimeLeaderElector.isDumperMasterOrX(2, TaskType.Dumper, "Dumper-2"));
    }

    @Test
    public void testIsDumperMasterOrX_dumperx() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, binlogxClusterSnapshot);
        assertTrue(RuntimeLeaderElector.isDumperMasterOrX(2, TaskType.DumperX, "Dumper-2"));
    }

    @Test
    public void testIsDumperMasterOrX_slave() {
        setSnapshotVersion(CLUSTER_SNAPSHOT_VERSION_KEY, globalBinlogClusterSnapshot);
        assertFalse(RuntimeLeaderElector.isDumperMasterOrX(2, TaskType.Dumper, "Dumper-1"));
    }

    private void setSnapshotVersion(String key, String val) {
        DynamicApplicationConfig.setValue(key, val);
    }
}
