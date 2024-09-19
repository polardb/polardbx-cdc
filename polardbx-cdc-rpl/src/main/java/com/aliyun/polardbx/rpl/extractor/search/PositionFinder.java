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
package com.aliyun.polardbx.rpl.extractor.search;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.LogEventConvert;
import com.aliyun.polardbx.rpl.extractor.search.handler.ISearchHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PositionFinder implements SinkFunction {

    private static final Logger log = LoggerFactory.getLogger(PositionFinder.class);
    private final BinlogPosition endPosition;
    private final LogEventConvert binlogParser;
    private final ErosaConnection mysqlConnection;
    private ISearchHandler searchHandler;
    private SearchContext context = new SearchContext();
    private BinlogPosition searchPosition;
    private boolean running = true;
    private boolean polarx = false;

    public PositionFinder(BinlogPosition endPosition,
                          LogEventConvert binlogParser,
                          ISearchHandler searchHandler,
                          ErosaConnection mysqlConnection) {
        this.endPosition = endPosition;
        this.binlogParser = binlogParser;
        this.mysqlConnection = mysqlConnection;
        this.searchHandler = searchHandler;
    }

    public void setPolarx(boolean polarx) {
        this.polarx = polarx;
    }

    public BinlogPosition findPos() throws IOException {
        String searchFile = endPosition.getFileName();
        context = new SearchContext();
        context.setCurrentSearchFile(searchFile);
        context.setPolarx(polarx);
        //resetHandlerList 查找到目标后会被删除，没被删除的一定是还没有找到目标位点
        while (searchFile != null) {
            mysqlConnection.reconnect();
            // tso需要被清空
            if (log.isDebugEnabled()) {
                log.debug("begin to seek pos in file : " + searchFile);
            }

            try {
                mysqlConnection.seek(searchFile, 4L, this);
            } catch (Exception e) {
                log.error("ERROR ## findTransactionBeginPosition has an error", e);
            }
            if (log.isDebugEnabled()) {
                log.debug("end seek pos @ " + searchPosition);
            }

            if (isPositionValid()) {
                break;
            }
            searchFile = preFileName(searchFile);
            if (searchFile == null) {
                // 已经是最小文件了，直接给pos赋值0
                searchPosition.setRtso(ExecutionConfig.ORIGIN_TSO);
                break;
            }
            if (!isPositionValid()) {
                throw new PositionNotFoundException();
            }
            context.reset();
            context.setCurrentSearchFile(searchFile);
            mysqlConnection.reconnect();
            long fileSize = mysqlConnection.binlogFileSize(searchFile);
            context.setFileSize(fileSize);
        }
        return searchPosition;
    }

    public boolean isPositionValid() {
        if (searchPosition == null) {
            throw new PositionNotFoundException();
        }
        if (polarx && StringUtils.isBlank(searchPosition.getRtso())) {
            return false;
        }
        return true;
    }

    private String preFileName(String fileName) {
        int dotIdx = fileName.indexOf(".");
        String prefix = fileName.substring(0, dotIdx);
        String suffix = fileName.substring(dotIdx + 1);
        int suffixLength = suffix.length();
        int seq = Integer.parseInt(suffix);
        if (seq == 1) {
            return null;
        }
        return prefix + "." + StringUtils.leftPad(String.valueOf(seq - 1), suffixLength, "0");
    }

    private MySQLDBMSEvent parseAndProfilingIfNecessary(LogEvent event) throws Exception {
        return binlogParser.parse(event, true);
    }

    private void extractTSO(LogEvent event) {
        context.setCurrentTSO(null);
        if (event instanceof QueryLogEvent) {
            QueryLogEvent queryLog = (QueryLogEvent) event;
            if (queryLog.getQuery().endsWith("BEGIN")) {
                return;
            }
            String tso = CommonUtils.extractPrivateTSOFromDDL(queryLog.getQuery());
            if (StringUtils.isBlank(tso)) {
                throw new PolardbxException("x to x need open private DDL config");
            }
            context.setCurrentTSO(tso);

        } else if (event instanceof RowsQueryLogEvent) {
            RowsQueryLogEvent rowsQueryLog = (RowsQueryLogEvent) event;
            context.setCurrentTSO(LogEventUtil.getTsoFromRowQuery(rowsQueryLog.getRowsQuery()));
        }
    }

    public boolean endOfFile(LogEvent event) {
        long fileSize = context.getFileSize();
        if (fileSize > 0 && event.getLogPos() >= fileSize) {
            log.info(
                context.getCurrentSearchFile() + " reach end! offset > fileSize : " + event.getLogPos() + ":"
                    + fileSize);
            return true;
        }

        if (event.getHeader().getType() == LogEvent.ROTATE_EVENT) {
            RotateLogEvent rotateLogEvent = (RotateLogEvent) event;
            if (!StringUtils.equalsIgnoreCase(rotateLogEvent.getFilename(), context.getCurrentSearchFile())) {
                log.info("file name not match , current : " + rotateLogEvent.getFilename() + " expect "
                    + context.getCurrentSearchFile());
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean sink(LogEvent event, LogPosition logPosition) throws CanalParseException, TableIdNotFoundException {
        try {

            if (polarx) {
                extractTSO(event);
            }

            if (searchHandler.isEnd(event, context) || endOfFile(event)) {
                searchPosition = context.getResultPosition();
                return false;
            }
            if (searchHandler.accept(event, context)) {
                searchHandler.handle(event, context);
            }

        } catch (Exception e) {
            String errorMsg = "search valid pos failed!, current pos  [" + logPosition.getFileName() + ":" + logPosition
                .getPosition() + "] , entry[" + endPosition.getFileName() + ":"
                + endPosition.getPosition() + "]";
            log.error(errorMsg, e);
            e = new PolardbxException(errorMsg, e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.RPL_PROCESS_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
            return false;
        }
        return running;
    }
}
