/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs.gareth;

import com.aliyun.polardbx.binlog.api.dbs.gareth.DbsDownloadCmd;
import com.aliyun.polardbx.binlog.api.dbs.gareth.DefaultDownloadAction;
import com.aliyun.polardbx.binlog.api.dbs.gareth.GarethActionFactory;
import com.aliyun.polardbx.binlog.api.dbs.gareth.LindormDownloadAction;
import com.aliyun.polardbx.binlog.api.dbs.gareth.NasDownloadAction;
import com.aliyun.polardbx.binlog.api.dbs.gareth.OssDownloadAction;
import com.aliyun.polardbx.binlog.api.dbs.gareth.S3DownloadAction;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class GarethActionFactoryTest {

    @Test
    public void test() throws IOException {
        DbsDownloadCmd cmd = GarethActionFactory.create("oss", "");
        Assert.assertEquals(OssDownloadAction.class, cmd.getClass());
        cmd = GarethActionFactory.create("NAS", "");
        Assert.assertEquals(NasDownloadAction.class, cmd.getClass());
        cmd = GarethActionFactory.create("s3", "");
        Assert.assertEquals(S3DownloadAction.class, cmd.getClass());
        cmd = GarethActionFactory.create("lindorm", "");
        Assert.assertEquals(LindormDownloadAction.class, cmd.getClass());
        cmd = GarethActionFactory.create("test", "");
        Assert.assertEquals(DefaultDownloadAction.class, cmd.getClass());
    }
}
