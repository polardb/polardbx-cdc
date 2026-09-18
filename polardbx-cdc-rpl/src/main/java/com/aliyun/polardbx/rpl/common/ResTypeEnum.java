/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

public enum ResTypeEnum {

    RDS_MYSQL("RDS_MYSQL"),

    POLARX1("POLARX1"),
    DRDS("DRDS"),

    POLARX2("POLARX2"),

    POLARDB_M("POLARDB_M");

    ResTypeEnum(String value) {
        this.value = value;
    }

    public String value;

    public String getValue() {
        return value;
    }
}
