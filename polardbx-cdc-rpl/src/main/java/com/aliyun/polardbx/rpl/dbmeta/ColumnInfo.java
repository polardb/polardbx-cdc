/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/11/29 21:19
 * @since 5.0.0.0
 */
@Data
public class ColumnInfo {

    private String name;
    // type value refers to
    // "Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/src.zip!/java/sql/Types.java"
    private int type;
    private String javaCharset;
    private boolean nullable;
    private boolean generated;
    private String typeName;
    private int size;
    private boolean onUpdate;

    public ColumnInfo(String name, int type, String javaCharset, boolean nullable, boolean generated, String typeName,
                      int size) {
        this.name = name;
        this.type = type;
        this.javaCharset = javaCharset;
        this.nullable = nullable;
        this.generated = generated;
        this.typeName = typeName;
        this.size = size;
    }

    public boolean isOnUpdate() {
        return onUpdate;
    }

    public void setOnUpdate(boolean onUpdate) {
        this.onUpdate = onUpdate;
    }
}
