/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_UNDEF;

/**
 * @author zm
 */

public class BinlogDumpUserVariables {
    EnumBinlogChecksumAlg slaveChecksumAlg;
    long masterHeartbeatPeriod;
    EnumClientType clientType;
    String traceId = "";
    long processId = 0;
    String ignoreServerIds = "";
    boolean serverIdFilterDdlEnabled;
    boolean archiveIgnoreEnabled;
    boolean whiteListMode;
    boolean rowsQueryIgnoreEnabled;
    boolean ignoreBySetFlag;
    String user = "";
    Set<String> tableSet = new HashSet<>();
    String instId = "";

    public BinlogDumpUserVariables() {
        slaveChecksumAlg = BINLOG_CHECKSUM_ALG_UNDEF;
        masterHeartbeatPeriod = DynamicApplicationConfig.getLong(BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD);
        clientType = EnumClientType.DEFAULT;
        archiveIgnoreEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED);
        rowsQueryIgnoreEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_ROWS_QUERY_IGNORE_ENABLED);
        ignoreBySetFlag = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_IGNORE_BY_SET_FLAG);
        serverIdFilterDdlEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_SERVER_ID_FILTER_DDL);
        String tableIgnore = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_IGNORE_TABLE);
        String tableAllow = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_DO_TABLE);
        initTableIgnore(tableIgnore, tableAllow);
    }

    public void initTableIgnore(String tableIgnore, String tableAllow) {
        tableSet.clear();
        // 优先黑名单
        String names = tableIgnore.replaceAll("^['\"]|['\"]$", "");
        if (StringUtils.isEmpty(names)) {
            names = tableAllow.replaceAll("^['\"]|['\"]$", "");
            whiteListMode = true;
        } else {
            whiteListMode = false;
        }
        if (StringUtils.isEmpty(names)) {
            return;
        }
        // 将names按,分割并填入tables中
        String[] tableNames = names.split(",");
        tableSet.addAll(Arrays.asList(tableNames));
    }

    public void initVariablesForColumnar() {
        if (clientType == EnumClientType.COLUMNAR) {
            archiveIgnoreEnabled =
                DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_COLUMNAR_ARCHIVE_IGNORE_ENABLED);
            rowsQueryIgnoreEnabled =
                DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_COLUMNAR_ROWS_QUERY_IGNORE_ENABLED);
            ignoreBySetFlag = DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_COLUMNAR_IGNORE_BY_SET_FLAG);
            String tableIgnore = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_COLUMNAR_TABLE_IGNORE);
            String tableAllow = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_COLUMNAR_TABLE_ALLOW);
            initTableIgnore(tableIgnore, tableAllow);
        }
    }
}
