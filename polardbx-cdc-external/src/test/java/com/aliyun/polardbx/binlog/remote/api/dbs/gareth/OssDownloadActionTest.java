/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs.gareth;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.api.dbs.gareth.OssDownloadAction;
import com.aliyun.polardbx.binlog.util.Shell;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

public class OssDownloadActionTest {

    @Test
    public void testOssDownload() throws IOException {
        try(MockedStatic<Shell> shellMock = Mockito.mockStatic(Shell.class)){
            shellMock.when(()->Shell.execCommand(Mockito.any(String[].class))).thenReturn("success");
            JSONObject object = new JSONObject();
            object.put("ossEndpoint", "cn-hangzhou.aliyun.com");
            object.put("accessKeyId", "akakakakak");
            object.put("accessKeySecret", "sksksksksksk");
            object.put("ossBucket", "apsaradb");
            OssDownloadAction ossDownloadAction = new OssDownloadAction(object.toJSONString());
            ossDownloadAction.download("localFile", "removeFilePath", "logPath");
            String[] cmd = "sudo gareth --engine default --config \"{\\\"job_type\\\":\\\"transfer\\\",\\\"src\\\":{\\\"accessKeyId\\\":\\\"akakakakak\\\",\\\"ossEndpoint\\\":\\\"cn-hangzhou.aliyun.com\\\",\\\"accessKeySecret\\\":\\\"sksksksksksk\\\",\\\"encrypt\\\":false,\\\"ossBucket\\\":\\\"apsaradb\\\",\\\"type\\\":\\\"oss\\\"},\\\"job\\\":{\\\"decompress\\\":true,\\\"src_path\\\":\\\"removeFilePath\\\",\\\"dest_path\\\":\\\"localFile\\\"},\\\"dest\\\":{\\\"type\\\":\\\"file\\\"}}\" --logfile logPath/gareth.log --error2stdout".split(" ");
            shellMock.verify(()->Shell.execCommand(cmd), Mockito.times(1));
        }
    }
}
