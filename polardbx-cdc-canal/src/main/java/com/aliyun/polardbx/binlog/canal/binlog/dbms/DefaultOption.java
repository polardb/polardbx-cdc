/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;

/**
 * This class creates a default SQL option implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultOption extends DBMSOption {

    private static final long serialVersionUID = -4741482473441751109L;

    protected String name;
    protected Serializable value;

    /**
     * Creates a new <code>DefaultOption</code> object
     */
    public DefaultOption(String name) {
        this.name = name;
    }

    /**
     * Creates a new <code>DefaultOption</code> object
     */
    public DefaultOption(String name, Serializable value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name value.
     *
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the option value.
     *
     * @return Returns the value.
     */
    public Serializable getValue() {
        return value;
    }

    /**
     * Change the option value.
     *
     * @param value - The updated value.
     */
    public void setValue(Serializable value) {
        this.value = value;
    }
}
