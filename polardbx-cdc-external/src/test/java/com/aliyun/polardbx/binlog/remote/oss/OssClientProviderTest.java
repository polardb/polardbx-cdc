/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.remote.oss;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/8/18
 **/
@Slf4j
public class OssClientProviderTest extends BaseTest {
    private final String testPath = "oss-manager-test";
    private OssManager ossManager;

    @Before
    public void before() {
        OssConfig ossConfig = new OssConfig();
        ossConfig.setAccessKeyId(getString(ConfigKeys.OSS_ACCESSKEY_ID));
        ossConfig.setAccessKeySecret(getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET));
        ossConfig.setBucketName(getString(ConfigKeys.OSS_BUCKET));
        ossConfig.setEndpoint(getString(ConfigKeys.OSS_ENDPOINT));
        ossConfig.setPolardbxInstance("unit-test");
        ossManager = new OssManager(ossConfig);
        ossManager.deleteAll(testPath);
    }

    @After
    public void after() throws IOException {
        ossManager.deleteAll(testPath);
        FileUtils.deleteDirectory(new File(testPath));
    }

    @Test
    public void ossManagerTest() {
        // test listFiles
        String ossTestPath = BinlogFileUtil.buildRemoteFileFullName(testPath, "test");
        ossManager.deleteAll(ossTestPath);
        List<String> actualFileList = ossManager.listFiles(ossTestPath);
        Assert.assertTrue(actualFileList.isEmpty());

        // prepare some files
        List<String> expectFileList = new ArrayList<>();
        expectFileList.add("oss-manager-test-1.txt");
        expectFileList.add("oss-manager-test-2.txt");
        expectFileList.add("oss-manager-test-3.txt");
        String fileContent = "Stray birds of summer come to my window to sing and fly away.";
        for (String fileName : expectFileList) {
            Appender appender = ossManager.providerAppender(ossTestPath + "/" + fileName);
            appender.begin();
            byte[] bytes = fileContent.getBytes();
            appender.append(bytes, bytes.length);
            appender.end();
        }

        // test listFiles
        actualFileList = ossManager.listFiles(ossTestPath);
        expectFileList.sort(String::compareTo);
        actualFileList.sort(String::compareTo);
        boolean compareResult = ListUtils.isEqualList(expectFileList, actualFileList);
        Assert.assertTrue(compareResult);

        // test isObjectsExistForPrefix
        boolean expectTrue = ossManager.isObjectsExistForPrefix(ossTestPath);
        Assert.assertTrue(expectTrue);
        boolean expectFalse = ossManager.isObjectsExistForPrefix("not-exist-path");
        Assert.assertFalse(expectFalse);

        // test getSize
        long expectSize = fileContent.length();
        long actualSize = ossManager.getSize(ossTestPath + "/" + expectFileList.get(0));
        Assert.assertEquals(expectSize, actualSize);

        // test deleteFile
        ossManager.delete(ossTestPath + "/" + expectFileList.get(0));
        expectFileList.remove(0);
        actualFileList = ossManager.listFiles(ossTestPath);
        compareResult = ListUtils.isEqualList(expectFileList, actualFileList);
        Assert.assertTrue(compareResult);

        // test getObjectData
        byte[] data = ossManager.getObjectData(ossTestPath + "/" + expectFileList.get(0));
        String actualFileContent = new String(data);
        Assert.assertEquals(fileContent, actualFileContent);

        // test clearDirectory
        ossManager.deleteAll(ossTestPath);
        actualFileList = ossManager.listFiles(ossTestPath);
        Assert.assertEquals(0, actualFileList.size());
    }
}
