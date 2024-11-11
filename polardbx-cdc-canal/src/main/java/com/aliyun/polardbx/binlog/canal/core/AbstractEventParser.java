/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.BinlogEventParser;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.IErrorHandler;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkResult;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 抽象的EventParser, 最大化共用mysql/oracle版本的实现
 *
 * @author jianghang 2013-1-20 下午08:10:25
 * @version 1.0.0
 */
public abstract class AbstractEventParser implements BinlogEventParser {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEventParser.class);

    protected volatile boolean running = false; // 是否处于运行中

    protected HandlerContext head = null;
    // 统计参数
    protected AtomicBoolean profilingEnabled = new AtomicBoolean(false); // profile开关参数
    protected AtomicLong receivedEventCount = new AtomicLong();
    protected AtomicLong parsedEventCount = new AtomicLong();
    protected AtomicLong consumedEventCount = new AtomicLong();
    protected long parsingInterval = -1;
    protected long processingInterval = -1;
    // 认证信息
    protected volatile AuthenticationInfo runningInfo;
    protected Thread parseThread = null;
    protected String polarxVersion;
    protected String polarxInstanceId;
    protected Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {

        @Override
        public void uncaughtException(Thread t, Throwable e) {

        }
    };
    protected AtomicBoolean needTransactionPosition = new AtomicBoolean(true);
    protected long lastEntryTime = 0L;
    protected volatile boolean detectingEnable = true; // 是否开启心跳检查
    protected Integer detectingIntervalInSeconds = 3; // 检测频率
    protected long currentServerId = -1;
    protected LogEventHandler eventHandler;
    protected SinkFunction searchFunction;
    protected ServerCharactorSet serverCharactorSet;
    protected int lowerCaseTableNames;
    protected IErrorHandler errorHandler;
    protected HandlerContext tail = new HandlerContext(new DefaultTailEventFilter());

    protected abstract ErosaConnection buildErosaConnection();

    protected abstract BinlogPosition findStartPosition(ErosaConnection connection, BinlogPosition position)
        throws IOException;

    public BinlogPosition findStartPositionOnceBeforeStart(ErosaConnection connection, BinlogPosition position)
        throws IOException {
        if (running) {
            return null;
        }
        running = true;
        BinlogPosition returnPos = findStartPosition(connection, position);
        running = false;
        return returnPos;
    }

    protected void preDump(ErosaConnection connection) {
    }

    protected boolean processTableMeta(BinlogPosition position) {
        return true;
    }

    protected void afterDump(ErosaConnection connection) {
    }

    protected void processDumpError(Throwable e) {
        // do nothing
    }

    public void setErrorHandler(IErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public void addFilter(LogEventFilter logEventFilter) {
        HandlerContext context = new HandlerContext(logEventFilter);
        if (head == null) {
            head = context;
            context.setNext(tail);
            tail = context;
            return;
        }
        context.setNext(tail.getNext());
        tail.setNext(context);
        tail = context;
    }

    public void setEventHandler(LogEventHandler eventLogEventHandler) {
        this.eventHandler = eventLogEventHandler;
    }

    @Override
    public void start(final AuthenticationInfo master, final BinlogPosition position) {

        if (running) {
            return;
        }

        running = true;
        this.runningInfo = master;

        final AtomicReference<BinlogPosition> positionReference = new AtomicReference<BinlogPosition>();
        positionReference.set(position);
        // 构造binlog parser
        // 启动工作线程
        parseThread = new Thread(() -> {
            ErosaConnection erosaConnection = null;
            ThreadRecorder recorder = new ThreadRecorder(runningInfo.getStorageMasterInstId());
            recorder.init();

            while (running) {
                try {
                    final RuntimeContext runtimeContext = new RuntimeContext(recorder);
                    runtimeContext.setAuthenticationInfo(runningInfo);
                    head.setRuntimeContext(runtimeContext);

                    // 开始执行replication
                    // 1. 构造Erosa连接
                    erosaConnection = buildErosaConnection();

                    // 3. 执行dump前的准备工作
                    preDump(erosaConnection);

                    erosaConnection.connect();// 链接
                    // 4. 获取最后的位置信息
                    logger.info("prepare find start position for " + positionReference.get());
                    long begin = System.currentTimeMillis();
                    BinlogPosition _startPosition = findStartPosition(erosaConnection, positionReference.get());
                    final BinlogPosition startPosition = _startPosition;

                    if (startPosition == null) {
                        throw new PositionNotFoundException(
                            "storageInstance:" + master.getStorageMasterInstId() + ",search pos:"
                                + position.toString());
                    }
                    logger.warn("find start position : " + startPosition.toString() + " cost : " + (
                        System.currentTimeMillis() - begin) + "ms");
                    // 重新链接，因为在找position过程中可能有状态，需要断开后重建

                    logger.info("begin to extractor binlog events !");
                    erosaConnection.reconnect();

                    recorder.dump();

                    //                    if (startPosition.getTso() > 0) {
                    //                        runtimeContext.setMaxTSO(startPosition.getTso());
                    //                    }
                    runtimeContext.setVersion(polarxVersion);
                    if (position.getTso() > 0) {
                        runtimeContext.setRecovery(true);
                        startPosition.setRtso(position.getRtso());// 这个rtso只用来rollback
                    } else {
                        runtimeContext.setRecovery(false);
                    }
                    runtimeContext.setStartPosition(startPosition);
                    runtimeContext.setServerCharactorSet(serverCharactorSet);
                    runtimeContext.setHostAddress(master.getAddress().getAddress().getHostAddress());
                    runtimeContext.setLowerCaseTableNames(((MysqlConnection) erosaConnection).getLowerCaseTableNames());
                    if (searchFunction instanceof SinkResult) {
                        RuntimeContext.setInitTopology(((SinkResult) searchFunction).getTopologyContext());
                    }
                    head.fireStart();

                    final SinkFunction sinkHandler = (event, logPosition) -> {
                        try {
                            runtimeContext.setBinlogFile(logPosition.getFileName());
                            head.setRuntimeContext(runtimeContext);
                            head.doNext(event);
                            positionReference.set(new BinlogPosition(runtimeContext.getBinlogFile(),
                                runtimeContext.getLogPos(),
                                -1,
                                event.getWhen()));
                            if (runtimeContext.hasTSO()) {
                                positionReference.get().setTso(Long.valueOf(runtimeContext.getMaxTSO()));
                            }

                            if (!running) {
                                return false;
                            }

                            return running;
                        } catch (Throwable e) {
                            if (e.getCause() instanceof TableIdNotFoundException) {
                                throw (TableIdNotFoundException) e.getCause();
                            }
                            // 记录一下，出错的位点信息
                            processSinkError(e,
                                positionReference.get(),
                                startPosition.getFileName(),
                                startPosition.getPosition());
                            throw new CanalParseException(e); // 继续抛出异常，让上层统一感知
                        }
                    };

                    head.setRuntimeContext(runtimeContext);
                    head.fireStartConsume();

                    // 4. 开始dump数据
                    if (StringUtils.isEmpty(startPosition.getFileName()) && startPosition.getTimestamp() > 0) {
                        erosaConnection.dump(startPosition.getTimestamp(), sinkHandler);
                    } else {
                        erosaConnection.dump(startPosition.getFileName(),
                            startPosition.getPosition(),
                            startPosition.getTimestamp(),
                            sinkHandler);
                    }

                } catch (Throwable e) {
                    if (errorHandler != null) {
                        errorHandler.onError(e);
                    }
                    processDumpError(e);
                    if (!running) {
                        if (!(e instanceof java.nio.channels.ClosedByInterruptException
                            || e.getCause() instanceof java.nio.channels.ClosedByInterruptException)) {
                            throw new CanalParseException(String.format("dump address %s has an error, retrying. ",
                                runningInfo.getAddress().toString()), e);
                        }
                    } else {
                        logger.error(String.format("dump address %s has an error, retrying. caused by ",
                            runningInfo.getAddress().toString()), e);
                        new Thread(() -> Runtime.getRuntime().halt(1)).start();
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
                        logger.error("disconnect address " + runningInfo.getAddress().toString()
                            + " has an error, retrying., caused by ", e1);

                    }
                }

                if (running) {
                    // sleep一段时间再进行重试
                    try {
                        Thread.sleep(5000 + RandomUtils.nextInt(0, 5000));
                    } catch (InterruptedException e) {
                    }
                }
            }
            recorder.stop();
        });

        parseThread.setUncaughtExceptionHandler(handler);
        parseThread.setName(String.format("address = %s , EventParser",
            runningInfo == null ? null : runningInfo.getAddress().toString()));
        parseThread.start();
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (head != null) {
            head.fireStop();
        }

        head = null;
        tail = new HandlerContext(new DefaultTailEventFilter());
        eventHandler = null;
        parseThread.interrupt(); // 尝试中断
        try {
            logger.info("prepare to join parseThread");
            parseThread.join();// 等待其结束
            logger.info("join parseThread finished.");
        } catch (InterruptedException e) {
            // ignore
        }

    }

    public Boolean getProfilingEnabled() {
        return profilingEnabled.get();
    }

    public void setProfilingEnabled(boolean profilingEnabled) {
        this.profilingEnabled = new AtomicBoolean(profilingEnabled);
    }

    protected void processSinkError(Throwable e, BinlogPosition lastPosition, String startBinlogFile,
                                    long startPosition) {
        if (lastPosition != null) {
            logger.warn(
                String.format("ERROR ## parse this event has an error , last position : [%s]", lastPosition.toString()),
                e);
        } else {
            logger.warn(String.format("ERROR ## parse this event has an error , last position : [%s,%s]",
                startBinlogFile,
                startPosition), e);
        }
    }

    @Override
    public void start(final AuthenticationInfo master, final BinlogPosition position, final BinlogEventSink eventSink) {
        return;
    }

    protected TimerTask buildHeartBeatTimeTask(ErosaConnection connection) {
        return null;
    }

    public Long getParsedEventCount() {
        return parsedEventCount.get();
    }

    public Long getConsumedEventCount() {
        return consumedEventCount.get();
    }

    public long getParsingInterval() {
        return parsingInterval;
    }

    public long getProcessingInterval() {
        return processingInterval;
    }

    public void setDetectingEnable(boolean detectingEnable) {
        this.detectingEnable = detectingEnable;
    }

    public void setDetectingIntervalInSeconds(Integer detectingIntervalInSeconds) {
        this.detectingIntervalInSeconds = detectingIntervalInSeconds;
    }

    public long getCurrentServerId() {
        return currentServerId;
    }

    public void setCurrentServerId(long currentServerId) {
        this.currentServerId = currentServerId;
    }

    public void setPolarxVersion(String polarxVersion) {
        this.polarxVersion = polarxVersion;
    }

    public void setPolarxInstanceId(String polarxInstanceId) {
        this.polarxInstanceId = polarxInstanceId;
    }

    public void setNeedTransactionPosition(AtomicBoolean needTransactionPosition) {
        this.needTransactionPosition = needTransactionPosition;
    }

    private class DefaultTailEventFilter implements LogEventFilter {

        @Override
        public void handle(HandlerEvent event, HandlerContext context) throws Exception {
            if (eventHandler != null) {
                eventHandler.handle(event);
            }
        }

        @Override
        public void onStart(HandlerContext context) {
            if (eventHandler != null) {
                eventHandler.onStart(context);
            }
        }

        @Override
        public void onStop() {
            if (eventHandler != null) {
                eventHandler.onStop();
            }
        }

        @Override
        public void onStartConsume(HandlerContext context) {

        }
    }
}
