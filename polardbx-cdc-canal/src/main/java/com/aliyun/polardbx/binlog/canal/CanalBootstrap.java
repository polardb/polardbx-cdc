/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDumpContext;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.SearchMetricsManager;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.BinarySearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.DefaultBinlogEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.ISearchTsoEventHandle;
import com.aliyun.polardbx.binlog.canal.core.handle.SearchTsoEventHandleV2;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.canal.exception.ServerIdNotMatchException;
import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_FORCED;

public class CanalBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(CanalBootstrap.class);

    @Getter
    private final BinlogEventProcessor processor;
    private final AuthenticationInfo authenticationInfo;
    private final String startCmdTSO;
    private final String polarxServerVersion;
    private final String localBinlogDir;
    private final List<LogEventFilter<?>> filterList = new ArrayList<>();
    private final LinkedList<String> searchQueue = new LinkedList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Long preferHostId;
    private MySqlInfo mySqlInfo;
    private DefaultBinlogEventHandle handle;
    private LogEventHandler<?> handler;
    private Thread runnableThread;

    public CanalBootstrap(AuthenticationInfo authenticationInfo, String polarxServerVersion, String localBinlogDir,
                          Long preferHostId, String startCmdTSO) {
        this.authenticationInfo = authenticationInfo;
        this.localBinlogDir = localBinlogDir;
        this.polarxServerVersion = polarxServerVersion;
        this.processor = new BinlogEventProcessor();
        this.preferHostId = preferHostId;
        this.startCmdTSO = startCmdTSO;
    }

    public void setHandler(LogEventHandler<?> handler) {
        this.handler = handler;
    }

    public void addLogFilter(LogEventFilter<?> filter) {
        filterList.add(filter);
    }

    public void start(final String requestTso) {
        if (running.compareAndSet(false, true)) {
            runnableThread = new Thread(() -> {
                try {
                    doStart(requestTso);
                    logger.warn("maybe rds master slave ha switch, will stop and restart task!");
                    if (running.get()) {
                        stop();
                        Runtime.getRuntime().halt(1);
                    }
                } catch (ConsumeOSSBinlogEndException e) {
                    logger.warn("oss consume end! will wait 30s!");
                    // 等待30s
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                    } catch (InterruptedException interruptedException) {
                        //do nothing
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
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.warn("stop canal bootstrap");
            if (runnableThread != null) {
                runnableThread.interrupt();
            }
            processor.stop();
            logger.warn("success stop canal bootstrap");
        }
    }

    public String getPositionRegion() {
        return "[" + mySqlInfo.getStartPosition() + "," + mySqlInfo.getEndPosition() + "]";
    }

    /**
     * just for search test
     * @param mySqlInfo
     */
    public void searchTestInit(MySqlInfo mySqlInfo){
        this.mySqlInfo = mySqlInfo;
        this.running.set(true);
    }

    public void doStart(String requestTso) throws Exception {
        mySqlInfo = new MySqlInfo();
        MysqlConnection connection = new MysqlConnection(authenticationInfo);
        connection.connect();
        mySqlInfo.init(connection);
        logger.info("start dump with server id {}", mySqlInfo.getServerId());
        logger.info("start dump with server Character {}", mySqlInfo.getServerCharactorSet());
        logger.info("start dump with server position region in {}", getPositionRegion());
        connection.disconnect();

        boolean forceConsumeBackup = DynamicApplicationConfig.getBoolean(TASK_DUMP_OFFLINE_BINLOG_FORCED);
        if (forceConsumeBackup) {
            logger.info("start consuming binlog from backup in force mode.");
            consumeOss(requestTso, false);
        } else {
            consumeMysql(connection, requestTso);
        }
    }

    public boolean consumeMysqlDirect(ErosaConnection connection) throws IOException {
        int curFileIdx = searchQueue.indexOf(processor.currentFileName());
        if (curFileIdx != -1 && curFileIdx < searchQueue.size()) {
            String nextFile = searchQueue.get(curFileIdx + 1);
            if (nextFile != null && Integer.parseInt(StringUtils.substringAfter(nextFile, ".")) == 1) {
                logger.info("detect rds local continues file exists , continue consume after oss finished! {}",
                    nextFile);
                processor.resetNextLogPosition(nextFile);
                handle.markDnTransferBarrier();
                processor.restore(connection);
                return true;
            }
        }
        if (processor.isServerIdMatch()) {
            // 尝试继续消费
            logger.info("continue consume after oss finished!");
            processor.restore(connection);
            return true;
        }
        logger.info("try consume mysql direct after oss failed!");
        return false;
    }

    public void consumeMysql(MysqlConnection connection, String requestTso) throws Exception {
        do {
            SearchMetricsManager.getInstance().startSearch();
            String storageMasterInstId = getStorageMasterInstId();
            BinlogPosition position = searchPosition(connection, requestTso);
            if (position != null) {
                SearchMetricsManager.getInstance().stopSearch(storageMasterInstId);
                consume(connection, position, requestTso);
                return;
            }
            if (!isRunning()) {
                return;
            }
            logger.warn("can not find {} in {} try oss!", requestTso, getPositionRegion());
            try {
                consumeOss(requestTso, connection.hasMoreNode());
            } catch (ConsumeOSSBinlogEndException e) {
                if (!consumeMysqlDirect(connection)) {
                    stopProcessor();
                    throw e;
                }
                SearchMetricsManager.getInstance().stopSearch(storageMasterInstId);
            } catch (ServerIdNotMatchException e) {
                connection.switchNextFollower();
                logger.warn("oss server id not match local, will try connect follower to continue!");
                connection.disconnect();
                resetProcessorHandle();
                continue;
            }
            break;
        } while (true);

    }

    public String getStorageMasterInstId() {
        return authenticationInfo.getStorageMasterInstId();
    }

    public void stopProcessor() {
        getProcessor().stop();
    }

    public void resetProcessorHandle() {
        getProcessor().setHandle(null);
    }

    public OssConnection buildOssConnection(String requestTso) throws IOException {
        long requestTime = -1;
        if (StringUtils.isNotBlank(requestTso)) {
            requestTime = CommonUtils.getTsoPhysicalTime(requestTso, TimeUnit.MILLISECONDS);
        }
        OssConnection connection =
            new OssConnection(authenticationInfo.getStorageMasterInstId(), authenticationInfo.getUid(),
                authenticationInfo.getBid(), localBinlogDir, preferHostId, mySqlInfo.getServerId(), requestTime);
        connection.connect();
        return connection;
    }

    public void consumeOss(String requestTso, boolean checkServerIdMatch) throws Exception {
        do {
            OssConnection connection = null;
            try {
                connection = buildOssConnection(requestTso);
                if (checkServerIdMatch && !connection.isServerIdMatch()) {
                    // dn remote build will not match
                    logger.error("oss binlog server id not match mysql, will try other mysql host(follower) !");
                    throw new ServerIdNotMatchException();
                }
                BinlogPosition position = searchPosition(connection, requestTso);
                if (!isRunning()) {
                    return;
                }
                if (position == null) {
                    logger.error("can not find position from oss and will try another oss host, tso is {}", requestTso);
                    //清空一下handler，重新初始化
                    resetProcessorHandle();
                    connection.tryOtherHost();
                    checkServerIdMatch = false;
                    continue;
                }
                consume(connection, position, requestTso);

                try {
                    connection.cleanDir();
                } catch (Exception e) {
                    // 消费完后，自动删除目录
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            throw new ConsumeOSSBinlogEndException();
        } while (true);

    }

    public void consume(ErosaConnection connection, BinlogPosition startPosition, String requestTso) throws Exception {
        logger.info("start consume with tso " + requestTso + " from " + startPosition);
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_DUMP);
        handle = new DefaultBinlogEventHandle(authenticationInfo, polarxServerVersion, startPosition, requestTso,
            mySqlInfo.getServerCharactorSet(), mySqlInfo.getLowerCaseTableNames(), mySqlInfo.getSqlMode());

        for (LogEventFilter<?> filter : filterList) {
            handle.addFilter(filter);
        }

        handle.setEventHandler(handler);

        processor.setSearchRecorder(null);
        processor.setHandle(handle);
        processor.init(connection, startPosition.getFileName(), startPosition.getPosition(), false,
            mySqlInfo.getServerCharactorSet(), mySqlInfo.getServerId(), mySqlInfo.getBinlogChecksum());
        processor.start();
    }

    public ISearchTsoEventHandle prepareSearchHandler(long searchTso) {
        ISearchTsoEventHandle searchTsoEventHandle;
        boolean quickMode = SearchMode.isSearchInQuickMode();
        if (quickMode) {
            // 每次直接清空handler，当搜索oss时，根据tso定位文件。
            logger.warn("quick mode will reset search handler");
            processor.setHandle(null);
        }
        if (processor.getHandle() == null) {
            long startCmdTSO = -1;
            if (StringUtils.isNotBlank(this.startCmdTSO)) {
                startCmdTSO = CommonUtils.getTsoTimestamp(this.startCmdTSO);
            }
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            searchTsoEventHandle =
                new SearchTsoEventHandleV2(authenticationInfo, searchTso, startCmdTSO, quickMode, clusterId);
            processor.setHandle(searchTsoEventHandle);
        }
        return (ISearchTsoEventHandle) processor.getHandle();
    }

    public String buildSearchFile(ErosaConnection connection, ISearchTsoEventHandle searchTsoEventHandle,
                                  long searchTso) {

        String lastSearchFile = searchTsoEventHandle.getLastSearchFile();
        logger.info("last search file : {}", lastSearchFile);
        String searchFile;
        if (StringUtils.isBlank(lastSearchFile)) {
            BinlogPosition endPosition = connection.findEndPosition(searchTso);
            searchFile = endPosition.getFileName();
        } else {
            searchFile = connection.preFileName(lastSearchFile);
        }
        return searchFile;
    }

    public SearchRecorder buildRecorder(ErosaConnection connection, long searchTso) {
        SearchRecorder searchRecorder =
            SearchMetricsManager.getInstance().getSearchRecorder(authenticationInfo.getStorageMasterInstId());
        if (!(connection instanceof MysqlConnection)) {
            searchRecorder.setLocal(false);
        }
        if (searchTso > 0) {
            searchRecorder.setSearchTime(CommonUtils.tso2physicalTime(searchTso, TimeUnit.MILLISECONDS));
        }
        searchRecorder.setQueueList(connection.binlogList());
        processor.setSearchRecorder(searchRecorder);
        return searchRecorder;
    }

    public long extractPhysicalTso(String requestTso) {
        if (requestTso != null && requestTso.length() > 19) {
            return CommonUtils.getTsoTimestamp(requestTso);
        }
        return -1;
    }

    public void initSearchProcessor(ErosaConnection connection, String searchFile) throws IOException {
        processor.init(connection.fork(), searchFile, 0, true, mySqlInfo.getServerCharactorSet(), null,
            mySqlInfo.getBinlogChecksum());
    }

    public boolean doSearchFile(ErosaConnection connection, String searchFile,
                                ISearchTsoEventHandle searchTsoEventHandle, SearchRecorder searchRecorder,
                                long searchTso) throws Exception {
        initSearchProcessor(connection, searchFile);
        searchFile = getProcessorFile();
        long binlogFileSize = connection.binlogFileSize(searchFile);
        logger.info("start search {} in {}[size={}]", searchTso, searchFile, binlogFileSize);
        searchRecorder.setFileName(searchFile);
        searchRecorder.setSize(binlogFileSize);
        if (binlogFileSize == -1) {
            //找不到这个文件，直接break
            String errorMsg =
                String.format("search %d in %s failed because file size is -1, will break this loop", searchTso,
                    searchFile);
            logger.info(errorMsg);
            return false;
        }
        doProcessor(searchTsoEventHandle, searchRecorder, searchFile, binlogFileSize);
        logger.info("end search {} in {}{}", searchTso, searchFile, searchTsoEventHandle.region());
        BinlogPosition startPosition = searchTsoEventHandle.searchResult();
        String topologyContext = searchTsoEventHandle.getTopologyContext();
        if (StringUtils.isNotBlank(topologyContext)) {
            RuntimeContext.setInitTopology(topologyContext);
            RuntimeContext.setInstructionId(searchTsoEventHandle.getCommandId());
        }
        if (startPosition != null) {
            searchRecorder.setFinish(true);
            return false;
        }
        return true;
    }

    public void doProcessor(ISearchTsoEventHandle searchTsoEventHandle, SearchRecorder searchRecorder,
                            String searchFile, long binlogFileSize) throws Exception {
        searchQueue.addFirst(searchFile);
        searchTsoEventHandle.setEndPosition(new BinlogPosition(searchFile, binlogFileSize, -1, -1));
        searchRecorder.setQuickMode(searchTsoEventHandle.isInQuickMode());
        processor.start();
        processor.stop();
    }

    public String getProcessorFile() {
        return processor.currentFileName();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 调整代码逻辑，先倒序搜索mysql 本地binlog，如果本地binlog没有对应的记录，则倒序搜索oss的文件
     * 1、 先搜leader 文件， 没有判断oss 是否有leader文件，有则继续搜索
     * 2、 没有leader文件，则断开leader， 连follower 节点
     * 3、 搜follower 本地文件，没有则搜索oss， 判断oss 是否有follower文件，有则继续处理
     * 4、 如果没有找到，则按照region 右侧边界最大的列表处理。
     */
    public BinlogPosition searchPosition(ErosaConnection connection, String requestTso) throws Exception {
        long searchTso = extractPhysicalTso(requestTso);

        logger.info("starting search position by tso : {}", searchTso);
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        connection.connect();
        ISearchTsoEventHandle searchTsoEventHandle = prepareSearchHandler(searchTso);
        String searchFile = buildSearchFile(connection, searchTsoEventHandle, searchTso);
        SearchRecorder searchRecorder = buildRecorder(connection, searchTso);
        while (isRunning()) {
            if (searchTso > 0 && searchTsoEventHandle.isInQuickMode() &&
                DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_RECOVER_SEARCH_TSO_BINARY_SEARCH_IN_QUICK_MODE)){
                return binarySearch(connection, searchTso, searchRecorder, requestTso);
            }
            if (!doSearchFile(connection, searchFile, searchTsoEventHandle, searchRecorder, searchTso)) {
                break;
            }
            searchFile = getProcessorFile();
            searchFile = connection.preFileName(searchFile);
            if (searchFile == null) {
                break;
            }
        }
        BinlogPosition startPosition = searchTsoEventHandle.searchResult();
        if (startPosition != null) {
            return startPosition;
        }
        return searchTsoEventHandle.getCommandPosition();
    }

    public BinarySearchTsoEventHandle prepareBinarySearchHandler(long searchTso, String requestTso){
        return new BinarySearchTsoEventHandle(searchTso, requestTso,  authenticationInfo);
    }

    public BinlogPosition binarySearch(ErosaConnection connection, long searchTso, SearchRecorder searchRecorder, String requestTso)
        throws Exception {
        final long originalSearchTSO = searchTso;
        long pushBackwardSec = DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_RECOVER_SEARCH_TSO_AUTO_QUICK_MODE_SWITCH_PUSH_BACKWARD_SECOND);
        long baseTSO = CommonUtils.getTsoTimestamp(startCmdTSO);
        // 如果切换到quick search模式， 往前多推进一段时间，默认一分钟
        searchTso = originalSearchTSO - CommonUtils.convertToTsoUnit(pushBackwardSec, TimeUnit.SECONDS);
        if (searchTso < baseTSO) {
            logger.warn(
                "try push forward search tso failed, because new search tso {} < base tso {}, will use base tso {}",
                searchTso, baseTSO, baseTSO);
            searchTso = baseTSO;
        }
        BinarySearchTsoEventHandle searchTsoEventHandle = prepareBinarySearchHandler(searchTso, requestTso);
        processor.setHandle(searchTsoEventHandle);
        // 文件排序使用编号
        List<String> binlogList = connection.binlogList();

        BinarySearchALG searchALG = new BinarySearchALG(binlogList.size(), searchTso);
        long finalSearchTso = searchTso;
        searchALG.search(m -> {
            if (!isRunning()){
                throw new InterruptedException("search position occur interrupted!");
            }
            String searchFileName = binlogList.get(m);
            doSearchFile(connection, searchFileName, searchTsoEventHandle, searchRecorder, finalSearchTso);
            return new BinarySearchALG.Region(searchTsoEventHandle.getMinTSO(), searchTsoEventHandle.getMaxTSO(), searchTsoEventHandle.searchResult(), searchTsoEventHandle.needCheckLossStart());
        });
        searchRecorder.setFinish(true);
        if (searchTsoEventHandle.needCheckLossStart()){
            // 有丢失start的情况，但是binlog都搜索完了， 可能是本地文件，start在oss上了，需要继续走oss
            return null;
        }
        // 直接返回位点
        return searchTsoEventHandle.searchResult();
    }

}
