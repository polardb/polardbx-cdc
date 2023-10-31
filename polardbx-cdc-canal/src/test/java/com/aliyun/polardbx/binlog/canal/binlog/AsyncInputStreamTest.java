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
package com.aliyun.polardbx.binlog.canal.binlog;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-22 13:38
 **/
public class AsyncInputStreamTest {

    @Test
    public void testAsyncInput() throws IOException {
        Resource resource = new DefaultResourceLoader().getResource("classpath:testing-conf/gms_tables.sql");
        long count1 = scan(new FileInputStream(resource.getFile()));
        long count2 = scan(new AsyncInputStream(new FileInputStream(resource.getFile())));
        Assert.assertEquals(count1, count2);
    }

    private long scan(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream);
        int lineSize = 0;
        while (scanner.hasNext()) {
            scanner.nextLine();
            lineSize++;
        }
        return lineSize;
    }

}
