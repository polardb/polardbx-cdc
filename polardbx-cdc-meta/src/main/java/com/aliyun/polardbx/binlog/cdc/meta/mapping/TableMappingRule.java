/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta.mapping;

import java.util.regex.Pattern;

public class TableMappingRule {
    private final Pattern pattern;
    private final String virtualTableName;

    public TableMappingRule(String regex, String virtualTableName) {
        this.pattern = Pattern.compile(regex);
        this.virtualTableName = virtualTableName;
    }

    public boolean matches(String tableName) {
        return pattern.matcher(tableName).matches();
    }

    public String getVirtualTableName() {
        return virtualTableName;
    }
}
