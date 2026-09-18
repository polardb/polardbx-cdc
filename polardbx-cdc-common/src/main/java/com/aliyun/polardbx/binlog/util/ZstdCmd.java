/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class ZstdCmd extends DecompressCmd {

    private static final Logger logger = LoggerFactory.getLogger(ZstdCmd.class);
    private String tmpFile;

    public ZstdCmd(String srcFile, String dstFile) {
        super(srcFile, dstFile);
        this.tmpFile = dstFile + "_tmp_zstd";
    }

    public ProcessBuilder buildProcessBuilder() {
        return new ProcessBuilder("zstd", "-d", srcFile, "-o", tmpFile);
    }

    public void execute() throws IOException, InterruptedException {
        ProcessBuilder pb = buildProcessBuilder();
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            process.waitFor();

            if (process.exitValue() != 0) {
                throw new RuntimeException("exec zstd error , src file : " + srcFile);
            }

            renameFile();
        } finally {
            try {
                process.destroy();
            } catch (Exception ignore) {
            }
            // 删除临时_tmp_zstd文件，源和目标外部会处理
            FileUtils.deleteQuietly(new File(tmpFile));
        }

    }

    public void renameFile() throws IOException {
        File file = new File(tmpFile);
        File finalFile = new File(dstFile);
        if (!file.renameTo(finalFile)) {
            throw new IOException(tmpFile + " rename to dst " + dstFile + " failed!");
        }
    }
}
