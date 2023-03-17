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

import java.util.Objects;

/**
 * Created by ziyang.lb
 **/
public class Cursor implements Comparable<Cursor> {

    private final String fileName;
    private final Long filePosition;
    private final String group;
    private final String stream;
    private final String tso;
    private final Long version;

    public Cursor(String fileName, Long filePosition, String group, String stream, String tso, Long version) {
        this.fileName = fileName;
        this.filePosition = filePosition;
        this.group = group;
        this.stream = stream;
        this.tso = tso;
        this.version = version;
    }

    public Cursor(String fileName, Long filePosition) {
        this(fileName, filePosition, null, null, null, null);
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFilePosition() {
        return filePosition;
    }

    public String getGroup() {
        return group;
    }

    public String getStream() {
        return stream;
    }

    public String getTso() {
        return tso;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Cursor cursor = (Cursor) o;
        return fileName.equals(cursor.fileName) &&
            filePosition.equals(cursor.filePosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, filePosition);
    }

    @Override
    public int compareTo(Cursor o) {
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

    @Override
    public String toString() {
        return "Cursor{" +
            "fileName='" + fileName + '\'' +
            ", filePosition=" + filePosition +
            ", group='" + group + '\'' +
            ", stream='" + stream + '\'' +
            ", tso='" + tso + '\'' +
            ", version=" + version +
            '}';
    }
}
