/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

public enum LowerCaseTableNameVariables {
    CASE_SENSITIVE(0), LOWERCASE(1), STORE_AS_GIVEN(2);

    private int value;

    LowerCaseTableNameVariables(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
