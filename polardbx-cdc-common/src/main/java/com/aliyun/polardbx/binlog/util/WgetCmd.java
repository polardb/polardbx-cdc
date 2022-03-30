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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author chengjin.lyf on 2019/3/3 3:08 PM
 * @since 1.0.25
 */
public class WgetCmd {

    private static final Logger logger = LoggerFactory.getLogger(WgetCmd.class);

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
        final Scanner scanner = new Scanner(process.getInputStream());
        scanner.useDelimiter("\n");
        Thread t = new Thread("wget-progress") {
            @Override
            public void run() {

                try {
                    while (scanner.hasNext()) {
                        String line = scanner.next();
                        WgetContext.add(line);
                    }
                } catch (Exception e) {
                    logger.error("print wget progress error! link : " + link, e);
                } finally {
                    WgetContext.finish();
                    scanner.close();
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
