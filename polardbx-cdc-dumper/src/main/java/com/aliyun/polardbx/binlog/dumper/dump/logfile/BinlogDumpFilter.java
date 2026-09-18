/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author zm
 * 用于在dump过程中过滤一些下游不想收到的event
 * 其配置优先级为 session > user global > global > cdc global (详见CN代码com.alibaba.polardbx.server.ServerConnection#prepareBinlogDumpExtConfig)
 * 暂时不会过滤压缩事件
 */
@Slf4j
public class BinlogDumpFilter {
    private static final byte[] ARCHIVE_BYTES = "ARCHIVE".getBytes();
    private static final byte[] BEGIN_BYTES = "BEGIN".getBytes();
    private final boolean archiveIgnoreEnabled;
    private final boolean rowsQueryIgnoreEnabled;
    private final Set<String> tableNameSet;
    private final Set<Long> tableIdSet;
    private final Set<Long> tableIdIgnoreOrAllowSet;
    @Getter
    private final boolean binlogDumpFilterEnabled;
    @Setter
    private boolean archiveIgnoring;
    @Getter
    private final boolean ignoreBySetFlag;
    private final Set<Long> ignoreServerIds = new HashSet<>();
    /**
     * true，白名单模式，即只允许白名单中的表进行同步,False则是黑名单模式
     */
    private final boolean whiteListMode;
    private final boolean serverIdIgnoreEnabled;
    private final boolean serverIdFilterDdlEnabled;

    public BinlogDumpFilter(BinlogDumpUserVariables vars) {
        this.archiveIgnoreEnabled = vars.archiveIgnoreEnabled;
        this.tableNameSet = vars.tableSet;
        this.rowsQueryIgnoreEnabled = vars.rowsQueryIgnoreEnabled;
        this.whiteListMode = vars.whiteListMode;
        initIgnoreServerIds(vars.ignoreServerIds);
        this.ignoreBySetFlag = vars.ignoreBySetFlag;
        this.serverIdFilterDdlEnabled = vars.serverIdFilterDdlEnabled;

        if (vars.clientType == EnumClientType.DEFAULT) {
            serverIdIgnoreEnabled =
                DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_SERVER_ID_IGNORE_ENABLED);
        } else {
            serverIdIgnoreEnabled = false;
        }

        binlogDumpFilterEnabled =
            archiveIgnoreEnabled || rowsQueryIgnoreEnabled || !tableNameSet.isEmpty() || serverIdIgnoreEnabled;
        tableIdIgnoreOrAllowSet = new HashSet<>();
        tableIdSet = new HashSet<>();
        log.info(
            "init binlog dump filter with archiveIgnored:{},rowsIgnored:{},whiteMode:{},tableSet:{},ignoreServerIds:{},serverIdIgnoreEnabled:{},serverIdFilterDdlEnabled:{}",
            archiveIgnoreEnabled, rowsQueryIgnoreEnabled, whiteListMode, tableNameSet, ignoreServerIds,
            serverIdIgnoreEnabled, serverIdFilterDdlEnabled);
    }

    public boolean filter(ByteArray byteArray) {
        int offset = byteArray.getPos();
        byteArray.skip(4);
        int eventType = byteArray.read();
        long serverId = byteArray.readLong(4);
        if (serverIdIgnoreEnabled && ignoreServerIds.contains(serverId)) {
            if (!serverIdFilterDdlEnabled && eventType == LogEvent.QUERY_EVENT) {
                byte[] data = byteArray.getData();
                if (!isBeginQueryEvent(data, offset)) {
                    return false;
                }
            }
            return true;
        }
        int eventLength = byteArray.readInteger(4);
        byteArray.skip(6);
        switch (eventType) {
        case LogEvent.ROWS_QUERY_LOG_EVENT:
            if (archiveIgnoreEnabled) {
                byteArray.skip(1);
                // eventSize减去header长度、checksum的长度4和payload的第一个字节1，便是query_log的字符串的长度
                // readString 是一个较为耗时的操作,因此这里直接比较字节码，以此希望快一些
                int contentLength = eventLength - 24;
                // 必须保证 "ARCHIVE" 字样一定在末尾
                byteArray.skip(contentLength - ARCHIVE_BYTES.length);
                boolean equals = true;
                for (byte b : ARCHIVE_BYTES) {
                    if (byteArray.readByte() != b) {
                        equals = false;
                        break;
                    }
                }
                if (equals) {
                    archiveIgnoring = true;
                    return true;
                }
            }
            return rowsQueryIgnoreEnabled;
        case LogEvent.XID_EVENT:
            archiveIgnoring = false;
            return false;
        case LogEvent.TABLE_MAP_EVENT:
            if (!tableNameSet.isEmpty()) {
                long tableId = byteArray.readLong(BinlogGenerateUtil.getTableIdLength());
                if (!tableIdSet.contains(tableId)) {
                    // 一个TableId仅需处理一次，避免多次读取字符串
                    tableIdSet.add(tableId);
                    // flags
                    byteArray.skip(2);
                    String dbName = byteArray.readString();
                    byteArray.skip(1);
                    String tbName = byteArray.readString();
                    if (tableNameSet.contains(dbName + "." + tbName)) {
                        tableIdIgnoreOrAllowSet.add(tableId);
                    }
                }
                boolean contains = tableIdIgnoreOrAllowSet.contains(tableId);
                if (whiteListMode && !contains) {
                    return true;
                }
                if (!whiteListMode && contains) {
                    return true;
                }
            }
            return archiveIgnoreEnabled && archiveIgnoring;
        case LogEvent.ROTATE_EVENT:
            tableIdSet.clear();
            tableIdIgnoreOrAllowSet.clear();
            return false;
        case LogEvent.WRITE_ROWS_EVENT:
        case LogEvent.UPDATE_ROWS_EVENT:
        case LogEvent.DELETE_ROWS_EVENT:
        case LogEvent.WRITE_ROWS_EVENT_V1:
        case LogEvent.UPDATE_ROWS_EVENT_V1:
        case LogEvent.DELETE_ROWS_EVENT_V1:
            if (tableIdSet.isEmpty()) {
                // 请求的位点在TableMapEvent之后，DML之前，无法判断该DML（启动后最多一个）是否该被过滤，因此不过滤
                return false;
            }
            if (!tableIdIgnoreOrAllowSet.isEmpty() || whiteListMode) {
                long tableId = byteArray.readLong(BinlogGenerateUtil.getTableIdLength());
                boolean contains = tableIdIgnoreOrAllowSet.contains(tableId);
                if (whiteListMode && !contains) {
                    return true;
                }
                if (!whiteListMode && contains) {
                    return true;
                }
            }
            return archiveIgnoreEnabled && archiveIgnoring;
        case LogEvent.FORMAT_DESCRIPTION_EVENT:
            // 由于通过flag来过滤event会修改event本身的数据，不能让下游进行checksum，否则必定失败
            // 当然就算让下游不进行checksum，下游也会把最后四个字节当成payload处理，同样报错
            if (ignoreBySetFlag) {
                byteArray.writeByte(
                    offset + eventLength - LogEvent.BINLOG_CHECKSUM_LEN - LogEvent.BINLOG_CHECKSUM_ALG_DESC_LEN,
                    (byte) LogEvent.BINLOG_CHECKSUM_ALG_UNDEF);
            }
            return false;
        default:
            return archiveIgnoreEnabled && archiveIgnoring;
        }
    }

    public ByteString getFilteredData(byte[] data, int offset) {
        if (ignoreBySetFlag) {
            // ignore flag
            data[offset + 17] |= (byte) 0x80;
            // 未知事件时，下游才会检查ignore flag
            data[offset + 4] = LogEvent.UNKNOWN_EVENT;
            return UnsafeByteOperations.unsafeWrap(data);
        } else {
            return ByteString.EMPTY;
        }
    }

    private void initIgnoreServerIds(String ids) {
        if (StringUtils.isNotBlank(ids)) {
            for (String token : ids.trim().split(RplConstants.COMMA)) {
                ignoreServerIds.add(Long.parseLong(token.trim()));
            }
        }
    }

    /**
     * 判断 QUERY_EVENT 是否为 BEGIN（事务开始），与 LogEventUtil.isStart() 同一思路
     * CDC 的 BEGIN 由 EventGenerator.makeBegin() 写入，结构固定：
     * post-header: thread_id(4) + exec_time(4) + db_len(1) + error_code(2) + status_vars_len(2)
     * 然后: status_vars(status_vars_len bytes) + db(db_len bytes) + null(1) + query
     */
    private boolean isBeginQueryEvent(byte[] data, int offset) {
        // post-header starts at offset+19 (after common header)
        int dbLen = data[offset + 19 + 8] & 0xFF;
        int statusVarsLen = (data[offset + 19 + 11] & 0xFF) | ((data[offset + 19 + 12] & 0xFF) << 8);
        int queryOffset = offset + 19 + 13 + statusVarsLen + dbLen + 1;
        if (queryOffset + BEGIN_BYTES.length > data.length) {
            return false;
        }
        for (int i = 0; i < BEGIN_BYTES.length; i++) {
            if (data[queryOffset + i] != BEGIN_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    public String getFilterInfo() {
        StringBuilder stringBuffer = new StringBuilder();
        if (rowsQueryIgnoreEnabled) {
            stringBuffer.append("ROWS_QUERY ");
        }
        if (archiveIgnoreEnabled) {
            stringBuffer.append("ARCHIVE ");
        }
        if (serverIdIgnoreEnabled && !ignoreServerIds.isEmpty()) {
            stringBuffer.append("IGNORE_SERVER_IDS: (");
            for (Long id : ignoreServerIds) {
                stringBuffer.append(id).append(",");
            }
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
            stringBuffer.append(") ");
        }
        if (!tableNameSet.isEmpty()) {
            if (whiteListMode) {
                stringBuffer.append("DO_TABLE: (");
            } else {
                stringBuffer.append("IGNORE_TABLE: (");
            }
            for (String tableName : tableNameSet) {
                stringBuffer.append(tableName).append(",");
            }
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
            stringBuffer.append(") ");
        }
        return stringBuffer.toString();
    }
}
