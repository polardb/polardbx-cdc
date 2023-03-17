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
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.binlog.AsyncInputStream;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

public class AsyncInputStreamTest {

    public static void main(String[] args) throws IOException {
        long base = System.currentTimeMillis();
        testAsyncInput();
        System.out.println(System.currentTimeMillis() - base);
//        base = System.currentTimeMillis();
//        System.out.println("begin");
//        testSyncInput();
//        System.out.println(System.currentTimeMillis() - base);
    }

    private static void testAsyncInput() throws IOException {
        String filePath = "/Users/yanfenglin/Downloads/mysql-bin.001661.log";
        FileInputStream fis = new FileInputStream(filePath);
        Scanner scanner = new Scanner(new AsyncInputStream(fis));
        int lineSize = 0;
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            lineSize++;

        }
        System.out.println("line count : " + lineSize);
        fis.close();
    }

    private static void testSyncInput() throws IOException {
        String filePath = "/Users/yanfenglin/Downloads/mysql-bin.001661.log";
        FileInputStream fis = new FileInputStream(filePath);
        Scanner scanner = new Scanner(fis);
        int lineSize = 0;
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            lineSize++;

        }
        System.out.println("line count : " + lineSize);
        fis.close();
    }
}
