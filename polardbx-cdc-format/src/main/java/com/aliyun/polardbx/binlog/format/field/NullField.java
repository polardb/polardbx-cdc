/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

public class NullField extends Field {

    public static final NullField INSTANCE = new NullField();
    private final byte[] empty = new byte[0];

    public NullField() {
        super(null);
    }

    @Override
    public byte[] encodeInternal() {
        return empty;
    }

    @Override
    public byte[] doGetTableMeta() {
        return empty;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public boolean isNull() {
        return true;
    }
}
