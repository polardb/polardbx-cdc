/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.DescribeRdsBinlogListApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.api.dbs.gareth.GarethActionFactory;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTaskFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.StorageDownloader;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesURLLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcherFactory;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.util.Shell;
import com.google.common.collect.Maps;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public class OssConnection implements ErosaConnection {

    protected static Logger logger = LoggerFactory.getLogger(OssConnection.class);
    protected HashMap<String, BinlogFile> ossBinlogFileMap = new HashMap<>();
    @Getter
    protected LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
    protected String storageInstanceId;
    protected String uid;
    protected String bid;
    protected Long preferHostId;
    protected Set<Long> ignoreHostIdSet = new HashSet<>();
    protected boolean alreadyTryOther = false;
    protected Long serverId;
    protected long requestTSO;
    protected String localBinlogDir;
    private String lastConnectFile = null;
    private StorageDownloader storageDownloader;

    private ExecutorService directConsumeExecutor;

    public OssConnection(String storageInstanceId, String uid, String bid, String localBinlogDir, Long preferHostId,
                         Long serverId, long requestTSO) {
        this.storageInstanceId = storageInstanceId;
        this.uid = uid;
        this.bid = bid;
        this.localBinlogDir = localBinlogDir + File.separator + storageInstanceId;
        this.preferHostId = preferHostId;
        this.serverId = serverId;
        this.requestTSO = requestTSO;
    }

    public void setLogger(Logger logger) {
        OssConnection.logger = logger;
    }

    public void tryOtherHost() {
        if (alreadyTryOther) {
            throw new PositionNotFoundException("try other host also can not find position");
        }

        alreadyTryOther = true;
        disconnect();
    }

    public void release() {
        ossBinlogFileMap.clear();
        binlogFileQueue.clear();
        try {
            cleanDir();
        } catch (Exception e) {
            logger.error("clean dir failed!", e);
        }
    }

    public void cleanDir() throws IOException {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            FileUtils.deleteDirectory(new File(localBinlogDir));
        }
    }

    public List<BinlogFile> callRdsApi(long begin, long end) throws Exception {
        int maxRecordsPerPage =
            DynamicApplicationConfig.getInt(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_RECORDS_PER_PAGE);
        boolean useDbsAPi = DynamicApplicationConfig.getBoolean(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS);
        logger.info("call rds api begin : {} end : {}, maxRecPerSize: {} , use dbsapi : {}", begin, end,
            maxRecordsPerPage, useDbsAPi);
        return DescribeRdsBinlogListApi.describeBinlogFiles(storageInstanceId, uid, bid, begin, end, maxRecordsPerPage,
            useDbsAPi);
    }

    public boolean isInit() {
        return !ossBinlogFileMap.isEmpty();
    }

    public long beginTimestamp() {
        if (requestTSO <= 0) {
            int dayLimit = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RECALL_DAYS_LIMIT);
            return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(dayLimit);
        }
        int lookBackMin = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RECALL_LOOK_BACK_MIN);
        return requestTSO - TimeUnit.MINUTES.toMillis(lookBackMin);
    }

    public void prepareServerIdForDbs(List<BinlogFile> totalRecords) throws IOException {
        Map<Long, Long> serverIdMap = Maps.newHashMap();
        String path = localBinlogDir+File.separator+"server_id";
        for (BinlogFile bf : totalRecords){
            String absFile = path+File.separator+bf.getLogname();
            if (!serverIdMap.containsKey(bf.getInstanceID())){
                String taskId = null;
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH)){
                    String garethConfig = DynamicApplicationConfig.getString(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG);
                    String type = DynamicApplicationConfig.getString(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE);
                    if (StringUtils.isBlank(garethConfig)){
                        DescribeStorageInfoResult
                            result = DbsApi.describeStorageInfo(bf.getStorageEntityId(), DynamicApplicationConfig.getString(ConfigKeys.RDS_UID), DynamicApplicationConfig.getString(ConfigKeys.RDS_BID));
                        type = result.getData().getType();
                        garethConfig = result.getDataJson();
                    }
                    GarethActionFactory.create(type, garethConfig).download(absFile, bf.getDownloadLink(), path);
                }else {
                    RdsDownloadForRestoreResult
                        result = DbsApi.submitDownloadTask(storageInstanceId, uid, bid, bf.getArchiveLogId(), path);
                    taskId = result.getData().getTaskId();
                    while (BinlogFileUtil.readFileSize(absFile) < 20 && !Thread.currentThread().isInterrupted()){
                        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
                        DescribeTaskStatusResult
                            downloadResult = DbsApi.describeTaskStatus(storageInstanceId, uid, bid, taskId);
                        logger.warn("download result : {}", JSON.toJSONString(downloadResult));
                        if (StringUtils.equalsIgnoreCase("Failed", downloadResult.getData().getStatus())){
                            throw new PolardbxException("download file "+bf.getLogname()+" failed by dbs gareth!");
                        }
                    }
                }

                long serverId = BinlogFileUtil.readServerId(absFile);
                serverIdMap.put(bf.getInstanceID(), serverId);
                if (taskId != null){
                    DbsApi.cancelTask(storageInstanceId, uid, bid, taskId);
                }
                String rootPath = DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR);
                String res = Shell.execCommand("sudo", "chown", "-R", "admin:admin", rootPath);
                logger.info("chown for gareth path : {}, res {}",rootPath,  res);
                FileUtils.deleteQuietly(new File(absFile));
            }
        }
        totalRecords.forEach(r->{
            long serverId = serverIdMap.get(r.getInstanceID());
            r.setServerId(serverId);
        });
    }

    public void filterBinlogList(List<BinlogFile> totalRecords) throws Exception {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS)){
            prepareServerIdForDbs(totalRecords);
        }

        List<BinlogFile> binlogFileList =
            BinlogProcessor.process(totalRecords, ignoreHostIdSet, preferHostId, requestTSO, serverId);
        Set<Long> newIgnoreHostIdSet = new HashSet<>();
        for (BinlogFile binlogFile : binlogFileList) {
            newIgnoreHostIdSet.add(binlogFile.getInstanceID());
            binlogFile.initRegionTime();
            ossBinlogFileMap.put(binlogFile.getLogname(), binlogFile);
            if (logger.isDebugEnabled()) {
                logger.debug("add binlog ： {} [{} , {} ] ", binlogFile.getLogname(), binlogFile.getLogBeginTime(),
                    binlogFile.getLogEndTime());
            }

            binlogFileQueue.add(binlogFile);
        }
        ignoreHostIdSet = newIgnoreHostIdSet;
        Long useHostId = null;
        Long serverId = null;
        if (!binlogFileQueue.isEmpty()) {
            useHostId = binlogFileQueue.get(0).getInstanceID();
            serverId = binlogFileQueue.get(0).getServerId();
        }
        logger.info("fetch binlog size : {} and use host : {} with serverId : {}", ossBinlogFileMap.size(), useHostId,
            serverId);
    }

    public void connectBefore() {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            try {
                FileUtils.forceMkdir(new File(this.localBinlogDir));
            } catch (IOException e) {
                logger.error("mkdir {} failed!", this.localBinlogDir, e);
                throw new PolardbxException(e);
            }
        }
    }

    @Override
    public void connect() throws IOException {
        if (isInit()) {
            return;
        }
        connectBefore();
        long end = System.currentTimeMillis();
        // 搜索往前推进1个小时
        final long begin = beginTimestamp();

        try {
            List<BinlogFile> totalRecords = callRdsApi(begin, end);
            filterBinlogList(totalRecords);
        } catch (Exception e) {
            throw new PositionNotFoundException(e);
        }
        // 不是下载模式，且也没有启用dbs 模式，才初始化memory cache
        if (!DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE) &&
            !DynamicApplicationConfig.getBoolean(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS)) {
            initExecutors();
            CacheManager cacheManager = SpringContextHolder.getObject(CacheManager.class);
            cacheManager.registerStorage(storageInstanceId);
        }
    }

    public void initExecutors() {
        final int newThreadNum = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_THREAD_NUM);
        directConsumeExecutor =
            new ThreadPoolExecutor(newThreadNum, newThreadNum, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(8192),
                r -> {
                    Thread t = new Thread(r, storageInstanceId + "oss-download-thread");
                    t.setDaemon(true);
                    return t;
                });
    }

    public void shutdownExecutors() {
        if (this.directConsumeExecutor != null) {
            this.directConsumeExecutor.shutdownNow();
            logger.info("shutdown direct consume executor");
        }
    }

    @Override
    public void reconnect() throws IOException {

    }

    @Override
    public void disconnect() {
        release();
        stopAsyncDownload();
        shutdownExecutors();
        CacheManager cacheManager = SpringContextHolder.getObject(CacheManager.class);
        cacheManager.unregisterStorage(storageInstanceId);
    }

    @Override
    public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {

    }

    @Override
    public void dump(String binlogfilename, Long binlogPosition, Long startTimestampMills, SinkFunction func)
        throws Exception {

    }

    @Override
    public void dump(long timestamp, SinkFunction func) throws Exception {

    }

    @Override
    public void dump(GTIDSet gtidSet, SinkFunction func) throws Exception {

    }

    public void printBinlogQueue() {
        for (BinlogFile bf : binlogFileQueue) {
            logger.error("{}{}", bf.getLogname(),
                MessageFormat.format("[ {0} , {1} , {2}]", bf.getLogBeginTime(), bf.getLogEndTime(),
                    bf.getDownloadLink()));
        }
    }

    @Override
    public ErosaConnection fork() {
        return this;
    }

    public String getLastLogName() {
        return binlogFileQueue.getLast().getLogname();
    }

    public BinlogFile getBinlogFile(String binlogName) {
        return ossBinlogFileMap.get(binlogName);
    }

    @Override
    public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search) throws IOException {
        if (binlogfilename == null && lastConnectFile == null) {
            // 可能是发生了实例迁移
            binlogfilename = getLastLogName();
            logger.warn("may be dn transfer to new binlog sequence, will use max oss file continue :{}",
                binlogfilename);
        }
        BinlogFile ossBinlogFile = getBinlogFile(binlogfilename);
        if (ossBinlogFile == null) {
            logger.error("can not find binlog file : {} from oss!", binlogfilename);
            throw new PositionNotFoundException();
        }
        lastConnectFile = binlogfilename;

        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE) ||
        DynamicApplicationConfig.getBoolean(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS)) {
            return providerLocalFetcher(ossBinlogFile, binlogPosition, search);
        } else {
            return providerContinuesRemoteUrlFetcher(ossBinlogFile, binlogPosition);
        }
    }

    public String getLastConnectFile() {
        return lastConnectFile;
    }

    public FileLogFetcher createFileLogFetcher() {
        return new FileLogFetcher();
    }

    public LogFetcher providerLocalFetcher(BinlogFile ossBinlogFile, long binlogPosition, boolean search)
        throws IOException {
        String binlogFilename = ossBinlogFile.getLogname();
        String path = localBinlogDir + File.separator + binlogFilename;
        File f = new File(path);
        try {
            logger.info("sync download first file {} before", binlogFilename);
            DownloadTask downloadTask =
                DownloadTaskFactory.createDownloadTask(storageInstanceId, ossBinlogFile,
                    path);
            downloadTask.exec();
        } catch (Exception e) {
            FileUtils.deleteQuietly(f);
            throw new PositionNotFoundException(e);
        }
        FileLogFetcher fetcher = createFileLogFetcher();
        fetcher.open(path, binlogPosition);
        logger.info("provider fetcher file ： {} size ： {} pos : {}", path, f.length(), binlogPosition);

        if (!search) {
            //只有dump 阶段启动异步下载
            startAsyncDownload(ossBinlogFile);
        }

        return new ContinuesFileLogFetcher(storageInstanceId, fetcher, localBinlogDir, ossBinlogFile, binlogFileQueue,
            storageDownloader);
    }

    public StorageDownloader createNewDownloader() {
        return new StorageDownloader(storageInstanceId, localBinlogDir);
    }

    /**
     * 按顺序下载binlog文件， 直到当前已经搜过文件为止
     *
     * @param lastBinlogFile 最近一个已经搜索过的文件
     */
    private void startAsyncDownload(BinlogFile lastBinlogFile) {
        if (storageDownloader != null) {
            logger.warn(
                "start async download detected old storage download exists, will exist old downloader and start new!");
            try {
                storageDownloader.stop();
            } catch (InterruptedException e) {
                throw new PolardbxException(e);
            }
        }
        storageDownloader = createNewDownloader();
        LinkedList<BinlogFile> binlogFileQueue = getBinlogFileQueue();
        int idx = binlogFileQueue.indexOf(lastBinlogFile);
        for (int i = idx; i < binlogFileQueue.size(); i++) {
            BinlogFile bf = binlogFileQueue.get(i);
            if (logger.isDebugEnabled()) {
                logger.debug("add download binlog : " + bf.getLogname());
            }
            DownloadTask downloadTask =
                DownloadTaskFactory.createDownloadTask(storageInstanceId, bf,
                    localBinlogDir + File.separator + bf.getLogname());
            storageDownloader.addTask(downloadTask);
        }
        // 启动下载器
        storageDownloader.start();
    }

    public void stopAsyncDownload() {
        if (storageDownloader != null) {
            try {
                storageDownloader.stop();
            } catch (InterruptedException e) {
                throw new PolardbxException(e);
            }
            storageDownloader = null;
        }
    }

    public LogFetcher providerContinuesRemoteUrlFetcher(BinlogFile ossBinlogFile, long binlogPosition)
        throws IOException {
        URLLogFetcher fetcher = providerRemoteUrlFetcher(ossBinlogFile, binlogPosition);
        return new ContinuesURLLogFetcher(storageInstanceId, fetcher, ossBinlogFile, binlogFileQueue,
            directConsumeExecutor);
    }

    public URLLogFetcher providerRemoteUrlFetcher(BinlogFile ossBinlogFile, long binlogPosition) throws IOException {
        URLLogFetcher fetcher = LogFetcherFactory.createURLLogFetcher(storageInstanceId, ossBinlogFile.getLogname());
        fetcher.open(ossBinlogFile.getIntranetDownloadLink(), binlogPosition, ossBinlogFile.getFileSize(),
            directConsumeExecutor);
        logger.info("provider fetcher url fetcher url ： {} pos : {}", ossBinlogFile.getDownloadLink(), binlogPosition);
        return fetcher;
    }

    @Override
    public BinlogPosition findEndPosition(Long tso) {
        BinlogFile endFile = null;
        if (tso > 0) {
            long timeInMill = CommonUtils.tso2physicalTime(tso, TimeUnit.MILLISECONDS);
            try {
                endFile = searchFileFromTime(timeInMill);
                logger.info("search file from tso timestamp {}", JSON.toJSONString(endFile));
            } catch (Exception e) {
                logger.error("search binlog file from tso failed!", e);
            }
        }
        if (endFile == null) {
            endFile = binlogFileQueue.get(binlogFileQueue.size() - 1);
        }
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            File f = new File(localBinlogDir + File.separator + endFile.getLogname());
            return new BinlogPosition(endFile.getLogname(), f.exists() ? f.length() : Long.MAX_VALUE, -1, -1);
        }
        return new BinlogPosition(endFile.getLogname(), 0, -1, -1);
    }

    private BinlogFile searchFileFromTime(Long timeInMill) throws ParseException {
        for (BinlogFile binlogFile : binlogFileQueue) {
            if (binlogFile.contain(timeInMill)) {
                return binlogFile;
            }
        }
        return null;
    }

    @Override
    public long binlogFileSize(String searchFileName) throws IOException {
        BinlogFile binlogFile = ossBinlogFileMap.get(searchFileName);
        if (binlogFile != null) {
            return binlogFile.getFileSize();
        } else {
            return -1L;
        }
    }

    @Override
    public String preFileName(String currentFileName) {
        String nextFileName = BinlogFileUtil.getPrevBinlogFileName(currentFileName);
        BinlogFile nextFile = ossBinlogFileMap.get(nextFileName);
        if (nextFile != null) {
            return nextFileName;
        }
        return null;
    }

    @Override
    public List<String> binlogList() {
        return binlogFileQueue.stream().map(BinlogFile::getLogname).collect(Collectors.toList());
    }

    public boolean isServerIdMatch() {
        return Objects.equals(this.serverId, ossBinlogFileMap.values().iterator().next().getServerId());
    }

}
