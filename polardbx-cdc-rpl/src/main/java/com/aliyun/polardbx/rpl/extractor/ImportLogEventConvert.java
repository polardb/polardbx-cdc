/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.sql.Types;
import java.util.BitSet;
import java.util.List;

/**
 * 基于{@linkplain LogEvent}转化为Entry对象的处理
 *
 * @author jianghang 2013-1-17 下午02:41:14
 * @version 1.0.0
 */
@Slf4j
public class ImportLogEventConvert extends LogEventConvert {

    private long nowLogicServerId = 0L;

    public ImportLogEventConvert(HostInfo metaHostInfo, BaseFilter filter, BinlogPosition startBinlogPosition,
                                 HostType srcHostType) {
        super(metaHostInfo, filter, startBinlogPosition, srcHostType);
    }

    @Override
    public MySQLDBMSEvent parse(LogEvent logEvent, boolean isSeek) throws Exception {
        if (logEvent == null || logEvent instanceof UnknownLogEvent) {
            return null;
        }

        int DBMSAction = logEvent.getHeader().getType();
        switch (DBMSAction) {
        // canal 解析 binlog 时，传入的 context (LogContext 类型)，其成员 FormatDescriptionLogEvent
        // formatDescription 是默认 binlog 不开启 CRC32 校验的（FormatDescriptionLogEvent 的
        // checksumAlg 字段）。
        // 而解析 binlog 时会用到 checksumAlg 值（参考 LogDecoder 第 64 行）。
        // 一个 binlog 文件，第一个事件为 ROTATE_EVENT，第二个为 FORMAT_DESCRIPTION_EVENT。
        // 一个 binlog 文件，只有 ROTATE_EVENT 带有 fileName 信息。
        // ROTATE_EVENT 不包含 checksumAlg 信息。FORMAT_DESCRIPTION_EVENT 包含 checksumAlg 信息。
        // 故如果 binlog 开启了 CRC32，那么：
        // canal 解析出来的 ROTATE_EVENT 中的 fileName 是错误的，它将 CRC32
        // 校验值的 4 个字节当成了 fileName 的一部分（参考 LogDecoder 第 64 行）。
        // canal 在解析完 FORMAT_DESCRIPTION_EVENT 后，会解析出正确的 checksumAlg。同时将 context 中的
        // formatDescription 字段替换成正确的。
        // 故如果 binlog 开启了 CRC32，那么解析出来的 binlogFileName 是错误的，而后续解析出的 event 数据却是对的。
        // 但是如果 binlog 文件发生了切换，那么第二个 binlog 文件被解析出来的 binlogFileName 则是对的。
        case LogEvent.ROTATE_EVENT:
            log.info("received ROTATE_EVENT, binlogFileName: {}", ((RotateLogEvent) logEvent).getFilename());
            if (!firstBinlogFile) {
                binlogFileName = ((RotateLogEvent) logEvent).getFilename();
            }
            firstBinlogFile = false;
            break;
        case LogEvent.FORMAT_DESCRIPTION_EVENT:
            break;
        case LogEvent.QUERY_EVENT:
            return parseQueryEvent((QueryLogEvent) logEvent, isSeek);
        case LogEvent.XID_EVENT:
            return parseXidEvent((XidLogEvent) logEvent);
        case LogEvent.TABLE_MAP_EVENT:
            break;
        case LogEvent.WRITE_ROWS_EVENT_V1:
        case LogEvent.WRITE_ROWS_EVENT:
            return parseRowsEvent((WriteRowsLogEvent) logEvent, isSeek);
        case LogEvent.UPDATE_ROWS_EVENT_V1:
        case LogEvent.UPDATE_ROWS_EVENT:
            return parseRowsEvent((UpdateRowsLogEvent) logEvent, isSeek);
        case LogEvent.DELETE_ROWS_EVENT_V1:
        case LogEvent.DELETE_ROWS_EVENT:
            return parseRowsEvent((DeleteRowsLogEvent) logEvent, isSeek);
        case LogEvent.ROWS_QUERY_LOG_EVENT:
            nowLogicServerId = LogEventUtil.getServerIdFromRowQuery((RowsQueryLogEvent) logEvent);
            return parseRowsQueryEvent((RowsQueryLogEvent) logEvent);
        case LogEvent.ANNOTATE_ROWS_EVENT:
            return parseAnnotateRowsEvent((AnnotateRowsEvent) logEvent);
        case LogEvent.USER_VAR_EVENT:
            return parseUserVarLogEvent((UserVarLogEvent) logEvent);
        case LogEvent.INTVAR_EVENT:
            return parseIntrvarLogEvent((IntvarLogEvent) logEvent);
        case LogEvent.RAND_EVENT:
            return parseRandLogEvent((RandLogEvent) logEvent);
        case LogEvent.HEARTBEAT_LOG_EVENT:
            return null;
        default:
            break;
        }

        return null;
    }

    @Override
    protected MySQLDBMSEvent parseQueryEvent(QueryLogEvent event, boolean isSeek) {
        String queryString = event.getQuery();
        // 普通事务
        // 由于trace带来的server id会晚于begin & xa start 因此不过滤begin
        // 这样可能导致多余的事务控制信息
        if (StringUtils.endsWithIgnoreCase(queryString, BEGIN)) {
            DBMSTransactionBegin transactionBegin = createTransactionBegin(event.getSessionId());
            // 清除rows query log 带来的server id in trace
            nowLogicServerId = 0L;
            return new MySQLDBMSEvent(transactionBegin, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
        } else if (StringUtils.endsWithIgnoreCase(queryString, COMMIT)) {
            DBMSTransactionEnd transactionEnd = createTransactionEnd(0L);
            return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
        } else {
            // XA事务
            DBMSXATransaction xaTransaction = getXaTransaction(queryString);
            if (xaTransaction != null) {
                // 清除rows query log 带来的server id in trace
                nowLogicServerId = 0L;
                String rewriteDbName = filter.getRewriteDb(event.getDbName(), null);
                DefaultQueryLog queryEvent = new DefaultQueryLog(rewriteDbName,
                    queryString,
                    new java.sql.Timestamp(event.getHeader().getWhen() * 1000),
                    event.getErrorCode(), 0, DBMSAction.QUERY, event.getExecTime());
                MySQLDBMSEvent mySQLDBMSEvent =
                    new MySQLDBMSEvent(queryEvent, createPosition(event.getHeader()), event.getHeader().getEventLen());
                mySQLDBMSEvent.setXaTransaction(xaTransaction);
                return mySQLDBMSEvent;
            } else {
                // ddl 不处理
            }
        }
        return null;
    }

    @Override
    protected MySQLDBMSEvent parseRowsQueryEvent(RowsQueryLogEvent event) {
        String queryString;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildRowsQueryEntry(queryString, event.getHeader(), DBMSAction.ROWQUERY);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    @Override
    protected MySQLDBMSEvent parseRowsEvent(RowsLogEvent event, boolean isSeek) {
        try {
            TableMapLogEvent table = event.getTable();
            if (table == null) {
                // tableId对应的记录不存在
                throw new TableIdNotFoundException("not found tableId:" + event.getTableId());
            }

            DBMSAction action = null;
            int type = event.getHeader().getType();
            if (LogEvent.WRITE_ROWS_EVENT_V1 == type || LogEvent.WRITE_ROWS_EVENT == type) {
                action = DBMSAction.INSERT;
            } else if (LogEvent.UPDATE_ROWS_EVENT_V1 == type || LogEvent.UPDATE_ROWS_EVENT == type) {
                action = DBMSAction.UPDATE;
            } else if (LogEvent.DELETE_ROWS_EVENT_V1 == type || LogEvent.DELETE_ROWS_EVENT == type) {
                action = DBMSAction.DELETE;
            } else {
                throw new CanalParseException("unsupport event type :" + event.getHeader().getType());
            }

            // 根据 filter 的 rewriteDbs 重写 DbName
            String rewriteDbName = filter.getRewriteDb(table.getDbName(), action);
            String rewriteTableName = filter.getRewriteTable(table.getDbName(), table.getTableName());

            // 过滤条件
            // 为有效逻辑server id
            if (nowLogicServerId != 0L) {
                if (filter.ignoreEvent(table.getDbName(), table.getTableName(), action, nowLogicServerId)) {
                    return null;
                }
            } else {
                if (filter.ignoreEvent(table.getDbName(), table.getTableName(), action, event.getServerId())) {
                    return null;
                }
            }

            if (isRDSHeartBeat(rewriteDbName, table.getTableName())) {
                return null;
            }

            BinlogPosition position = createPosition(event.getHeader());

            // 如果是查找模式，那么不进行行数据的解析
            if (isSeek) {
                DefaultRowChange rowChange = new DefaultRowChange(action,
                    rewriteDbName,
                    table.getTableName(),
                    new DefaultColumnSet(Lists.newArrayList()));
                return new MySQLDBMSEvent(rowChange, position, event.getHeader().getEventLen());
            }

            RowsLogBuffer buffer = event.getRowsBuf(charset);
            BitSet columns = event.getColumns();
            BitSet changeColumns = event.getChangeColumns(); // 非变更的列,而是指存在binlog记录的列,mysql full image模式会提供所有列
            boolean tableError = false;
            TableMeta tableMeta = getTableMeta(table.getDbName(), table.getTableName());
            if (tableMeta == null) {
                tableError = true;
                if (!filterTableError) {
                    throw new CanalParseException(
                        "not found [" + table.getDbName() + "." + table.getTableName() + "] in db , pls check!");
                }
            }

            // check table fileds count，只能处理加字段
            int columnSize = event.getTable().getColumnCnt();

            // 构造列信息
            List<DBMSColumn> dbmsColumns = Lists.newArrayList();
            List<FieldMeta> fieldMetas = tableMeta.getFields();
            // 兼容一下canal的逻辑,认为DDL新增列都加在末尾,如果表结构的列比binlog的要多
            int size = fieldMetas.size();
            if (columnSize < size) {
                size = columnSize;
            }
            for (int i = 0; i < size; i++) {
                FieldMeta fieldMeta = fieldMetas.get(i);
                // 先临时加一个sqlType=0的值
                DefaultColumn column = new DefaultColumn(fieldMeta.getColumnName(),
                    i,
                    Types.OTHER,
                    fieldMeta.isUnsigned(),
                    fieldMeta.isNullable(),
                    fieldMeta.isKey(),
                    fieldMeta.isUnique(),
                    fieldMeta.isGenerated(),
                    fieldMeta.isImplicitPk(),
                    fieldMeta.isOnUpdate()
                );
                dbmsColumns.add(column);
            }

            DefaultRowChange rowChange = new DefaultRowChange(action,
                rewriteDbName,
                rewriteTableName,
                new DefaultColumnSet(dbmsColumns));
            rowChange.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_SCHEMA, table.getDbName()));
            rowChange.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_SOURCE_TABLE, table.getTableName()));

            // 需要处理到update类型时，基于数据内容进行判定
            BitSet actualChangeColumns = new BitSet(columnSize);
            rowChange.setChangeColumnsBitSet(actualChangeColumns);

            while (buffer.nextOneRow(columns)) {
                // 处理row记录
                if (DBMSAction.INSERT == action) {
                    // insert的记录放在before字段中
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else if (DBMSAction.DELETE == action) {
                    // delete的记录放在before字段中
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                } else {
                    // update需要处理before/after
                    parseOneRow(rowChange, event, buffer, columns, false, tableMeta, actualChangeColumns);
                    if (!buffer.nextOneRow(changeColumns)) {
                        break;
                    }

                    parseOneRow(rowChange, event, buffer, changeColumns, true, tableMeta, actualChangeColumns);
                }
            }

            MySQLDBMSEvent dbmsEvent = new MySQLDBMSEvent(rowChange, position, event.getHeader().getEventLen());
            if (tableError) {
                log.warn("table parser error : " + rowChange.toString());
                return null;
            } else {
                return dbmsEvent;
            }
        } catch (Exception e) {
            throw new CanalParseException("parse row data failed.", e);
        }
    }
}
