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

import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * local binlog directory:
 * global binlog: /home/admin/binlog/
 * binlog-x: /home/admin/binlog/Version/group/stream/
 * local binlog name:
 * global binlog: binlog.000001
 * binlog-x: stream#binlog.000001
 * 所有接口方法签名中的fileName都是不带任何前缀路径的纯文件名
 *
 * @author yudong
 * @since 2022/8/19
 **/
@Slf4j
public class LocalFileSystem implements ICdcFileSystem {
    private final String binlogFullPath;
    private final String group;
    private final String stream;

    public LocalFileSystem(String binlogFullPath, String group, String stream) {
        if (!BinlogFileUtil.isValidFullPath(binlogFullPath, group, stream)) {
            throw new PolardbxException("invalid parameter, "
                + "path: " + binlogFullPath
                + ", group: " + group
                + ", stream: " + stream);
        }
        this.group = group;
        this.stream = stream;
        this.binlogFullPath = binlogFullPath;
        createDir();
    }

    @Override
    public CdcFile create(String fileName) {
        return new CdcFile(fileName, this);
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
        File[] files = BinlogFileUtil.listBinlogFiles(binlogFullPath, group, stream);
        for (File file : files) {
            res.add(new CdcFile(file.getName(), this));
        }
        res.sort(CdcFile::compareTo);
        return res;
    }

    @Override
    public String getName(String pureName) {
        return binlogFullPath + File.separator + pureName;
    }

    @Override
    public BinlogFileReadChannel getReadChannel(String fileName) throws IOException {
        if (exist(fileName)) {
            FileInputStream is = new FileInputStream(getName(fileName));
            return new BinlogFileReadChannel(is.getChannel(), is);
        }
        return null;
    }

    public File newFile(String fileName) {
        return new File(getName(fileName));
    }

    private void createDir() {
        File dir = new File(binlogFullPath);
        if (dir.exists()) {
            return;
        }
        if (!dir.mkdirs()) {
            throw new PolardbxException("failed to create local binlog dir: " + binlogFullPath);
        }
    }
}
