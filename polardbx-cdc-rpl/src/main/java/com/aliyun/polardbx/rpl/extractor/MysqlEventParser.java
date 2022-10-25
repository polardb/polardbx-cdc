/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.EventRepository;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.MysqlWithTsoEventParser;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.EventTransactionBuffer;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.ServerIdNotMatchException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author shicai.xsc 2020/11/27 17:37
 * @since 5.0.0.0
 */
@Slf4j
public class MysqlEventParser extends MysqlWithTsoEventParser {

    // binLogParser
    protected LogEventConvert binlogParser;
    protected EventTransactionBuffer transactionBuffer;

    protected BinlogEventSink eventSink;
    protected boolean tryUseMysqlMinPosition;
    protected BinlogPosition startPosition;
    protected int MAX_RETRY = 20;
    protected boolean autoRetry = true;
    protected boolean directExitWhenStop = true;
    protected IErrorHandler userDefinedHandler;
    protected EventRepository eventRepository;

    public MysqlEventParser(int bufferSize, EventRepository eventRepository) {
        // 初始化一下
        this.transactionBuffer = new EventTransactionBuffer(transaction -> {
            boolean succeeded = consumeTheEventAndProfilingIfNecessary(transaction);
            if (!running) {
                return;
            }

            if (!succeeded) {
                throw new CanalParseException("consume failed!");
            }
        });
        this.transactionBuffer.setBufferSize(bufferSize);
        this.eventRepository = eventRepository;
    }

    protected boolean consumeTheEventAndProfilingIfNecessary(List<MySQLDBMSEvent> entrys) throws CanalParseException {
        // 收到数据了,重置一下
        //dumpTimeoutCount = 0;
        //dumpErrorCount = 0;

        long startTs = -1;
        boolean enabled = getProfilingEnabled();
        if (enabled) {
            startTs = System.currentTimeMillis();
        }

        boolean result = eventSink.sink(entrys);
        if (enabled) {
            this.processingInterval = System.currentTimeMillis() - startTs;
        }

        if (consumedEventCount.incrementAndGet() < 0) {
            consumedEventCount.set(0);
        }

        return result;
    }

    @Override
    public void start(final AuthenticationInfo master, final BinlogPosition position, final BinlogEventSink eventSink) {
        if (running) {
            return;
        }

        running = true;

        this.runningInfo = master;
        this.eventSink = eventSink;
        this.startPosition = position;

        // 配置transaction buffer
        // 初始化缓冲队列
        transactionBuffer.start();

        // 启动工作线程
        parseThread = new ParserThread();
        parseThread.setUncaughtExceptionHandler((t, e) -> {
            boolean alreadyDeal = userDefinedHandler.handle(e);
            if (directExitWhenStop && !alreadyDeal) {
                log.error("encounter uncaught exception, process will exit.", e);
                Runtime.getRuntime().halt(1);
            }
        });
        parseThread.setName(String.format("address = %s , EventParser",
            runningInfo == null ? null : runningInfo.getAddress().toString()));
        parseThread.start();
    }

    @Override
    protected BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position) throws IOException {
        BinlogPosition startPosition = findStartPositionInternal(connection, position);
        if (needTransactionPosition.get()) {
            log.warn("prepare to find last position : " + startPosition.toString());
            Long preTransactionStartPosition = findTransactionBeginPosition(connection, startPosition);
            if (!preTransactionStartPosition.equals(startPosition.getPosition())) {
                log.warn("find new start Transaction Position , old : ",
                    startPosition.getPosition() + ", new : " + preTransactionStartPosition);

                BinlogPosition newStartPosition = new BinlogPosition(startPosition.getFileName(),
                    preTransactionStartPosition,
                    startPosition.getMasterId(),
                    startPosition.getTimestamp());
                startPosition = newStartPosition;
            }
            needTransactionPosition.compareAndSet(true, false);
        }

        // 兼容一下以前的dbsync的无文件前缀,添加一个默认值
        if (startPosition != null && startPosition.getFilePattern() == null) {
            connection.reconnect();
            BinlogPosition endPosition = findEndPosition((MysqlConnection) connection);
            startPosition.setFilePattern(endPosition.getFilePattern());
        }
        return startPosition;
    }

    @Override
    protected BinlogPosition findStartPositionInternal(ErosaConnection connection, BinlogPosition entryPosition) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        this.currentServerId = findServerId(mysqlConnection);
        if (entryPosition == null) {// 找不到历史成功记录
            return findEndPositionWithMasterIdAndTimestamp(mysqlConnection); // 默认从当前最后一个位置进行消费
        }

        // binlog定位位点失败,可能有两个原因:
        // 1. binlog位点被删除
        // 2. vip模式的mysql,发生了主备切换,判断一下serverId是否变化,针对这种模式可以发起一次基于时间戳查找合适的binlog位点
        // 此处不考虑vip模式情况，如有需要，请使用RdsEventParser

        // 判断一下是否需要按时间订阅
        if (CommonUtil.isMeaninglessBinlogFileName(entryPosition)) {
            // 如果没有指定binlogName，尝试按照timestamp进行查找
            if (entryPosition.getTimestamp() > 0L) {
                log.warn("prepare to find start position ::" + entryPosition.getTimestamp());
                return findByStartTimeStamp(mysqlConnection, entryPosition.getTimestamp());
            } else {
                log.warn("prepare to find start position just show master status");
                return findEndPositionWithMasterIdAndTimestamp(mysqlConnection); // 默认从当前最后一个位置进行消费
            }
        } else {
            if (entryPosition.getPosition() > 0L) {
                // 如果指定binlogName + offest，直接返回
                log.warn("prepare to find start position just last position " + entryPosition.getFileName() + ":"
                    + entryPosition.getPosition() + ":");
                return entryPosition;
                // return findPositionWithMasterIdAndTimestamp(mysqlConnection, entryPosition);
            } else {
                BinlogPosition specificLogFilePosition = null;
                if (entryPosition.getTimestamp() > 0L) {
                    // 如果指定binlogName +
                    // timestamp，但没有指定对应的offest，尝试根据时间找一下offest
                    BinlogPosition endPosition = findEndPosition(mysqlConnection);
                    if (endPosition != null) {
                        log.warn("prepare to find start position " + entryPosition.getFileName() + "::"
                            + entryPosition.getTimestamp());
                        specificLogFilePosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                            entryPosition.getTimestamp(),
                            endPosition,
                            entryPosition.getFileName());
                    }
                }

                if (specificLogFilePosition == null) {
                    // position不存在，从文件头开始
                    entryPosition = new BinlogPosition(entryPosition.getFileName(),
                        BINLOG_START_OFFEST,
                        entryPosition.getMasterId(),
                        entryPosition.getTimestamp());
                    return entryPosition;
                } else {
                    return specificLogFilePosition;
                }
            }
        }
    }

    protected BinlogPosition findPositionWithMasterIdAndTimestamp(MysqlConnection connection,
                                                                  BinlogPosition fixedPosition) {
        if (fixedPosition.getTimestamp() > 0) {
            return fixedPosition;
        }

        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        long startTimestamp = TimeUnit.MILLISECONDS
            .toSeconds(System.currentTimeMillis() + 102L * 365 * 24 * 3600 * 1000); // 当前时间的未来102年
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            fixedPosition,
            fixedPosition.getFileName());
    }

    protected BinlogPosition findEndPositionWithMasterIdAndTimestamp(MysqlConnection connection) {
        MysqlConnection mysqlConnection = (MysqlConnection) connection;
        final BinlogPosition endPosition = findEndPosition(mysqlConnection);
        long startTimestamp = System.currentTimeMillis();
        return findAsPerTimestampInSpecificLogFile(mysqlConnection,
            startTimestamp,
            endPosition,
            endPosition.getFileName());
    }

    // 根据时间查找binlog位置
    @Override
    protected BinlogPosition findByStartTimeStamp(MysqlConnection mysqlConnection, Long startTimestamp) {
        BinlogPosition endPosition = findEndPosition(mysqlConnection);
        BinlogPosition startPosition = findStartPosition(mysqlConnection);
        String maxBinlogFileName = endPosition.getFileName();
        String minBinlogFileName = startPosition.getFileName();
        log.info("show master status to set search end condition: " + endPosition);
        String startSearchBinlogFile = endPosition.getFileName();
        boolean shouldBreak = false;
        while (running && !shouldBreak) {
            try {
                BinlogPosition entryPosition = findAsPerTimestampInSpecificLogFile(mysqlConnection,
                    startTimestamp,
                    endPosition,
                    startSearchBinlogFile);
                if (entryPosition == null) {
                    if (StringUtils.equalsIgnoreCase(minBinlogFileName, startSearchBinlogFile)) {
                        // 已经找到最早的一个binlog，没必要往前找了
                        shouldBreak = true;
                        log.warn("Didn't find the corresponding binlog files from " + minBinlogFileName + " to "
                            + maxBinlogFileName);
                    } else {
                        // 继续往前找
                        int binlogSeqNum = Integer
                            .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                        if (binlogSeqNum <= 1) {
                            log.warn("Didn't find the corresponding binlog files");
                            shouldBreak = true;
                        } else {
                            int nextBinlogSeqNum = binlogSeqNum - 1;
                            String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                                startSearchBinlogFile.indexOf(".") + 1);
                            String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                            startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                        }
                    }
                } else {
                    log.info("found and return:" + endPosition + " in findByStartTimeStamp operation.");
                    return entryPosition;
                }
            } catch (Exception e) {
                log.warn("the binlogfile:" + startSearchBinlogFile
                        + " doesn't exist, to continue to search the next binlogfile , caused by ",
                    e);
                int binlogSeqNum = Integer
                    .parseInt(startSearchBinlogFile.substring(startSearchBinlogFile.indexOf(".") + 1));
                if (binlogSeqNum <= 1) {
                    log.warn("Didn't find the corresponding binlog files");
                    shouldBreak = true;
                } else {
                    int nextBinlogSeqNum = binlogSeqNum - 1;
                    String binlogFileNamePrefix = startSearchBinlogFile.substring(0,
                        startSearchBinlogFile.indexOf(".") + 1);
                    String binlogFileNameSuffix = String.format("%06d", nextBinlogSeqNum);
                    startSearchBinlogFile = binlogFileNamePrefix + binlogFileNameSuffix;
                }
            }
        }
        // 找不到
        return null;
    }

    // 根据想要的position，可能这个position对应的记录为rowdata，需要找到事务头，避免丢数据
    // 主要考虑一个事务执行时间可能会几秒种，如果仅仅按照timestamp相同，则可能会丢失事务的前半部分数据
    protected Long findTransactionBeginPosition(ErosaConnection mysqlConnection,
                                                final BinlogPosition entryPosition) throws IOException {
        // 尝试找到一个合适的位置
        final AtomicBoolean reDump = new AtomicBoolean(false);
        mysqlConnection.reconnect();
        binlogParser.refreshState();

        try {
            mysqlConnection.seek(entryPosition.getFileName(), entryPosition.getPosition(), new SinkFunction() {

                private BinlogPosition lastPosition;

                @Override
                public boolean sink(LogEvent event, LogPosition logPosition) {
                    try {
                        MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                        if (entry == null) {
                            return true;
                        }

                        DBMSEvent dbmsEvent = entry.getDbMessageWithEffect();
                        // 直接查询第一条业务数据，确认是否为事务Begin/End
                        if (dbmsEvent instanceof DBMSTransactionBegin || dbmsEvent instanceof DBMSTransactionEnd ||
                            CommonUtil.isPolarDBXHeartbeat(dbmsEvent) || CommonUtil.isDDL(dbmsEvent)) {
                            lastPosition = buildLastPosition(entry);
                            return false;
                        } else {
                            reDump.set(true);
                            lastPosition = buildLastPosition(entry);
                            return false;
                        }
                    } catch (Exception e) {
                        // 上一次记录的poistion可能为一条update/insert/delete变更事件，直接进行dump的话，会缺少tableMap事件，导致tableId未进行解析
                        processSinkError(e, lastPosition, entryPosition.getFileName(), entryPosition.getPosition());
                        MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                            TaskContext.getInstance().getTaskId(), e.getMessage());
                        reDump.set(true);
                        return false;
                    }
                }
            });
        } catch (Exception e) {
            log.error("ERROR ## findTransactionBeginPosition has an error", e);
        }

        log.info("begin redump");

        // 针对开始的第一条为非Begin记录，需要从该binlog扫描
        if (reDump.get()) {
            final AtomicLong preTransactionStartPosition = new AtomicLong(0L);
            mysqlConnection.reconnect();
            try {
                mysqlConnection.seek(entryPosition.getFileName(), 4L, new SinkFunction() {

                    private BinlogPosition lastPosition;

                    @Override
                    public boolean sink(LogEvent event, LogPosition logPosition) {
                        try {

                            MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                            if (entry == null) {
                                return true;
                            }

                            DBMSEvent dbmsEvent = entry.getDbMessageWithEffect();
                            // 直接查询第一条业务数据，确认是否为事务Begin
                            // 记录一下transaction begin position
                            if ((dbmsEvent instanceof DBMSTransactionBegin ||
                                CommonUtil.isPolarDBXHeartbeat(dbmsEvent) || CommonUtil.isDDL(dbmsEvent))
                                && entry.getPosition().getPosition() < entryPosition.getPosition()) {
                                preTransactionStartPosition.set(entry.getPosition().getPosition());
                            }

                            if (entry.getPosition().getPosition() >= entryPosition.getPosition()) {
                                return false;// 退出
                            }

                            lastPosition = buildLastPosition(entry);
                        } catch (Exception e) {
                            processSinkError(e, lastPosition, entryPosition.getFileName(), entryPosition.getPosition());
                            MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                                TaskContext.getInstance().getTaskId(), e.getMessage());
                            return false;
                        }

                        return running;
                    }
                });
            } catch (Exception e) {
                log.error("ERROR ## findTransactionBeginPosition has an error", e);
            }

            // 判断一下找到的最接近position的事务头的位置
            if (preTransactionStartPosition.get() > entryPosition.getPosition()) {
                log.error("preTransactionEndPosition greater than startPosition from zk or localconf, maybe lost data");
                throw new IOException(
                    "preTransactionStartPosition greater than startPosition from zk or localconf, maybe lost data");
            }
            return preTransactionStartPosition.get();
        } else {
            return entryPosition.getPosition();
        }
    }

    /**
     * 根据给定的时间戳，在指定的binlog中找到最接近于该时间戳(必须是小于时间戳)的一个事务起始位置。
     * 针对最后一个binlog会给定endPosition，避免无尽的查询
     */
    protected BinlogPosition findAsPerTimestampInSpecificLogFile(MysqlConnection mysqlConnection,
                                                                 final Long startTimestamp,
                                                                 final BinlogPosition endPosition,
                                                                 final String searchBinlogFile) {

        final AtomicReference<BinlogPosition> ref = new AtomicReference<BinlogPosition>();
        try {
            mysqlConnection.reconnect();
            binlogParser.refreshState();
            // 如果以timestamp来找位点，这里binlog parser的binlogfilename为"0"
            binlogParser.setBinlogFileName(searchBinlogFile);
            // 开始遍历文件
            mysqlConnection.seek(searchBinlogFile, 4L, new SinkFunction() {

                private BinlogPosition lastPosition;

                @Override
                public boolean sink(LogEvent event, LogPosition logPosition) {
                    BinlogPosition entryPosition = null;
                    try {
                        MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, true);
                        String logfilename = binlogParser.getBinlogFileName();
                        // String logfilename = searchBinlogFile;
                        Long logfileoffset = event.getLogPos();
                        Long logposTimestamp = event.getWhen();
                        Long masterId = event.getServerId();

                        // 提前执行判断：找第一条记录时间戳，如果最小的一条记录都不满足条件，可直接退出
                        if (logposTimestamp >= startTimestamp) {
                            return false;
                        }

                        // 不包含任何 transaction 的 binlog
                        if (entry == null) {
                            if (StringUtils.equals(endPosition.getFileName(), searchBinlogFile)
                                || StringUtils.equals(endPosition.getFileName(), logfilename)) {
                                if (endPosition.getPosition() <= logfileoffset) {
                                    entryPosition = new BinlogPosition(logfilename,
                                        logfileoffset,
                                        masterId,
                                        logposTimestamp);
                                    if (log.isDebugEnabled()) {
                                        log.debug("set " + entryPosition
                                            + " to be pending start position before finding another proper one...");
                                    }
                                    /**
                                     * 把这个 position 作为查找的结果
                                     */
                                    ref.set(entryPosition);

                                    return false;
                                }
                            }

                            return true;
                        }

                        logfilename = entry.getPosition().getFileName();

                        DBMSEvent dbmsEvent = entry.getDbMessageWithEffect();
                        if (dbmsEvent instanceof DBMSTransactionBegin || dbmsEvent instanceof DBMSTransactionEnd) {
                            if (log.isDebugEnabled()) {
                                log.debug(String.format("compare exit condition:%s,%s,%s, startTimestamp=%s...",
                                    logfilename,
                                    logfileoffset,
                                    logposTimestamp,
                                    startTimestamp));
                            }
                        }

                        if (StringUtils.equals(endPosition.getFileName(), searchBinlogFile)
                            || StringUtils.equals(endPosition.getFileName(), logfilename)) {
                            if (endPosition.getPosition() <= logfileoffset) {
                                return false;
                            }
                        }

                        // 记录一下上一个事务结束的位置，即下一个事务的position
                        // position = current +
                        // data.length，代表该事务的下一条offest，避免多余的事务重复
                        if (dbmsEvent instanceof DBMSTransactionEnd || dbmsEvent instanceof DBMSTransactionBegin) {
                            entryPosition = new BinlogPosition(logfilename, logfileoffset, masterId, logposTimestamp);
                            if (log.isDebugEnabled()) {
                                log.debug("set " + entryPosition
                                    + " to be pending start position before finding another proper one...");
                            }
                            ref.set(entryPosition);
                        }

                        lastPosition = buildLastPosition(entry);
                    } catch (Throwable e) {
                        processSinkError(e, lastPosition, searchBinlogFile, 4L);
                        MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                            TaskContext.getInstance().getTaskId(), e.getMessage());
                    }

                    return running;
                }
            });
        } catch (Exception e) {
            log.error("ERROR ## findAsPerTimestampInSpecificLogFile has an error", e);
        }

        if (ref.get() != null) {
            return ref.get();
        } else {
            return null;
        }
    }

    /**
     * @param isSeek 是否回溯
     */
    protected MySQLDBMSEvent parseAndProfilingIfNecessary(LogEvent bod, boolean isSeek) throws Exception {
        long startTs = -1;
        boolean enabled = getProfilingEnabled();
        if (enabled) {
            startTs = System.currentTimeMillis();
        }
        MySQLDBMSEvent event = binlogParser.parse(bod, isSeek);
        if (event != null) {
            event.setRepository(eventRepository);
            event.tryPersist();
        }

        if (enabled) {
            this.parsingInterval = System.currentTimeMillis() - startTs;
        }

        if (parsedEventCount.incrementAndGet() < 0) {
            parsedEventCount.set(0);
        }
        return event;
    }

    protected BinlogPosition buildLastPosition(MySQLDBMSEvent entry) { // 初始化一下
        return entry.getPosition();
    }

    public void setBinlogParser(LogEventConvert binlogParser) {
        this.binlogParser = binlogParser;
    }

    public void setAutoRetry(boolean autoRetry) {
        this.autoRetry = autoRetry;
    }

    public void setDirectExitWhenStop(boolean directExitWhenStop) {
        this.directExitWhenStop = directExitWhenStop;
    }

    public void setUserDefinedHandler(IErrorHandler handler) {
        this.userDefinedHandler = handler;
    }

    private class ParserThread extends Thread {
        int retry = 0;

        @Override
        public void run() {
            ErosaConnection erosaConnection = null;
            while (running) {
                try {
                    // 开始执行replication
                    // 1. 构造Erosa连接
                    erosaConnection = buildErosaConnection();

                    // 2. 启动一个心跳线程
                    // startHeartBeat(erosaConnection);

                    // 3. 执行dump前的准备工作，此处会准备好metaConnection
                    preDump(erosaConnection);

                    erosaConnection.connect();

                    // 5. 获取最后的位置信息
                    BinlogPosition processedStartPosition = findStartPosition(erosaConnection, startPosition);
                    if (processedStartPosition == null) {
                        if (tryUseMysqlMinPosition) {
                            processedStartPosition = findStartPosition((MysqlConnection) erosaConnection);
                            log.warn("can't find start position, will use mysql min position start!:"
                                + processedStartPosition);
                        } else {
                            throw new PositionNotFoundException("can't find start position");
                        }
                    }
                    startPosition = processedStartPosition;

                    if (!processTableMeta(startPosition)) {
                        throw new CanalParseException(
                            "can't find init table meta for with position : " + startPosition);
                    }
                    log.warn("find start position : " + startPosition.toString());
                    // 重新链接，因为在找position过程中可能有状态，需要断开后重建
                    erosaConnection.reconnect();
                    binlogParser.refreshState();
                    binlogParser.setBinlogFileName(startPosition.getFileName());

                    final SinkFunction sinkHandler = new SinkFunction() {

                        private BinlogPosition lastPosition;

                        @Override
                        public boolean sink(LogEvent event, LogPosition logPosition) throws CanalParseException,
                            TableIdNotFoundException {
                            try {
                                MySQLDBMSEvent entry = parseAndProfilingIfNecessary(event, false);

                                if (!running) {
                                    return false;
                                }

                                if (entry != null) {
                                    transactionBuffer.add(entry);
                                    // 记录一下对应的positions
                                    this.lastPosition = buildLastPosition(entry);
                                }
                                StatisticalProxy.getInstance().heartbeat();
                                return running;
                            } catch (TableIdNotFoundException e) {
                                throw e;
                            } catch (Throwable e) {
                                if (e.getCause() instanceof TableIdNotFoundException) {
                                    throw (TableIdNotFoundException) e.getCause();
                                }
                                // 记录一下，出错的位点信息
                                processSinkError(e,
                                    this.lastPosition,
                                    startPosition.getFileName(),
                                    startPosition.getPosition());
                                MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                                    TaskContext.getInstance().getTaskId(), e.getMessage());
                                throw new CanalParseException(e); // 继续抛出异常，让上层统一感知
                            }
                        }
                    };

                    // 4. 开始dump数据
                    erosaConnection.dump(startPosition.getFileName(),
                        startPosition.getPosition(),
                        startPosition.getTimestamp(),
                        sinkHandler);
                } catch (ServerIdNotMatchException e) {
                    throw e;
                } catch (TableIdNotFoundException e) {
                    // 特殊处理TableIdNotFound异常,出现这样的异常，一种可能就是起始的position是一个事务当中，导致tablemap
                    // Event时间没解析过
                    needTransactionPosition.compareAndSet(false, true);
                    log.error(String.format("dump address %s has an error, retrying. caused by ",
                        runningInfo.getAddress().toString()), e);
                    MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                        TaskContext.getInstance().getTaskId(), e.getMessage());
                } catch (Throwable e) {
                    processDumpError(e);
                    if (!running) {
                        if (!(e instanceof java.nio.channels.ClosedByInterruptException
                            || e.getCause() instanceof java.nio.channels.ClosedByInterruptException)) {
                            throw new CanalParseException(String.format("dump address %s has an error, retrying. ",
                                runningInfo.getAddress().toString()), e);
                        }
                    } else {
                        log.error(String.format("dump address %s has an error, retrying. caused by ",
                            runningInfo.getAddress().toString()), e);
                        MonitorManager.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                            TaskContext.getInstance().getTaskId(), e.getMessage());
                    }
                } finally {
                    // 重新置为中断状态
                    Thread.interrupted();
                    // 关闭一下链接
                    afterDump(erosaConnection);
                    try {
                        if (erosaConnection != null) {
                            erosaConnection.disconnect();
                        }
                    } catch (IOException e1) {
                        log.error("disconnect address " + runningInfo.getAddress().toString()
                                + " has an error, retrying., caused by ",
                            e1);
                    }
                }
                // 出异常了，退出sink消费，释放一下状态
                transactionBuffer.reset();// 重置一下缓冲队列，重新记录数据
                binlogParser.reset();// 重新置位

                log.error("ParserThread run failed, retry: {}", retry);
                if (++retry >= MAX_RETRY || !autoRetry) {
                    running = false;
                }
                if (running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }

            if (directExitWhenStop) {
                log.error("ParserThread failed after retry: {}, process exit", retry);
                System.exit(1);
            }
        }
    }
}
