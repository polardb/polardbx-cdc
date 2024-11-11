/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;

/**
 * This class stores generic name/value pairs in an easily serializable format. It provides an standard way to represent
 * session variables.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSOption implements Serializable {

    private static final long serialVersionUID = 8461323989391437790L;

    /**
     * Returns the name value.
     *
     * @return Returns the name.
     */
    public abstract String getName();

    /**
     * Returns the value value.
     *
     * @return Returns the value.
     */
    public abstract Serializable getValue();

    /**
     * Change the option value.
     *
     * @param value - The updated value.
     */
    public abstract void setValue(Serializable value);

    /**
     * {@inheritDoc}
     *
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getName().hashCode() ^ getValue().hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other instanceof DBMSOption) {
            return this.equals((DBMSOption) other);
        }
        return false;
    }

    /**
     * Return true if the option is equals other.
     */
    public boolean equals(DBMSOption other) {
        if (other == null) {
            return false;
        }
        if (!this.getName().equals(other.getName())) {
            return false;
        }
        Serializable value = this.getValue();
        Serializable otherValue = other.getValue();
        if (value != null || otherValue != null) {
            if (value == null || otherValue == null) {
                return false;
            }
            if (!value.equals(otherValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return getName() + " = " + getValue();
    }
}
