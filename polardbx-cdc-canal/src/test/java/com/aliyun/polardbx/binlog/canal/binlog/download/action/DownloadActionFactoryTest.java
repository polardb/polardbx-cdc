/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

public class DownloadActionFactoryTest extends BaseTest {

    @Test
    public void testCreate(){
        // true true gareth
        // true false dbs
        // false false http
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "true");
        IDownloadAction downloadAction = DownloadActionFactory.create();
        Assert.assertEquals(GarethAction.class, downloadAction.getClass());
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "false");
        downloadAction = DownloadActionFactory.create();
        Assert.assertEquals(DbsTaskAction.class, downloadAction.getClass());

        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "false");
        downloadAction = DownloadActionFactory.create();
        Assert.assertEquals(HttpAction.class, downloadAction.getClass());
    }
}
