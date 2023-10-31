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
package com.aliyun.polardbx.binlog.canal.binlog;

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
        final int val = this.fileName.compareTo(fileName);

        if (val == 0) {
            return (int) (this.position - position);
        }
        return val;
    }

    /**
     * {@inheritDoc}
     *
     * @see Comparable#compareTo(Object)
     */
    public int compareTo(LogPosition o) {
        final int val = fileName.compareTo(o.fileName);

        if (val == 0) {
            return (int) (position - o.position);
        }
        return val;
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
