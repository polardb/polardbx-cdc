/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.util.BinlogFileUtil;

/**
 * Implements binlog position.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class LogPosition implements Cloneable, Comparable<LogPosition> {

    /* binlog file's name */
    protected String fileName;

    /* position in file */
    protected long position;

    /**
     * Binlog position init.
     *
     * @param fileName file name for binlog files: mysql-bin.000001
     */
    public LogPosition(String fileName) {
        this.fileName = fileName;
        this.position = 0L;
    }

    /**
     * Binlog position init.
     *
     * @param fileName file name for binlog files: mysql-bin.000001
     */
    public LogPosition(String fileName, final long position) {
        this.fileName = fileName;
        this.position = position;
    }

    /**
     * Binlog position copy init.
     */
    public LogPosition(LogPosition source) {
        this.fileName = source.fileName;
        this.position = source.position;
    }

    public final String getFileName() {
        return fileName;
    }

    public final long getPosition() {
        return position;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    /* Clone binlog position without CloneNotSupportedException */
    public LogPosition clone() {
        try {
            return (LogPosition) super.clone();
        } catch (CloneNotSupportedException e) {
            // Never happend
            return null;
        }
    }

    /**
     * Compares with the specified fileName and position.
     */
    public final int compareTo(String fileName, final long position) {
        final int val = BinlogFileUtil.compareBinlogFileName(this.fileName, fileName);

        if (val == 0) {
            long diff = this.position - position;
            if (diff > 0){
                return 1;
            }else if (diff == 0){
                return 0;
            }else {
                return -1;
            }
        }
        return val;
    }

    /**
     * {@inheritDoc}
     *
     * @see Comparable#compareTo(Object)
     */
    public int compareTo(LogPosition o) {
        return compareTo(o.fileName, o.position);
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#equals(Object)
     */
    public boolean equals(Object obj) {
        if (obj instanceof LogPosition) {
            LogPosition pos = ((LogPosition) obj);
            return fileName.equals(pos.fileName) && (this.position == pos.position);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    public String toString() {
        return fileName + ':' + position;
    }
}
