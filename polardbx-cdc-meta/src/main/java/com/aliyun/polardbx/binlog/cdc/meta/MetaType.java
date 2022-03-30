package com.aliyun.polardbx.binlog.cdc.meta;

/**
 * created by ziyang.lb
 **/
public enum MetaType {
    /**
     * Snapshot
     */
    SNAPSHOT((byte) 1),
    /**
     * DDL SQL
     */
    DDL((byte) 2);

    private final byte value;

    MetaType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
