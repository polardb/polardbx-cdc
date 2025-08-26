/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.api.dbs.gareth.DefaultDownloadAction;
import com.aliyun.polardbx.binlog.api.dbs.gareth.S3DownloadAction;
import com.aliyun.polardbx.binlog.util.Shell;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

public class DefaultDownloadActionTest {
    @Test
    public void defaultDownload() throws IOException {
        try(MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class)){
            shellMockedStatic.when(()->Shell.execCommand(Mockito.any())).thenReturn("success");
            JSONObject object = new JSONObject();
            object.put("ak", "test-fdsasdfda-ak");
            object.put("sk", "test-fdsasdfda-sk");
            object.put("bucket", "apsaradb");
            object.put("endpoint", "127.0.0.1");
            object.put("port", "9000");
            DefaultDownloadAction action = new DefaultDownloadAction("default",object.toJSONString());
            action.download("/Users/yanfenglin/Downloads/gareth/New_1.txt", "apsaradb/New_1.txt", "/Users/yanfenglin/Downloads/gareth/log");
            String[] cmd = "sudo gareth --engine default --config \"{\\\"job_type\\\":\\\"transfer\\\",\\\"src\\\":{\\\"bucket\\\":\\\"apsaradb\\\",\\\"endpoint\\\":\\\"127.0.0.1\\\",\\\"port\\\":\\\"9000\\\",\\\"sk\\\":\\\"test-fdsasdfda-sk\\\",\\\"ak\\\":\\\"test-fdsasdfda-ak\\\",\\\"type\\\":\\\"default\\\"},\\\"job\\\":{\\\"decompress\\\":true,\\\"src_path\\\":\\\"apsaradb/New_1.txt\\\",\\\"dest_path\\\":\\\"/Users/yanfenglin/Downloads/gareth/New_1.txt\\\"},\\\"dest\\\":{\\\"type\\\":\\\"file\\\"}}\" --logfile /Users/yanfenglin/Downloads/gareth/log/gareth.log --error2stdout".split(" ");
            shellMockedStatic.verify(()->Shell.execCommand(cmd), Mockito.times(1));
        }
    }
}
