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
package oss;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.oss.OssClientProvider;
import com.aliyun.polardbx.binlog.oss.OssConfig;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MultiAppendTest {

    private OssClientProvider provider;

    @Before
    public void prepare() {
        final SpringContextBootStrap appContextBootStrap =
            new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        OssConfig ossConfig = new OssConfig();
        ossConfig.bucketName = "";
        ossConfig.accessKeyId = "";
        ossConfig.accessKeySecret = "";
        ossConfig.endpoint = "";
        ossConfig.polardbxInstance = "";
        provider = new OssClientProvider(ossConfig);
    }

    @Test
    public void test() throws IOException {
        File f = new File("/Users/yanfenglin/Downloads/java_pid50160.hprof");
        OssClientProvider.MultiUploader uploader =
            new OssClientProvider.MultiUploader("java_pid50160.hprof", f.length(), provider);
        uploader.begin();
        FileInputStream reader = (new FileInputStream(f));
        int len = -1;
        byte[] buffer = new byte[1024 * 1024];
        while ((len = reader.read(buffer)) != -1) {
            uploader.append(buffer, len);
        }
        uploader.end();
    }
}
