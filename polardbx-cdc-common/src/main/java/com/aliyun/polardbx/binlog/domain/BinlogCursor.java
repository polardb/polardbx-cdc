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
package com.aliyun.polardbx.binlog.domain;

import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * 包含一个binlog文件的位点信息
 *
 * @author ziyang.lb
 **/
@Getter
@ToString
public class BinlogCursor implements Comparable<BinlogCursor> {

    private final String fileName;
    private final Long filePosition;
    private final String group;
    private final String stream;
    private final String tso;
    private final Long version;

    public BinlogCursor(String fileName, Long filePosition, String group, String stream, String tso, Long version) {
        this.fileName = fileName;
        this.filePosition = filePosition;
        this.group = group;
        this.stream = stream;
        this.tso = tso;
        this.version = version;
    }

    public BinlogCursor(String fileName, Long filePosition) {
        this(fileName, filePosition, null, null, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BinlogCursor cursor = (BinlogCursor) o;
        return fileName.equals(cursor.fileName) &&
            filePosition.equals(cursor.filePosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, filePosition);
    }

    @Override
    public int compareTo(BinlogCursor o) {
        if (o == null) {
            return 1;
        } else {
            int flag = fileName.compareTo(o.fileName);
            if (flag != 0) {
                return flag;
            } else {
                return filePosition.compareTo(o.filePosition);
            }
        }
    }
}
