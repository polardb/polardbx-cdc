/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.filter;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryMeta;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;


/**
 * @author jiyue 2022/11/30 17:57
 * @since 5.0.0.0
 */

@Data
@Slf4j
public class FlashBackFilter extends BaseFilter {

    private RecoveryMeta recoveryMeta;
    private String doDb;
    private String doTable;
    private Map<String, Boolean> filterCache;

    public FlashBackFilter(RecoveryMeta recoveryMeta) {
        this.recoveryMeta = recoveryMeta;
    }

    @Override
    public boolean init() {
        try {
            doDb = recoveryMeta.getSchema();
            doTable = recoveryMeta.getTable();
            filterCache = new HashMap<>(128);
            return true;
        } catch (Throwable e) {
            log.error("ReplicaFilter init failed", e);
            return false;
        }
    }


    @Override
    public boolean ignoreEvent(String schema, String tbName, DBMSAction action, long serverId) {
        String key = schema + "." + tbName + "." + action.name();
        if (filterCache.containsKey(key)) {
            return filterCache.get(key);
        }

        boolean skip = !dbOk(schema) || !tableOk(schema, tbName);
        filterCache.put(key, skip);
        return skip;
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::tables_ok(const char *db, TABLE_LIST *tables)
     */
    private boolean tableOk(String db, String tb) {
        if (StringUtils.isBlank(doTable)) {
            return true;
        } else {
            return StringUtils.equalsIgnoreCase(doTable, tb);
        }
    }

    /**
     * refer: https://github.com/mysql/mysql-server/blob/8.0/sql/rpl_filter.cc bool
     * Rpl_filter::db_ok(const char *db, bool need_increase_counter)
     */
    private boolean dbOk(String db) {
        if (StringUtils.isBlank(doDb)) {
            return true;
        } else {
            return StringUtils.equalsIgnoreCase(doDb, db);
        }
    }

}


