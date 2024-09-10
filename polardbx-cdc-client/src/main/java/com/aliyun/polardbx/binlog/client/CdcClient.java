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
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTsoEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.HeartbeatLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.StreamObserverLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.listener.IExceptionHandler;
import com.aliyun.polardbx.binlog.client.meta.ReadonlyTableMeta;
import com.aliyun.polardbx.binlog.client.metrics.CdcClientMetricsManager;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class CdcClient {

    private static final Logger logger = LoggerFactory.getLogger(CdcClient.class);
    private final BinlogParser parser = new BinlogParser();
    DBMSTransactionEnd commitEvent = null;
    DBMSTransactionBegin beginEvent = null;
    private AtomicBoolean started = new AtomicBoolean(false);
    private Thread parseThread;
    private Throwable ex;
    private IEventHandler handle;
    private IExceptionHandler exceptionHandler;
    private DumperDataSource dataSource;
    private ScheduledExecutorService scheduledExecutorService;
    private MetaDbHelper metaDbHelper;
    private LogDecoder decoder;
    private StreamObserverLogFetcher logFetcher;
    private LogContext context;
    private ReadonlyTableMeta memoryTableMeta;
    private ServerCharactorSet serverCharset;
    private BinlogPosition startPosition;
    private long lastPrintSearchLogTimestamp = 0;
    private static final String POLARX_DDL_TSO_PREFIX = "# POLARX_TSO=";
    private static final String POLARX_DDL_ORIGIN_SQL_PREFIX = "# POLARX_ORIGIN_SQL=";

    public static final String PRIVATE_DDL_ENCODE_BASE64 = "# POLARX_ORIGIN_SQL_ENCODE=BASE64";

    public CdcClient(IMetaDBDataSourceProvider provider) {
        metaDbHelper = new MetaDbHelper(provider);

        dataSource = new DumperDataSource(metaDbHelper);
    }

    public void setExceptionHandler(IExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        this.dataSource.setExceptionHandler(exceptionHandler);
    }

    private void innerInit() throws Exception {
        if (handle == null) {
            throw new PolardbxException("not set event handle!");
        }
        dataSource.initCharset();
        serverCharset = dataSource.getServerCharset();
        decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        decoder.setNeedRecordData(false);
        logFetcher = new StreamObserverLogFetcher();
        logFetcher.registerErrorHandle((t) -> {
            if (exceptionHandler != null) {
                exceptionHandler.handle(t);
            }
        });

        context = new LogContext();
        if (needTableMeta()) {
            //binary data no need table meta
            memoryTableMeta = new ReadonlyTableMeta(metaDbHelper);
        }
        context.setServerCharactorSet(serverCharset);
        FormatDescriptionLogEvent fdl = FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x;
        fdl.getHeader().setChecksumAlg(LogEvent.BINLOG_CHECKSUM_ALG_CRC32);
        context.setFormatDescription(fdl);
    }

    public void startAsync(IEventHandler handle) throws Exception {
        this.startAsync(null, null, handle);
    }

    public void setBinaryData() {
        this.parser.setBinaryLog(true);
    }

    /**
     * init start position and fetch  tso for table meta rollback
     */
    private void searchPosition(String binlogFileName, Long filePosition) throws Exception {
        BinlogPosition innerPosition = null;
        if (StringUtils.isNotBlank(binlogFileName) && filePosition != null) {
            innerPosition = new BinlogPosition(binlogFileName, filePosition, -1, -1);
        }

        if (innerPosition == null) {
            innerPosition = dataSource.findStartPosition();
        }

        String searchBinlogFileName = innerPosition.getFileName();
        binlogFileName = searchBinlogFileName;
        long searchPos = innerPosition.getPosition();
        Long realStartPos = null;
        String realFileName = null;
        String rollbackTso = null;
        // 当 搜索到指定搜搜位点后， 需要锁定变更
        boolean lockPos = false;
        // search tso 标记，如果当前位点是 binlog.0001:4 ， 继续向后搜索第一个TSO即可
        boolean searchTSO = false;
        logger.info("search real pos for position : " + searchBinlogFileName + ":" + searchPos);
        while (searchBinlogFileName != null) {
            BinlogPosition searchPosition = new BinlogPosition(searchBinlogFileName, 4, -1, -1);
            LogContext logContext = new LogContext();
            logContext.setLogPosition(searchPosition);
            logContext.setServerCharactorSet(dataSource.getServerCharset());
            dataSource.reConnect();
            StreamObserverLogFetcher fetcher = new StreamObserverLogFetcher();
            dataSource.dump(searchPosition, fetcher);

            int processEvent = 0;

            while (fetcher.fetch()) {
                LogEvent event = decoder.decode(fetcher, logContext);
                if (event == null) {
                    continue;
                }
                long logPos = event.getLogPos();
                if (LogEventUtil.isStart(event) || LogEventUtil.isCommit(event)) {
                    if (!lockPos) {
                        realStartPos = logPos;
                        realFileName = searchBinlogFileName;
                    }
                    processEvent++;
                }

                if (event instanceof QueryLogEvent) {
                    if (!lockPos) {
                        realStartPos = logPos;
                        realFileName = searchBinlogFileName;
                    }
                    QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                    String queryLog = queryLogEvent.getQuery();
                    Scanner scanner = new Scanner(queryLog);
                    while (scanner.hasNext()) {
                        String query = scanner.nextLine();
                        if (query.startsWith(POLARX_DDL_TSO_PREFIX)) {
                            rollbackTso = query.substring(POLARX_DDL_TSO_PREFIX.length());
                        }
                    }
                    processEvent++;

                }

                if (event instanceof RowsQueryLogEvent) {
                    RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) event;
                    String rowsQuery = rowsQueryLogEvent.getRowsQuery();
                    if (!lockPos && rowsQuery.startsWith("CTS::")) {
                        realStartPos = logPos;
                        realFileName = searchBinlogFileName;
                    }
                    Scanner scanner = new Scanner(rowsQuery);
                    while (scanner.hasNext()) {
                        String line = scanner.nextLine();
                        String[] cmds = line.split("::");
                        for (int i = 0; i < cmds.length; i++) {
                            if (StringUtils.equals(cmds[i], "CTS")) {
                                rollbackTso = cmds[i + 1];
                                break;
                            }
                        }
                    }
                    processEvent++;

                }

                String currentFileName = logContext.getLogPosition().getFileName();

                if (!lockPos && binlogFileName.equals(currentFileName) && logPos >= searchPos) {
                    lockPos = true;
                    if (processEvent == 0) {
                        realStartPos = searchPos;
                        realFileName = searchBinlogFileName;
                        if (getSequence(currentFileName) == 1) {
                            // 如果是1号文件，就继续向后搜索
                            searchTSO = true;
                            continue;
                        }
                    }
                    break;
                }

                if (searchTSO && lockPos && rollbackTso != null) {
                    break;
                }

                if (!searchBinlogFileName.equals(currentFileName)) {
                    break;
                }

            }
            fetcher.close();
            if (lockPos && rollbackTso != null) {
                break;
            }

            searchBinlogFileName = beforeFile(searchBinlogFileName);
        }

        if (realFileName == null || realStartPos == null || rollbackTso == null) {
            throw new PolardbxException("position can not found!");
        }

        startPosition = new BinlogPosition(realFileName, realStartPos, -1, -1);
        startPosition.setRtso(rollbackTso);
        logger.info("search success will start @ pos [" + realFileName + ":" + realStartPos + "]");
        logger.info("search success will rollback @ tso : " + rollbackTso);
        LogPosition logPosition = new LogPosition(innerPosition.getFileName(), innerPosition.getPosition());
        context.setLogPosition(logPosition);
    }

    private int getSequence(String binlogFileName) {
        String[] spts = binlogFileName.split("\\.");
        return Integer.parseInt(spts[1]);
    }

    private String beforeFile(String binlogFileName) {
        String[] spts = binlogFileName.split("\\.");
        int num = Integer.parseInt(spts[1]);
        int before = num - 1;
        if (before <= 0) {
            return null;
        }
        int length = spts[1].length();
        return spts[0] + "." + StringUtils.leftPad(before + "", length, "0");
    }

    public TableMeta getTableMeta(String schema, String table) {
        if (needTableMeta()) {
            return memoryTableMeta.find(schema, table);
        }
        throw new UnsupportedOperationException();
    }

    public void startAsync(String binlogFileName, Long filePosition, IEventHandler handle)
        throws Exception {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        this.handle = handle;
        innerInit();
        searchPosition(binlogFileName, filePosition);
        if (needTableMeta()) {
            memoryTableMeta.rollback(startPosition.getRtso());
        }
        dataSource.reConnect();
        dataSource.dump(startPosition, logFetcher);
        parseThread = new Thread(this::doParser, "parser-thread");

        parseThread.start();
        logger.info("start cdc client success");
    }

    private boolean needTableMeta() {
        return !parser.isBinaryLog();
    }

    private DefaultRowChange processRowsLogEvent(RowsLogEvent rowsLogEvent) throws UnsupportedEncodingException {
        TableMeta tableMeta = null;
        if (needTableMeta()) {
            TableMapLogEvent tableMapLogEvent = rowsLogEvent.getTable();
            tableMeta =
                memoryTableMeta.find(tableMapLogEvent.getDbName(), tableMapLogEvent.getTableName());
        }
        return parser.parse(tableMeta,
            rowsLogEvent,
            serverCharset.getCharacterSetDatabase());
    }

    private DBMSEvent processQueryLogEvent(QueryLogEvent queryLogEvent) {
        String queryData = queryLogEvent.getQuery();
        DBMSEvent eventData = null;
        if (queryData.startsWith("BEGIN")) {
            beginEvent = new DBMSTransactionBegin();
        } else {

            String ddl = queryLogEvent.getQuery();
            Scanner scanner = new Scanner(ddl);
            StringBuilder newDdlBuilder = new StringBuilder();
            boolean needDecode = false;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                line = StringUtils.trim(line);
                if (StringUtils.startsWith(line, PRIVATE_DDL_ENCODE_BASE64)) {
                    needDecode = true;
                } else if (StringUtils.startsWith(line, POLARX_DDL_ORIGIN_SQL_PREFIX)) {
                    ddl = line.substring(POLARX_DDL_ORIGIN_SQL_PREFIX.length());
                    if (needDecode) {
                        ddl = new String(Base64.getDecoder().decode(ddl));
                        newDdlBuilder.append(POLARX_DDL_ORIGIN_SQL_PREFIX).append(ddl).append("\n");
                    } else {
                        newDdlBuilder.append(line).append("\n");
                    }
                } else {
                    newDdlBuilder.append(line).append("\n");
                }
            }
            if (needTableMeta()) {
                memoryTableMeta.apply(null, queryLogEvent.getDbName(), queryData, null);
            }
            eventData = new DefaultQueryLog(queryLogEvent.getDbName(), newDdlBuilder.toString(),
                new Timestamp(queryLogEvent.getExecTime()), queryLogEvent.getErrorCode(), queryLogEvent.getExecTime());

        }
        return eventData;
    }

    private DBMSEvent processRowsQueryLogEvent(RowsQueryLogEvent rowsQueryLogEvent) throws SQLException {
        String rowsQuery = rowsQueryLogEvent.getRowsQuery();
        String[] parameters = rowsQuery.split("::");
        DBMSEvent eventData = null;

        if (StringUtils.contains(parameters[0], "CTS")) {
            String tso = parameters[1];
            boolean isArchive = false;
            if (parameters.length > 2) {
                for (int i = 2; i < parameters.length; i++) {
                    if (StringUtils.equals(parameters[i], "ARCHIVE")) {
                        isArchive = true;
                        break;
                    }
                }
            }
            if (commitEvent != null) {
                commitEvent.setTso(tso);
                eventData = commitEvent;
                commitEvent = null;
            } else if (beginEvent != null) {
                beginEvent.setTso(tso);
                beginEvent.setArchive(isArchive);
                eventData = beginEvent;
                beginEvent = null;
            } else {
                eventData = new DBMSTsoEvent(tso);
            }
        }
        return eventData;
    }

    private void checkTransaction(LogPosition logPosition) {
        if (commitEvent != null) {
            throw new PolardbxException("last commit event not receive cts event!");
        }
        if (beginEvent != null) {
            pushEvent(beginEvent, logPosition);
            beginEvent = null;
        }
    }

    private void doParser() {
        try {
            logger.info("parser thread started!");
            while (started.get() && logFetcher.fetch()) {
                LogEvent event = decoder.decode(logFetcher, context);
                if (event == null) {
                    continue;
                }
                DBMSEvent eventData = null;
                if (event instanceof RowsLogEvent) {
                    checkTransaction(context.getLogPosition());
                    RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
                    eventData = processRowsLogEvent(rowsLogEvent);
                } else if (event instanceof QueryLogEvent) {
                    checkTransaction(context.getLogPosition());
                    QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                    eventData = processQueryLogEvent(queryLogEvent);

                } else if (event instanceof RowsQueryLogEvent) {
                    RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) event;
                    eventData = processRowsQueryLogEvent(rowsQueryLogEvent);
                    if (!(eventData instanceof DBMSTransactionBegin) && beginEvent != null) {
                        // 老版本，事务begin不支持获取tso
                        pushEvent(beginEvent, context.getLogPosition());
                        beginEvent = null;
                    }
                } else if (event instanceof XidLogEvent) {
                    beginEvent = null;
                    commitEvent = new DBMSTransactionEnd();
                } else if (event instanceof HeartbeatLogEvent) {
                    HeartbeatLogEvent heartbeatLogEvent = (HeartbeatLogEvent) event;
                    eventData = new DBMSHeartbeatLog(heartbeatLogEvent.getLogIdent());

                }
                if (eventData != null) {
                    pushEvent(eventData, context.getLogPosition());
                }

            }
        } catch (Throwable e) {
            if (exceptionHandler != null) {
                exceptionHandler.handle(e);
            } else {
                ex = e;
                logger.error("parser event failed!", e);
            }
        } finally {
            started.set(false);
            dataSource.releaseChannel();
        }
    }

    private void pushEvent(DBMSEvent eventData, LogPosition logPosition) {
        long begin = System.currentTimeMillis();
        CdcEventData cdcEventData =
            new CdcEventData(logPosition.getFileName(), logPosition.getPosition(), eventData);
        handle.onHandle(cdcEventData);
        long rt = System.currentTimeMillis() - begin;
        CdcClientMetricsManager.getInstance().recordRt(rt);
        CdcClientMetricsManager.getInstance().addEvent(1);
    }

    public boolean isTableMetaInit() {
        if (needTableMeta()) {
            return memoryTableMeta.isInit();
        }
        throw new UnsupportedOperationException();
    }

    private void printLog(LogPosition logPosition) {
        long now = System.currentTimeMillis();
        if (now - lastPrintSearchLogTimestamp > 3000) {
            logger.info("search pos in [" + logPosition.getFileName() + "] pos : [" + logPosition.getPosition() + "]");
            lastPrintSearchLogTimestamp = now;
        }
    }

    public void shutdown() {
        started.set(false);
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        if (dataSource != null) {
            dataSource.releaseChannel();
        }

    }

    public LogPosition getLogPosition() {
        return context.getLogPosition();
    }
}
