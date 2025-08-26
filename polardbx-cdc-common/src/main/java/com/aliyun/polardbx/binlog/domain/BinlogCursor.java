/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
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
    private final long timestamp;
    private final int fileSequence;

    public BinlogCursor(String fileName, Long filePosition, String group, String stream, String tso, Long version,
                        int fileSequence) {
        this.fileName = fileName;
        this.filePosition = filePosition;
        this.group = group;
        this.stream = stream;
        this.tso = tso;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
        this.fileSequence = fileSequence;
    }

    public BinlogCursor(String fileName, Long filePosition, String group, String stream, String tso, Long version) {
        this(fileName, filePosition, group, stream, tso, version, 0);
    }

    public BinlogCursor(String fileName, Long filePosition) {
        this(fileName, filePosition, null, null, null, null);
    }

    public BinlogCursor(String fileName, Long filePosition, int fileSequence) {
        this(fileName, filePosition, null, null, null, null, fileSequence);
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
            int flag = BinlogFileUtil.compareBinlogFileName(fileName, o.fileName);
            if (flag != 0) {
                return flag;
            } else {
                return filePosition.compareTo(o.filePosition);
            }
        }
    }
}
