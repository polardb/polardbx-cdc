/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
    public boolean init() {
        return true;
    }

    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        return false;
    }

    public String getRewriteDb(String schema, DBMSAction action) {
        return schema;
    }

    public String getRewriteTable(String table) {
        return table;
    }

    protected Set<String> initFilterSet(String filterStr) {
        Set<String> filters = new HashSet<>();

        if (StringUtils.isBlank(filterStr)) {
            return filters;
        }

        for (String token : filterStr.trim().toLowerCase().split(RplConstants.COMMA)) {
            filters.add(token.trim());
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
