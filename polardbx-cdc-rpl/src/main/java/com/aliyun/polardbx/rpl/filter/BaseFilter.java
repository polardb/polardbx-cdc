/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.rpl.common.RplConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author jiyue 2021/8/17 13:28
 * @since 5.0.0.0
 */

public class BaseFilter {
    public void init() {
    }

    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        return false;
    }

    public boolean ignoreEventByTso(String tso) {
        return false;
    }

    public String getRewriteDb(String schema, DBMSAction action) {
        return schema;
    }

    public String getRewriteTable(String db, String table) {
        return table;
    }

    protected Set<String> initFilterSet(String filterStr) {
        Set<String> filters = new HashSet<>();
        if (StringUtils.isNotBlank(filterStr)) {
            for (String token : filterStr.trim().split(RplConstants.COMMA)) {
                filters.add(token.trim());
            }
        }
        return filters;
    }

    protected Set<Long> initIgnoreServerIds(String filterStr) {
        Set<String> tmpIgnoreServerIds = initFilterSet(filterStr);
        Set<Long> ignoreServerIds = new HashSet<>();
        for (String serverId : tmpIgnoreServerIds) {
            ignoreServerIds.add(Long.valueOf(serverId));
        }
        return ignoreServerIds;
    }

}
