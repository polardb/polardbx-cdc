/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
