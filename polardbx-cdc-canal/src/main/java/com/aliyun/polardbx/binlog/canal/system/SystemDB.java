/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.system;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;

public class SystemDB {

    private static final ISystemDBProvider provider;

    static {
        provider = SpringContextHolder.getObject(ISystemDBProvider.class);
    }

    public static boolean isDrdsImplicitId(String colName) {
        return provider.isDrdsImplicitId(colName);
    }

    public static boolean isSys(String db) {
        return provider.isSys(db);
    }

    public static boolean isMysql(String db) {
        return provider.isMysql(db);
    }

    public static boolean isMetaDb(String db) {
        return provider.isMetaDb(db);
    }

    public static boolean isInstruction(String db, String table) {
        return provider.instructionTable(db, table);
    }

    public static boolean isLogicDDL(String db, String table) {
        return provider.ddlRecordTable(db, table);
    }

    public static boolean isCdcSingleGroup(String groupName) {
        return provider.isCdcSingleGroup(groupName);
    }

    public static boolean isGlobalTxTable(String tableName) {
        return provider.isGlobalTxTable(tableName);
    }

    public static boolean isHeartbeat(String db, String phyTable) {
        return provider.heartbeatTable(db, phyTable);
    }

    public static boolean isSyncPoint(String db, String phyTable) {
        return provider.isSyncPoint(db, phyTable);
    }

    public static TableMeta getDdlTableMeta() {
        return provider.getDdlTableMeta();
    }

    public static TableMeta getInstructionTableMeta() {
        return provider.getInstructionTableMeta();
    }

    public static TableMeta getSyncPointTableMeta() {
        return provider.getSyncPointTableMeta();
    }

    public static TableMeta getHeartbeatTableMeta() {
        return provider.getHeartbeatTableMeta();
    }

    public static InstructionCommand parseInstructionCommand(WriteRowsLogEvent wr) {
        return provider.parseInstructionCommand(wr);
    }

    public static TxGlobalEvent parseTxGlobalEvent(WriteRowsLogEvent rowsLogEvent, String charset) {
        return provider.parseTxGlobalEvent(rowsLogEvent, charset);
    }

    public static boolean isPolarxGlobalTrxLogTable(String tableName) {
        return provider.isPolarxGlobalTrxLogTable(tableName);
    }

    public static boolean isDrdsRedoLogTable(String tableName) {
        return provider.isDrdsRedoLogTable(tableName);
    }

    public static boolean isAndorDatabase(String db) {
        return provider.isAndorDatabase(db);
    }
}
