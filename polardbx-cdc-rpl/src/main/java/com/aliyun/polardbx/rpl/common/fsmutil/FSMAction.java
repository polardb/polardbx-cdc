/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import org.apache.commons.lang3.StringUtils;

public enum FSMAction {
    /*
   for non use
   * */
    NULL("NULL"),

    START_BACKFLOW("START_BACKFLOW");

    private String value;

    public String getValue() {
        return value;
    }

    FSMAction(String value) {
        this.value = value;
    }

    public static FSMAction from(String value) {
        for (FSMAction i : FSMAction.values()) {
            if (StringUtils.equals(i.value, value)) {
                return i;
            }
        }
        return NULL;
    }
}
