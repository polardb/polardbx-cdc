/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
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

    public static boolean isSupportApply(DDLRecord record) {
        if (record.getExtInfo() != null) {
            if (record.getExtInfo().isGsi()) {
                return false;
            }
            if (record.getExtInfo().isCci()) {
                return false;
            }
        }
        return !"DROP_SEQUENCE".equals(record.getSqlKind())
            && !"CREATE_SEQUENCE".equals(record.getSqlKind())
            && !"RENAME_SEQUENCE".equals(record.getSqlKind())
            && !"ALTER_SEQUENCE".equals(record.getSqlKind())
            && !"CREATE_FUNCTION".equals(record.getSqlKind())
            && !"CREATE_JAVA_FUNCTION".equalsIgnoreCase(record.getSqlKind())
            && !"ALTER_FUNCTION".equals(record.getSqlKind())
            && !"DROP_FUNCTION".equals(record.getSqlKind())
            && !"DROP_JAVA_FUNCTION".equals(record.getSqlKind())
            && !"CREATE_PROCEDURE".equals(record.getSqlKind())
            && !"DROP_PROCEDURE".equals(record.getSqlKind())
            && !"ALTER_PROCEDURE".equals(record.getSqlKind())
            && !"CREATE_TABLEGROUP".equals(record.getSqlKind())
            && !"DROP_TABLEGROUP".equals(record.getSqlKind())
            && !"MERGE_TABLEGROUP".equals(record.getSqlKind())
            && !"ANALYZE_TABLE".equals(record.getSqlKind())
            && !"SET_PASSWORD".equals(record.getSqlKind())
            && !"REVOKE_ROLE".equals(record.getSqlKind())
            && !"REVOKE_PRIVILEGE".equals(record.getSqlKind())
            && !"GRANT_ROLE".equals(record.getSqlKind())
            && !"GRANT_PRIVILEGE".equals(record.getSqlKind())
            && !"DROP_USER".equals(record.getSqlKind())
            && !"DROP_ROLE".equals(record.getSqlKind())
            && !"CREATE_USER".equals(record.getSqlKind())
            && !"CREATE_ROLE".equals(record.getSqlKind())
            && !"SQL_SET_DEFAULT_ROLE".equals(record.getSqlKind())
            && !"CREATE_JOINGROUP".equals(record.getSqlKind())
            && !"ALTER_JOINGROUP".equals(record.getSqlKind())
            && !"DROP_JOINGROUP".equals(record.getSqlKind())
            && !"CONVERT_ALL_SEQUENCES".equals(record.getSqlKind())
            && !"DROP_VIEW".equals(record.getSqlKind())
            && !"DROP_MATERIALIZED_VIEW".equals(record.getSqlKind())
            && !"CREATE_VIEW".equals(record.getSqlKind())
            && !"ALTER_VIEW".equals(record.getSqlKind())
            && !"CREATE_MATERIALIZED_VIEW".equals(record.getSqlKind())
            && !"ALTER_INDEX_VISIBILITY".equals(record.getSqlKind())
            && !"ALTER_TABLEGROUP_ADD_TABLE".equals(record.getSqlKind())
            && !("ALTER_TABLE_SET_TABLEGROUP".equals(record.getSqlKind()) && record.getExtInfo() != null
            && record.getExtInfo().isGsi())
            && !("ALTER_TABLEGROUP".equals(record.getSqlKind()) && record.getVisibility() != 0);
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
