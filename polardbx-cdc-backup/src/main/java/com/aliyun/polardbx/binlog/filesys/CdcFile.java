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
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;

/**
 * @author yudong
 * @since 2022/8/29
 **/
public class CdcFile implements Comparable<CdcFile> {
    /**
     * file name, without any prefix path
     */
    @Getter
    private final String name;
    private final IFileSystem fileSystem;
    @Setter
    @Getter
    private BinlogOssRecord record;
    @Setter
    @Getter
    private String location;

    private final Format dataFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CdcFile(String name, IFileSystem fileSystem) {
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
        if (record != null && record.getLogSize() != 0) {
            return record.getLogSize();
        }
        return fileSystem.size(name);
    }

    public String getCreatedTime() {
        if (record != null && record.getGmtCreated() != null) {
            return dataFormat.format(record.getGmtCreated());
        }

        return "";
    }

    public String getLastModifyTime() {
        if (record != null && record.getGmtModified() != null) {
            return dataFormat.format(record.getGmtModified());
        }

        return "";
    }

    // TODO: add binlogReader to get first event
    public String getFirstEventTime() {
        if (record != null && record.getLogBegin() != null) {
            return dataFormat.format(record.getLogBegin());
        }

        return "";
    }

    // TODO: add binlogReader to get last event
    public String getLastEventTime() {
        if (record != null && record.getLogEnd() != null) {
            return dataFormat.format(record.getLogEnd());
        }

        return "";
    }

    // TODO: add binlogReader to get last tso
    public String getLastTso() {
        if (record != null && record.getLastTso() != null) {
            return record.getLastTso();
        }

        return "";
    }

    public String getUploadStatus() {
        if (record != null) {
            return BinlogUploadStatus.fromValue(record.getUploadStatus()).name();
        }

        return "";
    }

    public File newFile() {
        assert fileSystem instanceof LocalFileSystem;
        return ((LocalFileSystem) fileSystem).newFile(name);
    }

    @Override
    public int compareTo(CdcFile o) {
        return this.name.compareTo(o.name);
    }

    public boolean exist() {
        return fileSystem.exist(name);
    }
}
