/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs.gareth;

import com.aliyun.polardbx.binlog.api.dbs.gareth.NasDownloadAction;
import com.aliyun.polardbx.binlog.util.Shell;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

public class NasDownloadActionTest {

    @Test
    public void testDownload() throws IOException {
        try(MockedStatic<Shell> shellMock = Mockito.mockStatic(Shell.class)){
            shellMock.when(()->Shell.execCommand(Mockito.any(String[].class))).thenReturn("success");
            String garethConfig = "{\"uid\":\"223274842729957349\",\"protocol\":\"nfs\",\"protocolVersion\":\"v4\",\"type\":\"nas\",\"region\":\"cn-beijing\",\"originalIp\":\"10.0.109.210\",\"originalPort\":\"2049\",\"mountpoint\":\"/apsaradb/test\"}";
            NasDownloadAction nasDownloadAction = new NasDownloadAction(garethConfig);
            nasDownloadAction.download("test.txt", "/tmp/test.txt", "/tmp/log");
            String [] cmd="sudo gareth --engine default --config \"{\\\"job_type\\\":\\\"transfer\\\",\\\"src\\\":{\\\"uid\\\":\\\"223274842729957349\\\",\\\"protocol\\\":\\\"nfs\\\",\\\"port\\\":\\\"2049\\\",\\\"encrypt\\\":false,\\\"ip\\\":\\\"10.0.109.210\\\",\\\"protocolVersion\\\":\\\"v4\\\",\\\"type\\\":\\\"nas\\\",\\\"region\\\":\\\"cn-beijing\\\",\\\"originalIp\\\":\\\"10.0.109.210\\\",\\\"originalPort\\\":\\\"2049\\\",\\\"mountpoint\\\":\\\"/apsaradb/test\\\"},\\\"job\\\":{\\\"decompress\\\":true,\\\"src_path\\\":\\\"/tmp/test.txt\\\",\\\"dest_path\\\":\\\"test.txt\\\"},\\\"dest\\\":{\\\"type\\\":\\\"file\\\"}}\" --logfile /tmp/log/gareth.log --error2stdout".split(" ");
            shellMock.verify(()->Shell.execCommand(cmd), Mockito.times(1));
        }
    }
}
