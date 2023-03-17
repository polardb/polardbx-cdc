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

import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;

import java.io.File;
import java.io.IOException;

/**
 * @author yudong
 * @since 2022/8/29
 **/
public class CdcFile implements Comparable<CdcFile> {
    /** file name, without any prefix path */
    private final String name;
    private final ICdcFileSystem fileSystem;

    public CdcFile(String name, ICdcFileSystem fileSystem) {
        this.name = name;
        this.fileSystem = fileSystem;
    }

    public void delete() {
        fileSystem.delete(name);
    }

    public BinlogFileReadChannel getReadChannel() throws IOException {
        return fileSystem.getReadChannel(name);
    }

    public long size() {
        return fileSystem.size(name);
    }

    public String getName() {
        return name;
    }

    public File newFile() {
        assert fileSystem instanceof LocalFileSystem;
        return ((LocalFileSystem) fileSystem).newFile(name);
    }

    @Override
    public int compareTo(CdcFile o) {
        return new CdcFileNameComparator().compare(name, o.getName());
    }
}
