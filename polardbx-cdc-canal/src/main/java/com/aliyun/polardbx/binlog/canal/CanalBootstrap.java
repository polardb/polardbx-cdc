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

package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.DefaultBinlogEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CanalBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(CanalBootstrap.class);

    private BinlogEventProcessor processor;

    private AuthenticationInfo authenticationInfo;

    private MySqlInfo mySqlInfo;

    private String polarxServerVersion;

    private String topologyContext;

    private List<LogEventFilter> filterList = new ArrayList<>();
    private LogEventHandler handler;
    private Thread runnableThread;
    private String startCmdTSO;

    public CanalBootstrap(AuthenticationInfo authenticationInfo, String polarxServerVersion, String localBinlogDir,
                          String startCmdTSO) {
        this.authenticationInfo = authenticationInfo;
        this.polarxServerVersion = polarxServerVersion;
        this.processor = new BinlogEventProcessor();
        this.startCmdTSO = startCmdTSO;
    }

    public void setHandler(LogEventHandler handler) {
        this.handler = handler;
    }

    public void addLogFilter(LogEventFilter filter) {
        filterList.add(filter);
    }

    public void start(final String requestTso) throws Exception {
        runnableThread = new Thread(() -> {
            try {
                doStart(requestTso);
                logger.warn("maybe rds master slave ha switch, will stop and restart task!");
                stop();
                Runtime.getRuntime().halt(1);
            } catch (ConsumeOSSBinlogEndException e) {
                logger.warn("oss consume end! will wait 30s!");
                // 等待30s
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                } catch (InterruptedException interruptedException) {

                }
                logger.warn("oss consume end!");
                Runtime.getRuntime().halt(1);
            } catch (Throwable e) {
                logger.error("do start dumper failed!", e);
                Runtime.getRuntime().halt(1);
            }
        }, "canal-dumper-" + authenticationInfo.getStorageInstId());
        runnableThread.setDaemon(true);
        runnableThread.start();
    }

    public void stop() {
        logger.warn("stop canal bootstrap");
        if (runnableThread != null) {
            runnableThread.interrupt();
        }
        processor.stop();
        logger.warn("success stop canal bootstrap");
    }

    private void doStart(String requestTso) throws Exception {
        mySqlInfo = new MySqlInfo();
        MysqlConnection connection = new MysqlConnection(authenticationInfo);
        connection.connect();
        mySqlInfo.init(connection);
        logger.info("start dump with server id " + mySqlInfo.getServerId());
        logger.info("start dump with server Charactor " + mySqlInfo.getServerCharactorSet());
        logger.info("start dump with server position region in [" + mySqlInfo.getStartPosition() + "," + mySqlInfo
            .getEndPosition() + "]");
        connection.disconnect();
        consumeMysql(connection, requestTso);
    }

    private void consumeMysql(ErosaConnection connection, String requestTso) throws Exception {
        long realTso = -1;
        if (StringUtils.isNotBlank(requestTso)) {
            realTso = CommonUtils.getTsoTimestamp(requestTso);
        }
        logger.info("dump mysql with start tso " + realTso);
        BinlogPosition position = searchPosition(connection, realTso);
        if (position != null) {
            consume(connection, position, requestTso);
            return;
        }
        logger.warn("can not find " + requestTso + " in [" + mySqlInfo.getStartPosition() + "," + mySqlInfo
            .getEndPosition() + "] try backup store!");
        consumeFromBackup(requestTso);
    }

    private void consumeFromBackup(String requestTso) throws Exception {
        throw new UnsupportedOperationException("backup store is not support.");
    }

    private void consume(ErosaConnection connection, BinlogPosition startPosition, String requestTso)
        throws Exception {
        logger.info("start consume with tso " + requestTso + " from " + startPosition);
        DefaultBinlogEventHandle handle =
            new DefaultBinlogEventHandle(authenticationInfo, polarxServerVersion, startPosition, requestTso,
                mySqlInfo.getServerCharactorSet(), mySqlInfo.getLowerCaseTableNames(), topologyContext);

        for (LogEventFilter filter : filterList) {
            handle.addFilter(filter);
        }

        handle.setEventHandler(handler);

        processor.setHandle(handle);
        processor.init(connection, startPosition.getFileName(), startPosition.getPosition(), false,
            mySqlInfo.getServerCharactorSet());
        try {
            processor.start();
        } finally {
            handle.onEnd();

        }
    }

    private BinlogPosition searchPosition(ErosaConnection connection, long requestTso) throws Exception {
        logger.info("search position by tso : " + requestTso);
        long startCmdTSO = -1;
        if (StringUtils.isNotBlank(this.startCmdTSO)) {
            startCmdTSO = CommonUtils.getTsoTimestamp(this.startCmdTSO);
        }
        SearchTsoEventHandle searchTsoEventHandle =
            new SearchTsoEventHandle(requestTso, authenticationInfo, startCmdTSO);
        processor.setHandle(searchTsoEventHandle);
        connection.connect();
        BinlogPosition endPosition = connection.findEndPosition(requestTso);
        String searchFile = endPosition.getFileName();
        while (true) {
            searchTsoEventHandle.reset();
            processor.init(connection.fork(), searchFile, 0, true, mySqlInfo.getServerCharactorSet());
            long binlogFileSize = connection.binlogFileSize(searchFile);
            if (binlogFileSize == -1) {
                //找不到这个文件，直接break
                break;
            }
            logger.info("start search " + requestTso + " in " + searchFile);
            searchTsoEventHandle.setCurrentFile(searchFile);
            searchTsoEventHandle
                .setEndPosition(new BinlogPosition(searchFile, binlogFileSize, -1, -1));
            processor.start();
            BinlogPosition startPosition = searchTsoEventHandle.searchResult();
            topologyContext = searchTsoEventHandle.getTopologyContext();
            if (startPosition != null) {
                return startPosition;
            }
            searchFile = connection.preFileName(searchFile);
            if (searchFile == null) {
                break;
            }
        }
        BinlogPosition startPosition = searchTsoEventHandle.getCommandPosition();
        if (startPosition != null) {
            topologyContext = searchTsoEventHandle.getTopologyContext();
            return startPosition;
        }
        return null;
    }

}
