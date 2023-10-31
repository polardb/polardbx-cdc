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
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/8/10
 **/
@Slf4j
@Ignore
public class OssAppendTest extends BaseTest {
    private final String testPath = "oss-appender-test";
    private OssManager ossManager;

    @Before
    public void before() {
        OssConfig ossConfig = new OssConfig();
        ossConfig.setAccessKeyId(getString(ConfigKeys.OSS_ACCESSKEY_ID));
        ossConfig.setAccessKeySecret(getString(ConfigKeys.OSS_ACCESSKEY_ID_SECRET));
        ossConfig.setBucketName(getString(ConfigKeys.OSS_BUCKET));
        ossConfig.setEndpoint(getString(ConfigKeys.OSS_ENDPOINT));
        ossConfig.setPolardbxInstance("test-instance");
        ossManager = new OssManager(ossConfig);
        ossManager.deleteAll(testPath);
    }

    @After
    public void after() {
        ossManager.deleteAll(testPath);
    }

    @Test
    public void simpleAppendSmallFileTest() {
        int testRounds = 10;
        long fileSizeUpperLimit = 1024 * 1024;
        int bufferSize = 100 * 1024;
        String baseFileName = testPath + "/simple-append-small-test";

        for (int i = 0; i < testRounds; i++) {
            String fileName = String.format("%s-%d.txt", baseFileName, i);
            log.info("fileName:{}", fileName);
            long fileSize = ThreadLocalRandom.current().nextLong(fileSizeUpperLimit);
            Appender appender = ossManager.providerAppender(fileName);
            doAppend(appender, fileSize, bufferSize);
            log.info("file:{}, size:{} bytes append successfully.", fileName, fileSize);
        }
    }

    @Test
    public void simpleAppendBigFileTest() {
        int testRounds = 2;
        long fileSizeUpperLimit = 1024 * 1024 * 1024 * 8L;
        int bufferSize = 100 * 1024 * 1024;
        String baseFileName = testPath + "/simple-append-big-test";

        for (int i = 0; i < testRounds; i++) {
            String fileName = String.format("%s-%d.txt", baseFileName, i);
            long fileSize = ThreadLocalRandom.current().nextLong(fileSizeUpperLimit);
            log.info("append file:{}, bytes:{} to oss.", fileName, fileSize);
            Appender appender = ossManager.providerAppender(fileName);
            doAppend(appender, fileSize, bufferSize);
            log.info("file:{}, size:{} bytes append successfully.", fileName, fileSize);
        }
    }

    @Test
    public void multiAppendSmallFileTest() {
        int testRounds = 10;
        long fileSizeUpperLimit = 1024 * 1024;
        int bufferSize = 100 * 1024;
        String baseFileName = testPath + "/multi-append-small-test";

        for (int i = 0; i < testRounds; i++) {
            String fileName = String.format("%s-%d.txt", baseFileName, i);
            log.info("fileName:{}", fileName);
            long fileSize = ThreadLocalRandom.current().nextLong(fileSizeUpperLimit);
            Appender appender = ossManager.providerMultiAppender(fileName, fileSize);
            doAppend(appender, fileSize, bufferSize);
            log.info("file:{}, size:{} bytes append successfully.", fileName, fileSize);
        }
    }

    @Test
    public void multiAppendBigFileTest() {
        int testRounds = 2;
        long fileSizeUpperLimit = 1024 * 1024 * 1024 * 8L;
        int bufferSize = 100 * 1024 * 1024;
        String baseFileName = testPath + "/multi-append-big-test";

        for (int i = 0; i < testRounds; i++) {
            String fileName = String.format("%s-%d.txt", baseFileName, i);
            long fileSize = ThreadLocalRandom.current().nextLong(fileSizeUpperLimit);
            log.info("append file:{}, bytes:{} to oss.", fileName, fileSize);
            Appender appender = ossManager.providerMultiAppender(fileName, fileSize);
            doAppend(appender, fileSize, bufferSize);
            log.info("file:{}, size:{} bytes append successfully.", fileName, fileSize);
        }
    }

    private void doAppend(Appender appender, long fileSize, int bufferSize) {
        log.info("start to append, buffer size:{}.", bufferSize);
        byte[] buffer = new byte[bufferSize];
        long remainSize = fileSize;

        appender.begin();
        while (remainSize > 0) {
            new Random().nextBytes(buffer);
            int len = (int) Math.min(remainSize, bufferSize);
            remainSize -= len;
            appender.append(buffer, len);
            log.info("append {} bytes successfully.", len);
        }
        appender.end();
    }
}
