/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

/**
 * Created by ShuGuang
 **/
public class BinlogEventMeta {

    private final int type;
    private final int length;
    private final int startPosition;
    private final int nextPosition;

    public BinlogEventMeta(int type, int length, int startPosition, int nextPosition) {
        this.type = type;
        this.length = length;
        this.startPosition = startPosition;
        this.nextPosition = nextPosition;
    }

    public int getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public int getNextPosition() {
        return nextPosition;
    }
}
