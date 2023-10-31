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

    public static TableMeta getDdlTableMeta() {
        return provider.getDdlTableMeta();
    }

    public static TableMeta getInstructionTableMeta() {
        return provider.getInstructionTableMeta();
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
}
