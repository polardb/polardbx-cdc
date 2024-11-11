/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.XATransactionType;
import com.aliyun.polardbx.binlog.canal.binlog.event.DeleteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.IntvarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RandLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent.ColumnInfo;
import com.aliyun.polardbx.binlog.canal.binlog.event.UnknownLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UserVarLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.WriteRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMetaCache;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_PARSE_ERROR_PROCESS_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_FILTER_TABLE_ERROR;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.util.CommonUtils.extractPolarxOriginSql;

/**
 * 基于{@linkplain LogEvent}转化为Entry对象的处理
 *
 * @author jianghang 2013-1-17 下午02:41:14
 * @version 1.0.0
 */
@Slf4j
public class LogEventConvert {

    protected static final Logger extractorLogger = LoggerFactory.getLogger("rplExtractorLogger");
    public static final String ISO_8859_1 = "ISO-8859-1";
    public static final int TINYINT_MAX_VALUE = 256;
    public static final int SMALLINT_MAX_VALUE = 65536;
    public static final int MEDIUMINT_MAX_VALUE = 16777216;
    public static final long INTEGER_MAX_VALUE = 4294967296L;
    public static final BigInteger BIGINT_MAX_VALUE = new BigInteger("18446744073709551616");
    public static final int version = 1;
    public static final String BEGIN = "BEGIN";
    public static final String COMMIT = "COMMIT";

    protected static final String MYSQL = "mysql";
    protected static final String HA_HEALTH_CHECK = "ha_health_check";
    protected static final String DRDS_SYSTEM_MYSQL_HEARTBEAT = "__drds__system__mysql__heartbeat__";

    protected TableMetaCache tableMetaCache;

    protected String binlogFileName = "";
    protected String charset = "utf8";
    protected boolean filterQueryDml = true;
    protected boolean filterRowsQuery = false;
    // 是否跳过table相关的解析异常,比如表不存在或者列数量不匹配,issue 92
    protected boolean filterTableError = getBoolean(RPL_FILTER_TABLE_ERROR);
    protected boolean firstBinlogFile = true;
    protected HostType srcHostType;
    protected HostInfo metaHostInfo;
    protected BaseFilter filter;

    protected FieldMeta rdsImplicitIDFieldMeta;
    protected TableMeta rdsHeartBeatTableMeta;
    protected String nowTso;
    protected boolean enableSrcLogicalMetaSnapshot;

    public LogEventConvert(HostInfo metaHostInfo, BaseFilter filter, BinlogPosition startBinlogPosition,
                           HostType srcHostType) {
        this.metaHostInfo = metaHostInfo;
        this.filter = filter;
        this.binlogFileName = startBinlogPosition.getFileName();
        this.srcHostType = srcHostType;
        this.enableSrcLogicalMetaSnapshot = false;
    }

    public LogEventConvert(HostInfo metaHostInfo, BaseFilter filter, BinlogPosition startBinlogPosition,
                           HostType srcHostType, boolean enableSrcLogicalMetaSnapshot) {
        this.metaHostInfo = metaHostInfo;
        this.filter = filter;
        this.binlogFileName = startBinlogPosition.getFileName();
        this.srcHostType = srcHostType;
        this.enableSrcLogicalMetaSnapshot = enableSrcLogicalMetaSnapshot;
    }

    public static DBMSTransactionBegin createTransactionBegin(long threadId) {
        DBMSTransactionBegin transactionBegin = new DBMSTransactionBegin();
        transactionBegin.setThreadId(threadId);
        return transactionBegin;
    }

    public static DBMSTransactionEnd createTransactionEnd(long transactionId) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(transactionId);
        return transactionEnd;
    }

    public void refreshState() {
        firstBinlogFile = true;
    }

    public void init() throws Exception {
        this.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        DataSource dataSource = DataSourceUtil.createDruidMySqlDataSource(metaHostInfo.isUsePolarxPoolCN(),
            metaHostInfo.getHost(),
            metaHostInfo.getPort(),
            "",
            metaHostInfo.getUserName(),
            metaHostInfo.getPassword(),
            "",
            1,
            2,
            true,
            null,
            null);
        this.tableMetaCache = new TableMetaCache(dataSource,
            srcHostType == HostType.POLARX2 && enableSrcLogicalMetaSnapshot);
        initMeta();
    }

    protected void initMeta() {
        rdsImplicitIDFieldMeta =
            new FieldMeta(RplConstants.RDS_IMPLICIT_ID, "long", false, true, null, true);

        // 处理rds模式的mysql.ha_health_check心跳数据
        // 主要RDS的心跳表基本无权限,需要mock一个tableMeta
        FieldMeta idMeta = new FieldMeta("id", "bigint(20)", true, false, "0", false);
        FieldMeta typeMeta = new FieldMeta("type", "char(1)", false, true, "0", false);// type为主键
        rdsHeartBeatTableMeta = new TableMeta(MYSQL, HA_HEALTH_CHECK, Arrays.asList(idMeta, typeMeta));
    }

    public String getBinlogFileName() {
        return binlogFileName;
    }

    public void setBinlogFileName(String binlogFileName) {
        this.binlogFileName = binlogFileName;
    }

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
            if (!firstBinlogFile) {
                binlogFileName = ((RotateLogEvent) logEvent).getFilename();
            }
            break;
        case LogEvent.FORMAT_DESCRIPTION_EVENT:
            firstBinlogFile = false;
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
            String thisTso = LogEventUtil.getTsoFromRowQuery(((RowsQueryLogEvent) logEvent).getRowsQuery());
            // nowTso 仅能从 DDL / 心跳 / 事务内的第一个rows query log获取
            // 同事务内后续的 rows query log 无 tso
            // 因此此处要判空，防止后续dml没有正确的 tso
            // nowTso 仅在事务开头和事务结尾清除
            if (thisTso != null) {
                nowTso = thisTso;
            }
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

    public void reset() {
        tableMetaCache.reset();
    }

    protected MySQLDBMSEvent parseQueryEvent(QueryLogEvent event, boolean isSeek) {
        String queryString = event.getQuery();
        if (StringUtils.endsWithIgnoreCase(queryString, BEGIN)) {
            // 此处还拿不到该事务真正的tso，因此直接置空
            nowTso = null;
            DBMSTransactionBegin transactionBegin = createTransactionBegin(event.getSessionId());
            return new MySQLDBMSEvent(transactionBegin, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
        } else if (StringUtils.endsWithIgnoreCase(queryString, COMMIT)) {
            DBMSTransactionEnd transactionEnd = createTransactionEnd(0L);
            // 先获取nowTso再置空，确保下游的position中含有tso
            MySQLDBMSEvent sqlDBMSEvent = new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()),
                event.getHeader().getEventLen());
            nowTso = null;
            return sqlDBMSEvent;
        } else {
            // 先提取TSO: # POLARX_TSO=，并尝试过滤
            String tso = CommonUtils.extractPrivateTSOFromDDL(queryString);
            if (StringUtils.isNotBlank(tso)) {
                nowTso = tso;
            }
            if (filter.ignoreEventByTso(nowTso)) {
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                    extractorLogger.warn("ignoreDdlByTSO: " + nowTso + ":[" + event.getDbName() + "]:["
                        + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
                }

                return null;
            }

            // 尝试提取私有DDL，# POLARX_ORIGIN_SQL=
            String originSql = extractPolarxOriginSql(queryString);
            originSql = StringUtils.isNotBlank(originSql) ? originSql : queryString;

            // 如果上游是polardbx，则不能过滤，正常同步
            if (StringUtils.isBlank(tso) && (StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "flush")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "grant")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "create user")
                || StringUtils.startsWithIgnoreCase(StringUtils.trim(originSql), "drop user"))) {
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                    extractorLogger.warn("ignoreDdlByEmptyTSO: " + nowTso + ":[" + event.getDbName() + "]:["
                        + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
                }
                return null;
            }

            // 尝试进行解析
            int mode = getInt(RPL_DDL_PARSE_ERROR_PROCESS_MODE);
            DdlResult ddlResult = null;
            try {
                ddlResult = DruidDdlParser.parse(originSql, event.getDbName());
            } catch (Exception e) {
                if (mode == 1) {
                    log.warn("skip ddl sql because of parsing error, sql {} , tso {}.", originSql, tso);
                    if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                        extractorLogger.warn("ignoreDdlByParsingError: " + nowTso + ":[" + event.getDbName() + "]:["
                            + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
                    }
                    return null;
                } else if (mode == 2) {
                    log.error("ignore ddl sql because of parsing error, sql {} , tso {}.", originSql, tso);
                    if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                        extractorLogger.warn("ignoreDdlByParsingError: " + nowTso + ":[" + event.getDbName() + "]:["
                            + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
                    }

                } else {
                    log.error("parse ddl sql error, sql {}, tso {}.", originSql, tso, e);
                    throw e;
                }
            }
            DBMSAction action = ddlResult == null ? DBMSAction.OTHER : ddlResult.getType();
            String rewriteDbName = filter.getRewriteDb(event.getDbName(), action);
            if (filter.ignoreEvent(rewriteDbName,
                (ddlResult == null || ddlResult.getTableName() == null) ? "" : ddlResult.getTableName(),
                action, event.getServerId())) {
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                    extractorLogger.warn("ignoreDdlByFilter: " + nowTso + ":[" + event.getDbName() + "]:["
                        + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
                }
                return null;
            }

            // 跨库 ddl，构造 DDL 事件时，要使用其真正的 db，即 result.getSchemaName()
            // 但polardbx不需要
            if (StringUtils.isBlank(tso) && ddlResult != null && StringUtils.isNotBlank(ddlResult.getSchemaName())
                && !StringUtils.equalsIgnoreCase(event.getDbName(), ddlResult.getSchemaName())) {
                rewriteDbName = filter.getRewriteDb(ddlResult.getSchemaName(), action);
            }

            // 更新内存 tableMetaCache
            TableMeta preVersionTableMeta = null;
            if (ddlResult != null) {
                if (StringUtils.isNotBlank(ddlResult.getTableName())) {
                    preVersionTableMeta = tableMetaCache.getTableMetaIfPresent(rewriteDbName, ddlResult.getTableName());
                }
                BinlogPosition position = createPosition(event.getHeader());
                tableMetaCache.apply(position, rewriteDbName, originSql);
            }

            // 构造对象
            DefaultQueryLog queryEvent = new DefaultQueryLog(rewriteDbName,
                queryString,
                new java.sql.Timestamp(event.getHeader().getWhen() * 1000),
                event.getErrorCode(),
                event.getSqlMode(),
                action,
                event.getExecTime());
            queryEvent.setTableMeta(preVersionTableMeta);
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_EXTRACTOR_DDL_LOG_OPEN)) {
                extractorLogger.warn("recordDdl : " + nowTso + ":[" + event.getDbName() + "]:["
                    + binlogFileName + ":" + event.getLogPos() + "]," + queryString);
            }

            return new MySQLDBMSEvent(queryEvent, createPosition(event.getHeader()), event.getHeader().getEventLen());
        }
    }

    protected MySQLDBMSEvent parseRowsQueryEvent(RowsQueryLogEvent event) {
        if (filterQueryDml && filterRowsQuery) {
            return null;
        }
        // mysql5.6支持，需要设置binlog-rows-query-log-events=1，可详细打印原始DML语句
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildRowsQueryEntry(queryString, event.getHeader(), DBMSAction.ROWQUERY);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseAnnotateRowsEvent(AnnotateRowsEvent event) {
        if (filterQueryDml) {
            return null;
        }
        // mariaDb支持，需要设置binlog_annotate_row_events=true，可详细打印原始DML语句
        String queryString = null;
        try {
            queryString = new String(event.getRowsQuery().getBytes(ISO_8859_1), charset);
            return buildQueryEntry(queryString, event.getHeader(), null);
        } catch (UnsupportedEncodingException e) {
            throw new CanalParseException(e);
        }
    }

    protected MySQLDBMSEvent parseUserVarLogEvent(UserVarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseIntrvarLogEvent(IntvarLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseRandLogEvent(RandLogEvent event) {
        if (filterQueryDml) {
            return null;
        }

        return buildQueryEntry(event.getQuery(), event.getHeader(), null);
    }

    protected MySQLDBMSEvent parseXidEvent(XidLogEvent event) {
        DBMSTransactionEnd transactionEnd = new DBMSTransactionEnd();
        transactionEnd.setTransactionId(event.getXid());
        return new MySQLDBMSEvent(transactionEnd, createPosition(event.getHeader()), event.getHeader().getEventLen());
    }

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
                throw new CanalParseException("unsupported event type :" + event.getHeader().getType());
            }

            // 根据 filter 的 rewriteDbs 重写 DbName
            String rewriteDbName = filter.getRewriteDb(table.getDbName(), action);

            // 过滤条件
            if (filter.ignoreEvent(rewriteDbName, table.getTableName(), action, event.getServerId())) {
                return null;
            }
            if (filter.ignoreEventByTso(nowTso)) {
                return null;
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
            TableMeta tableMeta = getTableMeta(rewriteDbName, table.getTableName());
            if (tableMeta == null) {
                if (filterTableError) {
                    return null;
                } else {
                    throw new CanalParseException(
                        "not found [" + rewriteDbName + "." + table.getTableName() + "] in db , pls check!");
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
                table.getTableName(),
                new DefaultColumnSet(dbmsColumns));
            BitSet actualChangeColumns = new BitSet(columnSize); // 需要处理到update类型时，基于数据内容进行判定
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

    protected void parseOneRow(DefaultRowChange rowChange, RowsLogEvent event, RowsLogBuffer buffer, BitSet cols,
                               boolean isAfter, TableMeta tableMeta,
                               BitSet actualChangeColumns) throws UnsupportedEncodingException {
        int columnCnt = event.getTable().getColumnCnt();
        ColumnInfo[] columnInfo = event.getTable().getColumnInfo();

        DefaultRowData rowData = new DefaultRowData(columnCnt);
        for (int i = 0; i < columnCnt; i++) {
            ColumnInfo info = columnInfo[i];
            // mysql 5.6开始支持nolob/mininal类型,并不一定记录所有的列,需要进行判断
            if (!cols.get(i)) {
                continue;
            }

            FieldMeta fieldMeta = tableMeta.getFields().get(i);
            // 优先使用列的 charset, 如果列没有则使用表的 charset, 表一定有 charset
            String charset = StringUtils.isNotBlank(fieldMeta.getCharset()) ? fieldMeta
                .getCharset() : tableMeta.getCharset();
            String javaCharset = CharsetConversion.getJavaCharset(charset);
            buffer.nextValue(info.type, info.meta, fieldMeta.isBinary(), javaCharset);

            int javaType = buffer.getJavaType();
            Serializable dataValue = null;
            if (!buffer.isNull()) {
                final Serializable value = buffer.getValue();
                // 处理各种类型
                switch (javaType) {
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.BIGINT:
                    // 处理unsigned类型
                    Number number = (Number) value;
                    if (fieldMeta.isUnsigned() && number.longValue() < 0) {
                        switch (buffer.getLength()) {
                        case 1: /* MYSQL_TYPE_TINY */
                            dataValue = TINYINT_MAX_VALUE + number.intValue();
                            javaType = Types.SMALLINT; // 往上加一个量级
                            break;

                        case 2: /* MYSQL_TYPE_SHORT */
                            dataValue = SMALLINT_MAX_VALUE + number.intValue();
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 3: /* MYSQL_TYPE_INT24 */
                            dataValue = MEDIUMINT_MAX_VALUE + number.intValue();
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 4: /* MYSQL_TYPE_LONG */
                            dataValue = INTEGER_MAX_VALUE + number.longValue();
                            javaType = Types.BIGINT; // 往上加一个量级
                            break;

                        case 8: /* MYSQL_TYPE_LONGLONG */
                            dataValue = BIGINT_MAX_VALUE.add(BigInteger.valueOf(number.longValue()));
                            javaType = Types.DECIMAL; // 往上加一个量级，避免执行出错
                            break;
                        default:
                            break;
                        }
                    } else {
                        // 对象为number类型，直接valueof即可
                        dataValue = value;
                    }
                    break;
                case Types.REAL: // float
                case Types.DOUBLE: // double
                    // 对象为number类型，直接valueof即可
                    dataValue = String.valueOf(value);
                    break;
                case Types.BIT:// bit
                    // 对象为byte[]类型,不能直接转为字符串,入库的时候会有问题
                    dataValue = value;
                    break;
                case Types.DECIMAL:
                    dataValue = ((BigDecimal) value).toPlainString();
                    break;
                case Types.TIMESTAMP:
                    // 修复时间边界值
                    // String v = value.toString();
                    // v = v.substring(0, v.length() - 2);
                    // columnBuilder.setValue(v);
                    // break;
                case Types.TIME:
                case Types.DATE:
                    // 需要处理year
                    dataValue = value.toString();
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    // fixed text encoding
                    // https://github.com/AlibabaTech/canal/issues/18
                    // mysql binlog中blob/text都处理为blob类型，需要反查table
                    // meta，按编码解析text
                    if (fieldMeta != null && isText(fieldMeta.getColumnType())) {
                        dataValue = new String((byte[]) value, javaCharset);
                        javaType = Types.CLOB;
                    } else {
                        // byte数组，直接使用iso-8859-1保留对应编码，浪费内存
                        dataValue = (byte[]) value;
                        javaType = Types.BLOB;
                    }
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                    if (value == null) {
                        dataValue = null;
                    } else {
                        dataValue = value.toString();
                    }
                    break;
                default:
                    dataValue = value.toString();
                }
            }

            // 下标从1开始
            rowData.setRowValue(i + 1, dataValue);
            // 处理一下sqlType
            DBMSColumn dbmsColumn = rowChange.getColumns().get(i);
            if (dbmsColumn instanceof DefaultColumn && dbmsColumn.getSqlType() == Types.OTHER) {
                ((DefaultColumn) dbmsColumn).setSqlType(javaType);
            }
        }

        if (isAfter) {
            rowChange.addChangeData(rowData);
            // 处理一下变更列
            DBMSRowData beforeRowData = rowChange.getRowData(rowChange.getRowSize());
            buildChangeColumns(beforeRowData, rowData, columnCnt, actualChangeColumns);
        } else {
            rowChange.addRowData(rowData);
        }
    }

    protected void buildChangeColumns(DBMSRowData beforeRowData, DBMSRowData afterRowData, int size,
                                      BitSet changeColumns) {
        for (int i = 1; i <= size; i++) {
            Serializable before = beforeRowData.getRowValue(i);
            Serializable after = afterRowData.getRowValue(i);

            boolean check = isUpdate(before, after);
            if (check) {
                changeColumns.set(i - 1, true);
            }
        }

    }

    protected boolean isUpdate(Serializable before, Serializable after) {
        if (before == null && after == null) {
            return false;
        } else if (before != null && after != null && isEqual(before, after)) {
            return false;
        }

        // 比如nolob/minial模式下,可能找不到before记录,认为是有变化
        return true;
    }

    private boolean isEqual(Serializable before, Serializable after) {
        if (before instanceof byte[] && after instanceof byte[]) {
            return Arrays.equals((byte[]) before, (byte[]) after);
        } else {
            return before.equals(after);
        }
    }

    protected MySQLDBMSEvent buildQueryEntry(String queryString, LogHeader logHeader, DBMSAction action) {
        DefaultQueryLog queryLog = new DefaultQueryLog(null,
            queryString,
            new java.sql.Timestamp(logHeader.getWhen() * 1000),
            0,
            0,
            action,
            0);

        return new MySQLDBMSEvent(queryLog, createPosition(logHeader), logHeader.getEventLen());
    }

    protected MySQLDBMSEvent buildRowsQueryEntry(String queryString, LogHeader logHeader, DBMSAction action) {
        DefaultRowsQueryLog queryLog = new DefaultRowsQueryLog(
            queryString,
            action);

        return new MySQLDBMSEvent(queryLog, createPosition(logHeader), logHeader.getEventLen());
    }

    protected BinlogPosition createPosition(LogHeader logHeader) {
        BinlogPosition position = new BinlogPosition(binlogFileName, logHeader.getLogPos(), logHeader.getServerId(),
            logHeader.getWhen()); // 记录到秒
        position.setRtso(nowTso);
        return position;
    }

    protected TableMeta getTableMeta(String dbName, String tbName) {
        try {
            TableMeta tableMeta = tableMetaCache.getTableMeta(dbName, tbName);
            if (tableMeta.getPrimaryFields().isEmpty() && !tableMeta.getUseImplicitPk()) {
                if (srcHostType == HostType.RDS) {
                    // 添加隐藏主键
                    if (log.isDebugEnabled()) {
                        log.info("Add implicit id {} for table {}.{}", RplConstants.RDS_IMPLICIT_ID, dbName, tbName);
                    }
                    tableMeta.addFieldMeta(rdsImplicitIDFieldMeta);
                    tableMeta.setUseImplicitPk(true);
                }
            }
            return tableMeta;
        } catch (Throwable e) {
            String message = ExceptionUtils.getRootCauseMessage(e);
            if (filterTableError) {
                if (StringUtils.containsIgnoreCase(message, "doesn't exist")) {
                    return null;
                }
                if (StringUtils.containsIgnoreCase(message, "Unknown database")) {
                    return null;
                }
                log.error("filter table error, root cause: {}", message);
            }

            throw new CanalParseException(e);
        }
    }

    protected boolean isText(String columnType) {
        return "LONGTEXT".equalsIgnoreCase(columnType) || "MEDIUMTEXT".equalsIgnoreCase(columnType)
            || "TEXT".equalsIgnoreCase(columnType) || "TINYTEXT".equalsIgnoreCase(columnType);
    }

    protected boolean isRDSHeartBeat(String schema, String table) {
        return (MYSQL.equalsIgnoreCase(schema) && HA_HEALTH_CHECK.equalsIgnoreCase(table))
            || DRDS_SYSTEM_MYSQL_HEARTBEAT.equalsIgnoreCase(table) ||
            "__drds__systable__leadership__".equalsIgnoreCase(table);
    }

    protected String getXid(String queryString, XATransactionType type) throws CanalParseException {
        return queryString.substring(type.getName().length()).trim();
    }

    protected DBMSXATransaction getXaTransaction(String queryString) throws CanalParseException {
        DBMSXATransaction xaTransaction = null;
        if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_START.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_START),
                XATransactionType.XA_START);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_END.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_END),
                XATransactionType.XA_END);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_COMMIT.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_COMMIT),
                XATransactionType.XA_COMMIT);
        } else if (StringUtils.startsWithIgnoreCase(queryString, XATransactionType.XA_ROLLBACK.getName())) {
            xaTransaction = new DBMSXATransaction(getXid(queryString, XATransactionType.XA_ROLLBACK),
                XATransactionType.XA_ROLLBACK);
        }

        return xaTransaction;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public boolean rollback(BinlogPosition position) {
        this.nowTso = position.getRtso();
        return tableMetaCache.rollback(position);
    }
}
