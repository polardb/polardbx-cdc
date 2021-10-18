/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
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
    private String topologyContext;

    public DefaultBinlogEventHandle(AuthenticationInfo runningInfo, String polarxVersion,
                                    BinlogPosition startPosition, String requestTSO,
                                    ServerCharactorSet serverCharactorSet, int lowerCaseTableNames,
                                    String topologyContext) {
        this.runningInfo = runningInfo;
        this.polarxVersion = polarxVersion;
        this.startPosition = startPosition;
        this.requestTSO = requestTSO;
        this.serverCharactorSet = serverCharactorSet;
        this.lowerCaseTableNames = lowerCaseTableNames;
        this.topologyContext = topologyContext;
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
    public boolean interupt() {
        return false;
    }

    @Override
    public void onStart() {
        ThreadRecorder recorder = new ThreadRecorder(runningInfo.getStorageInstId());
        recorder.init();
        recorder.dump();
        runtimeContext = new RuntimeContext(recorder);
        runtimeContext.setVersion(polarxVersion);
        if (StringUtils.isNotBlank(requestTSO)) {
            runtimeContext.setRecovery(true);
//            startPosition.setRtso(requestTSO);
        } else {
            runtimeContext.setRecovery(false);
        }
        runtimeContext.setStartPosition(startPosition);
        runtimeContext.setServerCharactorSet(serverCharactorSet);
        runtimeContext.setHostAddress(runningInfo.getAddress().getAddress().getHostAddress());
        runtimeContext.setLowerCaseTableNames(lowerCaseTableNames);
        runtimeContext.setTopology(topologyContext);
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
            throw new CanalParseException(e); // 继续抛出异常，让上层统一感知
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
