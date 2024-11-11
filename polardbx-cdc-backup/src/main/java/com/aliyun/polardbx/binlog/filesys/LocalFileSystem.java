/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2022/8/19
 **/
@Slf4j
public class LocalFileSystem implements IFileSystem {
    private final String fullPath;
    @Getter
    private final String group;
    @Getter
    private final String stream;

    public LocalFileSystem(String rootPath, String group, String stream) {
        this.fullPath = BinlogFileUtil.getFullPath(rootPath, group, stream);
        this.group = group;
        this.stream = stream;
        createDir();
    }

    @Override
    public boolean delete(String fileName) {
        return newFile(fileName).delete();
    }

    @Override
    public boolean exist(String fileName) {
        return newFile(fileName).exists();
    }

    @Override
    public CdcFile get(String fileName) {
        if (exist(fileName)) {
            return new CdcFile(fileName, this);
        }
        return null;
    }

    @Override
    public long size(String fileName) {
        if (exist(fileName)) {
            return newFile(fileName).length();
        }
        return -1;
    }

    @Override
    public List<CdcFile> listFiles() {
        List<CdcFile> res = new ArrayList<>();

        List<File> fileList = BinlogFileUtil.listLocalBinlogFiles(fullPath, group, stream);
        for (File f : fileList) {
            CdcFile cdcFile = new CdcFile(f.getName(), this);
            cdcFile.setLocation("LOCAL");
            res.add(cdcFile);
        }

        res.sort(CdcFile::compareTo);
        return res;
    }

    @Override
    public String getFullName(String pureName) {
        return fullPath + File.separator + pureName;
    }

    @Override
    public BinlogFileReadChannel getReadChannel(String fileName) throws IOException {
        if (exist(fileName)) {
            FileInputStream is = new FileInputStream(getFullName(fileName));
            return new BinlogFileReadChannel(is.getChannel(), is);
        }
        return null;
    }

    public File newFile(String fileName) {
        return new File(getFullName(fileName));
    }

    private void createDir() {
        File dir = new File(fullPath);
        if (dir.exists()) {
            return;
        }
        if (!dir.mkdirs()) {
            throw new PolardbxException("failed to create local binlog dir: " + fullPath);
        }
    }

    public void cleanDir() throws IOException {
        File dir = new File(fullPath);
        if (dir.exists()) {
            FileUtils.cleanDirectory(dir);
        }
    }
}
