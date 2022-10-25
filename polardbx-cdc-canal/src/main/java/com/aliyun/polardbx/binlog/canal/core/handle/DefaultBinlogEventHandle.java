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
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.LogEventHandler;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

public class DefaultBinlogEventHandle implements EventHandle {

    protected HandlerContext head = null;
    protected HandlerContext tail = new HandlerContext(new DefaultTailEventFilter());
    protected LogEventHandler eventHandler;
    private RuntimeContext runtimeContext;
    private AuthenticationInfo runningInfo;
    private String polarxVersion;
    private BinlogPosition startPosition;
    private String requestTSO;
    private ServerCharactorSet serverCharactorSet;
    private int lowerCaseTableNames;

    public DefaultBinlogEventHandle(AuthenticationInfo runningInfo, String polarxVersion,
                                    BinlogPosition startPosition, String requestTSO,
                                    ServerCharactorSet serverCharactorSet, int lowerCaseTableNames) {
        this.runningInfo = runningInfo;
        this.polarxVersion = polarxVersion;
        this.startPosition = startPosition;
        this.requestTSO = requestTSO;
        this.serverCharactorSet = serverCharactorSet;
        this.lowerCaseTableNames = lowerCaseTableNames;
    }

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

    public void setEventHandler(LogEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @Override
    public boolean interrupt() {
        return false;
    }

    @Override
    public Set<Integer> interestEvents() {
        Set<Integer> flagSet = new HashSet<>();
        flagSet.add(LogEvent.START_EVENT_V3);
        flagSet.add(LogEvent.QUERY_EVENT);
        flagSet.add(LogEvent.ROWS_QUERY_LOG_EVENT);
        flagSet.add(LogEvent.WRITE_ROWS_EVENT_V1);
        flagSet.add(LogEvent.WRITE_ROWS_EVENT);
        flagSet.add(LogEvent.UPDATE_ROWS_EVENT_V1);
        flagSet.add(LogEvent.UPDATE_ROWS_EVENT);
        flagSet.add(LogEvent.DELETE_ROWS_EVENT_V1);
        flagSet.add(LogEvent.DELETE_ROWS_EVENT);
        flagSet.add(LogEvent.ROTATE_EVENT);
        flagSet.add(LogEvent.SEQUENCE_EVENT);
        flagSet.add(LogEvent.GCN_EVENT);
        flagSet.add(LogEvent.TABLE_MAP_EVENT);
        flagSet.add(LogEvent.XA_PREPARE_LOG_EVENT);
        flagSet.add(LogEvent.XID_EVENT);
        flagSet.add(LogEvent.FORMAT_DESCRIPTION_EVENT);
        return flagSet;
    }

    @Override
    public void onStart() {
        ThreadRecorder recorder = new ThreadRecorder(runningInfo.getStorageInstId());
        recorder.init();
        recorder.dump();
        runtimeContext = new RuntimeContext(recorder);
        runtimeContext.setVersion(polarxVersion);
        runtimeContext.setRecovery(StringUtils.isNotBlank(requestTSO));
        runtimeContext.setStartPosition(startPosition);
        runtimeContext.setServerCharactorSet(serverCharactorSet);
        runtimeContext.setHostAddress(runningInfo.getAddress().getAddress().getHostAddress());
        runtimeContext.setLowerCaseTableNames(lowerCaseTableNames);
        runtimeContext.setAuthenticationInfo(runningInfo);
        head.setRuntimeContext(runtimeContext);
        head.fireStart();
        head.fireStartConsume();
    }

    @Override
    public void handle(LogEvent event, LogPosition logPosition) {
        try {
            runtimeContext.setBinlogFile(logPosition.getFileName());
            runtimeContext.getThreadRecorder().addNetIn(event.getEventLen());
            head.setRuntimeContext(runtimeContext);
            head.doNext(event);
        } catch (Throwable e) {
            if (logPosition != null) {
                String message = String.format("meet fatal error when consume binlog at position %s:%s",
                    logPosition.getFileName(), logPosition.getPosition());
                throw new CanalParseException(message, e);
            } else {
                throw new CanalParseException(e);
            }
        }
    }

    @Override
    public void onEnd() {
        head.fireStop();
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
