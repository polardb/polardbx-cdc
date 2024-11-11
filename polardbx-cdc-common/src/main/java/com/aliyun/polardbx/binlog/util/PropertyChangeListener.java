/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

public interface PropertyChangeListener {
    void onInit(String propsName, String value);

    void onPropertyChange(String propsName, String oldValue, String newValue);
}
