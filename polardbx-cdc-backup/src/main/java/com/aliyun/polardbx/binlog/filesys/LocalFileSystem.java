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
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yudong
 * @since 2022/8/19
 **/
@Slf4j
public class LocalFileSystem implements IFileSystem {
    private final String fullPath;
    private final String group;
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
        List<File> fileList = BinlogFileUtil.listLocalBinlogFiles(fullPath, group, stream);
        return fileList.stream().map(f -> new CdcFile(f.getName(), this)).sorted(CdcFile::compareTo)
            .collect(Collectors.toList());
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
