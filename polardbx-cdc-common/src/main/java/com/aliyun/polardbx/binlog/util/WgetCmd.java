/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author chengjin.lyf on 2019/3/3 3:08 PM
 * @since 1.0.25
 */
public class WgetCmd {

    private String link;
    private String fileName;

    public WgetCmd(String link, String fileName) {
        this.link = link;
        this.fileName = fileName;
    }

    public File execute() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("wget", "--no-check-certificate", link, "-O", fileName + "_tmp_wget");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        final InputStream inputStream = process.getInputStream();
        Thread t = new Thread() {
            @Override
            public void run() {

                byte[] cache = new byte[512];
                int idx = 0;
                int data = 0;
                long st = 0;
                try {
                    while ((data = inputStream.read()) != -1) {
                        long now = System.currentTimeMillis();
                        if (idx >= cache.length) {
                            System.out.write(cache, 0, idx);
                            idx = 0;
                            continue;
                        }
                        cache[idx++] = (byte) data;
                        if (data == '\n') {
                            if (now - st > 30000) {
                                st = now;
                                System.out.write(cache, 0, idx);
                            }
                            idx = 0;
                            continue;
                        }
                    }
                } catch (Exception e) {

                }
            }
        };
        t.setDaemon(true);
        t.start();

        process.waitFor();

        if (process.exitValue() != 0) {
            throw new RuntimeException("exec wget error : link " + link);
        }

        File file = new File(fileName + "_tmp_wget");
        File finalFile = new File(fileName);
        file.renameTo(finalFile);
        return finalFile;
    }

}
