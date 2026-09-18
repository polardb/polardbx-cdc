/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.github.luben.zstd.ZstdInputStream;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class MultiPartInputStreamFactoryTest extends BaseTest {

    @Test
    public void testZstd() throws IOException {
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RDS_BINLOG_AUTO_DECOMPRESS, "true");
        String fileName = "my.001";
        String url = String.format("http://111/%s.zst?ssss", fileName);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        mockUrlConnection(url, connection);
        InputStream is = MultiPartInputStreamFactory.create(url, 100, "dn-1", fileName, null);
        Assert.assertTrue(is instanceof ZstdInputStream);
    }

    @Test
    public void testZstd2() throws IOException {
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RDS_BINLOG_AUTO_DECOMPRESS, "false");
        String fileName = "my.001";
        String url = String.format("http://111/%s.zst?ssss", fileName);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        mockUrlConnection(url, connection);
        InputStream is = MultiPartInputStreamFactory.create(url, 100, "dn-1", fileName, null);
        Assert.assertTrue(is instanceof MultiPartInputStream);
    }
}
