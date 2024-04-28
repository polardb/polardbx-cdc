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
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesURLLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.gtid.GTIDSet;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OssConnection implements ErosaConnection {

    protected static final Logger logger = LoggerFactory.getLogger(OssConnection.class);
    protected HashMap<String, BinlogFile> ossBinlogFileMap = new HashMap<>();
    protected LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
    protected String storageInstanceId;
    protected String uid;
    protected String bid;
    protected Long preferHostId;
    protected Set<Long> ignoreHostIdSet = new HashSet<>();
    protected boolean alreadyTryOther = false;
    protected boolean test = false;
    protected int recallInterval = 10;
    protected Long serverId;
    protected long requestTSO;
    protected String localBinlogDir;
    private String lastConnectFile = null;

    public OssConnection(String storageInstanceId, String uid, String bid, String localBinlogDir,
                         Long preferHostId, int recallInterval, Long serverId,
                         long requestTSO) {
        this.storageInstanceId = storageInstanceId;
        this.uid = uid;
        this.bid = bid;
        this.localBinlogDir = localBinlogDir + File.separator + storageInstanceId;
        this.preferHostId = preferHostId;
        this.recallInterval = recallInterval;
        this.serverId = serverId;
        this.requestTSO = requestTSO;
    }

    public static String fetchFileName(String downloadLink) {
        int prefix = downloadLink.lastIndexOf("/");
        int suffix = downloadLink.indexOf("?");
        String fileName = downloadLink.substring(prefix + 1, suffix);
        return fileName;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public void tryOtherHost() {
        if (alreadyTryOther) {
            throw new PositionNotFoundException("try other host also can not find position");
        }

        alreadyTryOther = true;
        ossBinlogFileMap.clear();
        binlogFileQueue.clear();
        try {
            cleanDir();
        } catch (Exception e) {
            logger.error("clean dir failed!", e);
        }
    }

    private void cleanDir() throws IOException {
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            FileUtils.deleteDirectory(new File(localBinlogDir));
        }
    }

    @Override
    public void connect() throws IOException {
        if (!ossBinlogFileMap.isEmpty()) {
            return;
        }
        BinlogProcessor.test = test;
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            try {
                FileUtils.forceMkdir(new File(this.localBinlogDir));
            } catch (IOException e) {
                logger.error("mkdir " + this.localBinlogDir + " failed!", e);
                throw new PolardbxException(e);
            }
        }
        long end = System.currentTimeMillis();
        // 搜索最近10天
        long begin = end - TimeUnit.DAYS.toMillis(recallInterval);
        int counts = 0;
        int pageNumber = 1;
        Long _preId = preferHostId;
        Set<Long> _ignoreHostIdSet = new HashSet<>();
        try {
            List<BinlogFile> totalRecords = new ArrayList<>();
            do {
                DescribeBinlogFilesResult result = RdsApi
                    .describeBinlogFiles(storageInstanceId, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                        RdsApi.formatUTCTZ(new Date(end)),
                        500,
                        pageNumber++);
                logger.info(
                    "rds api result item size : " + result.getItems().size() + " request server id : " + serverId);
                totalRecords.addAll(result.getItems());
                counts += result.getItemsNumbers();
                if (result.getTotalRecords() <= counts) {
                    break;
                }
            } while (true);
            List<BinlogFile> binlogFileList =
                BinlogProcessor.process(totalRecords, ignoreHostIdSet, _preId, requestTSO, serverId);
            for (BinlogFile binlogFile : binlogFileList) {
                _ignoreHostIdSet.add(binlogFile.getInstanceID());
                binlogFile.initRegionTime();
                ossBinlogFileMap.put(binlogFile.getLogname(), binlogFile);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "add binlog ： " + binlogFile.getLogname() + " [" + binlogFile.getLogBeginTime() + " , "
                            + binlogFile.getLogEndTime() + " ] ");
                }

                binlogFileQueue.add(binlogFile);
            }
        } catch (Exception e) {
            throw new PositionNotFoundException(e);
        }
        ignoreHostIdSet = _ignoreHostIdSet;
        logger.info("fetch binlog size : " + ossBinlogFileMap.size());
    }

    @Override
    public void reconnect() throws IOException {

    }

    @Override
    public void disconnect() throws IOException {

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
            logger
                .error(bf.getLogname() + MessageFormat
                    .format("[ {0} , {1} , {2}]", bf.getLogBeginTime(), bf.getLogEndTime(), bf.getDownloadLink()));
        }
    }

    @Override
    public ErosaConnection fork() {
        return this;
    }

    @Override
    public LogFetcher providerFetcher(String binlogfilename, long binlogPosition, boolean search) throws IOException {
        if (binlogfilename == null && lastConnectFile == null) {
            // 可能是发生了实例迁移
            binlogfilename = binlogFileQueue.getLast().getLogname();
            logger.warn("may be dn transfer to new binlog sequence, will use max oss file continue :" + binlogfilename);
        }
        BinlogFile ossBinlogFile = ossBinlogFileMap.get(binlogfilename);
        if (ossBinlogFile == null) {
            logger.error("can not find binlog file : " + binlogfilename + " from oss!");
            throw new PositionNotFoundException();
        }
        lastConnectFile = binlogfilename;

        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE)) {
            return providerLocalFetcher(ossBinlogFile, binlogPosition, search);
        } else {
            return providerRemoteUrlFetcher(ossBinlogFile, binlogPosition);
        }
    }

    public String getLastConnectFile() {
        return lastConnectFile;
    }

    private LogFetcher providerLocalFetcher(BinlogFile ossBinlogFile, long binlogPosition, boolean search)
        throws IOException {
        String binlogfilename = ossBinlogFile.getLogname();
        String path = localBinlogDir + File.separator + binlogfilename;
        File f = new File(path);
        try {
            if (!f.exists()) {
                HttpHelper
                    .download(ossBinlogFile.getIntranetDownloadLink(), path);
            }
        } catch (Exception e) {
            f.delete();
            throw new PositionNotFoundException(e);
        }
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(path, binlogPosition);
        logger.info("provider fetcher file ： " + path + " size ： " + f.length() + " pos : " + binlogPosition);
        ContinuesFileLogFetcher wrapper =
            new ContinuesFileLogFetcher(storageInstanceId, fetcher, localBinlogDir, ossBinlogFile, binlogFileQueue,
                !search);
        return wrapper;
    }

    private LogFetcher providerRemoteUrlFetcher(BinlogFile ossBinlogFile, long binlogPosition) throws IOException {
        URLLogFetcher fetcher = new URLLogFetcher();
        fetcher.open(ossBinlogFile.getIntranetDownloadLink(), binlogPosition, ossBinlogFile.getFileSize());
        logger.info("provider fetcher url fetcher url ： " + ossBinlogFile.getDownloadLink() + " pos : "
            + binlogPosition);
        ContinuesURLLogFetcher wrapper =
            new ContinuesURLLogFetcher(storageInstanceId, fetcher, ossBinlogFile, binlogFileQueue);
        return wrapper;
    }

    @Override
    public BinlogPosition findEndPosition(Long tso) {
        BinlogFile endFile = null;
        if (tso > 0) {
            long timeInMill = CommonUtils.tso2physicalTime(tso, TimeUnit.MILLISECONDS);
            try {
                endFile = searchFileFromTime(timeInMill);
                logger.info("search file from tso timestamp " + JSON.toJSONString(endFile));
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
        int split = currentFileName.indexOf(".");
        int binlogSeqNum = Integer
            .parseInt(currentFileName.substring(split + 1));
        binlogSeqNum--;
        if (binlogSeqNum < 1) {
            return null;
        }
        String nextFileName = currentFileName.substring(0, split) + "." + StringUtils
            .leftPad(binlogSeqNum + "", currentFileName.length() - split - 1, "0");
        BinlogFile nextFile = ossBinlogFileMap.get(nextFileName);
        if (nextFile != null) {
            return nextFileName;
        }
        return null;
    }
}
