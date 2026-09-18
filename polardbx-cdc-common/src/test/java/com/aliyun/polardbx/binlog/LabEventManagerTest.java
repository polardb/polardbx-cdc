/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import org.junit.Assert;
import org.junit.Test;

public class LabEventManagerTest {

    @Test
    public void testExternalColumnConfigKeysAreRegistered() {
        Assert.assertTrue(ServerVariables.variables.contains(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MEMORY_LIMIT_BYTES));
        Assert.assertTrue(ServerVariables.variables.contains(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_TXN_BYTES));
        Assert.assertTrue(ServerVariables.variables.contains(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_MAX_ENTRIES));
        Assert.assertTrue(ServerVariables.variables.contains(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_PARTIAL_UPDATE_ROW_IMAGE_ENABLED));
        Assert.assertTrue(ServerVariables.variables.contains(
            ConfigKeys.TASK_REFORMAT_EXTERNAL_COLUMN_BLOB_REF_ERROR_FALLBACK_ENABLED));
    }
}
