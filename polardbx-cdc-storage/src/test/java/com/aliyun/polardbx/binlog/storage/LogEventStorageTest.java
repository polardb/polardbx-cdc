/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Test;

public class LogEventStorageTest extends BaseTestWithGmsTables {

    @Test
    public void tryRestart() {
        LogEventStorage storage =
            new LogEventStorage(new Repository(true, "test", PersistMode.AUTO, 0.5, 100, 100, DeleteMode.SINGLE, 1));
        storage.start();
        storage.stop();
        storage.start();
        storage.stop();
    }
}
