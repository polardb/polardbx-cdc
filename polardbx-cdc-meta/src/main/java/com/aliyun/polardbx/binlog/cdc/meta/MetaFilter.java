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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_LOGIC_DDL_DB_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_LOGIC_DDL_TABLE_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_LOGIC_DDL_TSO_BLACKLIST;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MetaFilter {
    private static Set<String> logicDdlApplyDatabaseBlackList;
    private static Set<String> logicDdlApplyTableBlackList;
    private static Set<String> logicDdlApplyTsoBlackList;

    static {
        initBlacklist();
    }

    public static boolean isDbInApplyBlackList(String dbName) {
        return logicDdlApplyDatabaseBlackList.contains(dbName);
    }

    public static boolean isTableInApplyBlackList(String fullTableName) {
        return logicDdlApplyTableBlackList.contains(fullTableName);
    }

    public static boolean isTsoInApplyBlackList(String tso) {
        return logicDdlApplyTsoBlackList.contains(tso);
    }

    private static void initBlacklist() {
        String configStr1 = DynamicApplicationConfig.getString(META_BUILD_LOGIC_DDL_DB_BLACKLIST);
        if (StringUtils.isNotBlank(configStr1)) {
            String[] array = configStr1.toLowerCase().split(",");
            logicDdlApplyDatabaseBlackList = Sets.newHashSet(array);
        } else {
            logicDdlApplyDatabaseBlackList = Sets.newHashSet();
        }
        log.info("ddl apply database blacklist is " + logicDdlApplyDatabaseBlackList);

        String configStr2 = DynamicApplicationConfig.getString(META_BUILD_LOGIC_DDL_TABLE_BLACKLIST);
        if (StringUtils.isNotBlank(configStr2)) {
            String[] array = configStr2.toLowerCase().split(",");
            logicDdlApplyTableBlackList = Sets.newHashSet(array);
        } else {
            logicDdlApplyTableBlackList = Sets.newHashSet();
        }
        log.info("ddl apply table blacklist is " + logicDdlApplyTableBlackList);

        String configStr3 = DynamicApplicationConfig.getString(META_BUILD_LOGIC_DDL_TSO_BLACKLIST);
        if (StringUtils.isNotBlank(configStr3)) {
            String[] array = configStr3.toLowerCase().split(",");
            logicDdlApplyTsoBlackList = Sets.newHashSet(array);
        } else {
            logicDdlApplyTsoBlackList = Sets.newHashSet();
        }
        log.info("ddl apply tso blacklist is " + logicDdlApplyTsoBlackList);
    }
}
