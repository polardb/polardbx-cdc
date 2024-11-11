/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
        final Scanner scanner = new Scanner(process.getInputStream(), "utf8");
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
