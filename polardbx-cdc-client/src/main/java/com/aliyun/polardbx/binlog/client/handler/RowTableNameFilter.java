/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class RowTableNameFilter {

    private static final Logger logger = LoggerFactory.getLogger(RowTableNameFilter.class);
    /**
     * db.table set
     */
    private volatile Set<String> acceptTableSet = null;
    /**
     * db.table set
     */
    private volatile Set<String> ignoreTableSet = null;

    private volatile boolean lowercase = true;

    public void setAcceptTableSet(Set<String> acceptTableSet) {
        this.acceptTableSet = acceptTableSet;
    }

    public void setIgnoreTableSet(Set<String> ignoreTableSet) {
        this.ignoreTableSet = ignoreTableSet;
    }

    public boolean filter(String fullTableName) {
        if (lowercase) {
            fullTableName = StringUtils.lowerCase(fullTableName);
        }
        if (acceptTableSet != null && !acceptTableSet.contains(fullTableName)) {
            if (logger.isDebugEnabled()) {
                logger.debug("ignore table data because {} not in acceptTableSet!", fullTableName);
            }
            return true;
        }
        if (ignoreTableSet != null && ignoreTableSet.contains(fullTableName)) {
            if (logger.isDebugEnabled()) {
                logger.debug("ignore table data because {} in ignoreTableSet!", fullTableName);
            }
            return true;
        }
        return false;
    }
}
