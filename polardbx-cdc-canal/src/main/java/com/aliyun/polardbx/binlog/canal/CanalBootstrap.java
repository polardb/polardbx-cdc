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
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDumpContext;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.DefaultBinlogEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.ISearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV1;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV2;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_RDSBINLOG_DOWNLOAD_RECALLDAYS;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_RDSBINLOG_FORCE_CONSUME_BACKUP;

public class CanalBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(CanalBootstrap.class);
    private static final Logger searchLogger = LoggerFactory.getLogger("searchLogger");

    private BinlogEventProcessor processor;

    private AuthenticationInfo authenticationInfo;

    private MySqlInfo mySqlInfo;

    private String polarxServerVersion;

    private String localBinlogDir;

    private List<LogEventFilter> filterList = new ArrayList<>();
    private LogEventHandler handler;
    private Thread runnableThread;
    private Long preferHostId;
    private String startCmdTSO;

    public CanalBootstrap(AuthenticationInfo authenticationInfo, String polarxServerVersion, String localBinlogDir,
                          Long preferHostId, String startCmdTSO) {
        this.authenticationInfo = authenticationInfo;
        this.localBinlogDir = localBinlogDir;
        this.polarxServerVersion = polarxServerVersion;
        this.processor = new BinlogEventProcessor();
        this.preferHostId = preferHostId;
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

        boolean forceConsumeBackup = DynamicApplicationConfig.getBoolean(TASK_RDSBINLOG_FORCE_CONSUME_BACKUP);
        if (forceConsumeBackup) {
            logger.info("start consuming binlog from backup in force mode.");
            consumeOss(requestTso);
        } else {
            consumeMysql(connection, requestTso);
        }
    }

    private void consumeMysql(ErosaConnection connection, String requestTso) throws Exception {
        long realTso = -1;
        if (StringUtils.isNotBlank(requestTso)) {
            realTso = CommonUtils.getTsoTimestamp(requestTso);
        }
        logger.info("dump mysql with start tso " + realTso);
        BinlogPosition position = searchPosition(connection, requestTso, realTso);
        if (position != null) {
            consume(connection, position, requestTso);
            return;
        }
        logger.warn("can not find " + requestTso + " in [" + mySqlInfo.getStartPosition() + "," + mySqlInfo
            .getEndPosition() + "] try oss!");
        try {
            consumeOss(requestTso);
        } catch (ConsumeOSSBinlogEndException e) {
            if (processor.isServerIdMatch()) {
                // 尝试继续消费
                logger.info("continue consume after oss finished!");
                processor.restore(connection);
                return;
            }
            processor.stop();
            throw e;
        }
    }

    private void consumeOss(String requestTso) throws Exception {
        long requestTime = -1;
        if (StringUtils.isNotBlank(requestTso)) {
            requestTime = CommonUtils.getTsoPhysicalTime(requestTso, TimeUnit.MILLISECONDS);
        }
        OssConnection connection = new OssConnection(authenticationInfo.getStorageInstId(), authenticationInfo.getUid(),
            authenticationInfo.getBid(), localBinlogDir, preferHostId,
            DynamicApplicationConfig.getInt(TASK_RDSBINLOG_DOWNLOAD_RECALLDAYS), mySqlInfo.getServerId(), requestTime);
        do {
            long realTso = -1;
            if (requestTso != null && requestTso.length() > 19) {
                realTso = CommonUtils.getTsoTimestamp(requestTso);
            }
            BinlogPosition position = searchPosition(connection, requestTso, realTso);
            if (position == null) {
                logger.error("can not find position from oss, tso is " + requestTso);
                //清空一下handler，重新初始化
                processor.setHandle(null);
                connection.tryOtherHost();
                continue;
            }
            consume(connection, position, requestTso);

            try {
                //保留这块逻辑，删除历史记录
                new File(localBinlogDir + File.separator + authenticationInfo.getStorageInstId()).deleteOnExit();
            } catch (Exception e) {
                // 消费完后，自动删除目录
            }
            throw new ConsumeOSSBinlogEndException();
        } while (true);

    }

    private void consume(ErosaConnection connection, BinlogPosition startPosition, String requestTso)
        throws Exception {
        logger.info("start consume with tso " + requestTso + " from " + startPosition);
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_DUMP);
        DefaultBinlogEventHandle handle =
            new DefaultBinlogEventHandle(authenticationInfo, polarxServerVersion, startPosition, requestTso,
                mySqlInfo.getServerCharactorSet(), mySqlInfo.getLowerCaseTableNames());

        for (LogEventFilter filter : filterList) {
            handle.addFilter(filter);
        }

        handle.setEventHandler(handler);

        processor.setHandle(handle);
        processor.init(connection, startPosition.getFileName(), startPosition.getPosition(), false,
            mySqlInfo.getServerCharactorSet(), mySqlInfo.getServerId(), mySqlInfo.getBinlogChecksum());
        processor.start();
    }

    /**
     * 调整代码逻辑，先倒序搜索mysql 本地binlog，如果本地binlog没有对应的记录，则倒序搜索oss的文件
     * 1、 如果oss上没有找到对应mysql实例的文件，则优先找region最大的
     * 2、 如果oss上找到了对应的mysql实例文件，则继续按照文件名倒序搜索。
     */
    private BinlogPosition searchPosition(ErosaConnection connection, String requestTso, long searchTso)
        throws Exception {
        searchLogger.info("search position by tso : " + searchTso);
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        connection.connect();
        ISearchTsoEventHandle searchTsoEventHandle;
        boolean inQuickMode = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_SEARCHTSO_QUICKMODE);
        if (inQuickMode) {
            // 每次直接清空handler，当搜索oss时，根据tso定位文件。
            processor.setHandle(null);
        }
        if (processor.getHandle() == null) {
            long startCmdTSO = -1;
            if (StringUtils.isNotBlank(this.startCmdTSO)) {
                startCmdTSO = CommonUtils.getTsoTimestamp(this.startCmdTSO);
            }
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_SEARCHTSO_HANDLE_V1)) {

                searchTsoEventHandle =
                    new SearchTsoEventHandleV1(authenticationInfo, requestTso, searchTso, startCmdTSO);
            } else {
                searchTsoEventHandle =
                    new SearchTsoEventHandleV2(authenticationInfo, searchTso, startCmdTSO);
            }
            processor.setHandle(searchTsoEventHandle);
        } else {
            searchTsoEventHandle = (ISearchTsoEventHandle) processor.getHandle();
        }
        String lastSearchFile = searchTsoEventHandle.getLastSearchFile();
        searchLogger.info("last search file : " + lastSearchFile);
        String searchFile;
        if (StringUtils.isBlank(lastSearchFile)) {
            BinlogPosition endPosition = connection.findEndPosition(searchTso);
            searchFile = endPosition.getFileName();
        } else {
            searchFile = connection.preFileName(lastSearchFile);
        }
        while (true) {
            processor.init(connection.fork(), searchFile, 0, true, mySqlInfo.getServerCharactorSet(),
                null, mySqlInfo.getBinlogChecksum());
            long binlogFileSize = connection.binlogFileSize(searchFile);
            if (binlogFileSize == -1) {
                //找不到这个文件，直接break
                break;
            }
            searchLogger.info("start search " + searchTso + " in " + searchFile);
            searchTsoEventHandle
                .setEndPosition(new BinlogPosition(searchFile, binlogFileSize, -1, -1));
            processor.start();
            processor.stop();
            searchLogger.info("end search " + searchTso + " in " + searchFile + searchTsoEventHandle.region());
            BinlogPosition startPosition = searchTsoEventHandle.searchResult();
            String topologyContext = searchTsoEventHandle.getTopologyContext();
            if (StringUtils.isNotBlank(topologyContext)) {
                RuntimeContext.setInitTopology(topologyContext);
            }
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
            RuntimeContext.setInitTopology(searchTsoEventHandle.getTopologyContext());
            return startPosition;
        }
        return null;
    }

}
