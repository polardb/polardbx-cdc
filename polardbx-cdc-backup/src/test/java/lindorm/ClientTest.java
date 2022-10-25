/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lindorm;

import com.aliyun.polardbx.binlog.BinlogBackupManager;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.lindorm.LindormClient;
import com.aliyun.polardbx.binlog.lindorm.LindormConfig;
import com.aliyun.polardbx.binlog.lindorm.LindormProvider;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;

public class ClientTest {

    @Test
    public void testCreateBucket() throws Exception {
        String hostname = "";
        int port = 9190;
        int downloadPort = 9042;
        String bucketName = "testBBBBucket22";

        String filePath = "/Users/yanfenglin/Downloads/mysql-bin.001323";
        String ak = "accessKey";
        String sk = "accessSecret";
        LindormClient client = new LindormClient(ak, sk, hostname, port, downloadPort, bucketName);
        client.initBucket();
        long begin = System.currentTimeMillis();
        LindormProvider.LindormAppender appender = new LindormProvider.LindormAppender(client, filePath);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filePath));
        byte[] bytes = new byte[1024 * 1024 * 5];
        appender.begin();
        int len = 0;
        while ((len = bis.read(bytes)) != -1) {
            appender.append(bytes, len);
            System.out.println(" append bytes " + len);
        }
        appender.end();
        long end = System.currentTimeMillis();
        System.out.println("success download costs : " + (end - begin));

    }

    @Test
    public void testUpload() {
        String localDir = "/Users/yanfenglin/Documents/polardbx-binlog/dumper/binlog";
        LindormConfig config = new LindormConfig();
        RemoteBinlogProxy.getInstance().configLindorm(config);
        BinlogBackupManager manager = new BinlogBackupManager();
    }
}
