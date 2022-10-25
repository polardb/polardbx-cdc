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

package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.ServerIdNotMatchException;
import com.aliyun.polardbx.binlog.download.BinlogProcessor;
import com.aliyun.polardbx.binlog.download.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.download.RdsApi;
import com.aliyun.polardbx.binlog.download.rds.BinlogFile;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.extractor.flashback.ILocalBinlogEventListener;
import com.aliyun.polardbx.rpl.extractor.flashback.LocalBinlogEventParser;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author shicai.xsc 2020/11/29 21:19
 * @since 5.0.0.0
 */
@Slf4j
public class RdsBinlogExtractor extends MysqlBinlogExtractor {

    private BinlogUrlDownloader binlogDownloader;
    private AbstractEventParser localParser;
    private AbstractEventParser remoteParser;
    private List<String> binlogList;
    private HashMap<String, BinlogFile> ossBinlogFileMap = new HashMap<>();
    private boolean allHostIdTestFlag = false;
    private long preferHostId = -1;
    private Set<Long> allHostIds = new HashSet<>();
    private Set<Long> ignoreHostIds = new HashSet<>();
    private Long fixHostInstanceId;
    private long oldServerId;

    public RdsBinlogExtractor(RdsExtractorConfig extractorConfig, HostInfo srcHostInfo, HostInfo metaHostInfo,
                              BinlogPosition position,
                              BaseFilter filter) {
        super(extractorConfig, srcHostInfo, metaHostInfo, position, filter);
        srcAuthInfo.setUid(extractorConfig.getUid());
        srcAuthInfo.setBid(extractorConfig.getBid());
        srcAuthInfo.setStorageInstId(extractorConfig.getRdsInstanceId());
        binlogDownloader = new BinlogUrlDownloader();
        if (extractorConfig.getFixHostInstanceId() != null) {
            fixHostInstanceId = extractorConfig.getFixHostInstanceId();
        }
        if (fixHostInstanceId != null) {
            allHostIdTestFlag = true;
        }
        oldServerId = position.getMasterId();
    }

    @Override
    public void start() {
        log.info("start canal binlog extractor start");
        startInternal();
        running = true;
        log.info("start canal binlog extractor finish");
    }

    public void startInternal() {
        try {
            binlogList = new ArrayList<>();
            binlogDownloader.init();
            initRemoteEventParser();
            // 取src作为meta信息获取方
            LogEventConvert logEventConvert = new ImportLogEventConvert(srcHostInfo, filter, position, HostType.RDS);
            logEventConvert.init();
            // 如果只有时间戳，那么会在remote parser中回溯60秒，然后寻找位点,返回null或者有效位点
            // 如果有binlog file/offset，但server id不同，那么也会回溯60秒，然后寻找位点,返回null或者有效位点
            // 如果有file/offset且server id相同，那么比较下位点和最小位点,返回null或者有效位点
            // 返回null代表需要去oss binlog file里查找
            ((MysqlEventParser) remoteParser).setBinlogParser(logEventConvert);
            MysqlConnection conn = new MysqlConnection(srcAuthInfo);
            ((MysqlEventParser) remoteParser).preDump(conn);
            conn.connect();
            BinlogPosition newPosition = remoteParser.findStartPositionOnceBeforeStart(conn, position);
            conn.disconnect();
            initLocalEventParser();

            // need to download binlog from oss
            if (newPosition == null) {
                log.warn("find no remote start position");
                // 由于应该以主的server id为准
                // 如果主server id和position里的server id不同,则直接清除表位点
                if (remoteParser.getCurrentServerId() != oldServerId) {
                    StatisticalProxy.getInstance().deleteTaskTablePosition();
                }
                long begin = (position.getTimestamp() - RplConstants.FALLBACK_INTERVAL_SECONDS) * 1000;
                long end = System.currentTimeMillis();
                int counts = 0;
                int pageNumber = 1;
                List<BinlogFile> binlogFileList;
                do {
                    DescribeBinlogFilesResult result = RdsApi
                        .describeBinlogFiles(srcAuthInfo.getStorageInstId(),
                            srcAuthInfo.getUid(),
                            srcAuthInfo.getBid(),
                            RdsApi.formatUTCTZ(new Date(begin)),
                            RdsApi.formatUTCTZ(new Date(end)),
                            1000,
                            pageNumber++);
                    log.warn("oss rdsapi result: {}", result.toString());
                    if (result.getItems().isEmpty()) {
                        log.error("oss api returns no binlog file info");
                        return;
                    }
                    allHostIds = new HashSet<>();
                    for (BinlogFile file : result.getItems()) {
                        file.initRegionTime();
                        // 指定instance id
                        if (fixHostInstanceId != null && fixHostInstanceId.equals(file.getInstanceID())) {
                            allHostIds.add(file.getInstanceID());
                            break;
                        }
                        // 未指定instance id
                        if (fixHostInstanceId == null && file.contain(begin)) {
                            allHostIds.add(file.getInstanceID());
                        }
                    }
                    choosePreferId();
                    binlogFileList = BinlogProcessor.process(result.getItems(), new HashSet<>(), preferHostId, begin
                        , null);

                    Iterator<BinlogFile> iterator = binlogFileList.iterator();
                    while (iterator.hasNext()) {
                        BinlogFile binlogFile = iterator.next();
                        String binlogFileName = binlogFile.getLogname();
                        ossBinlogFileMap.put(binlogFileName, binlogFile);
                        binlogList.add(binlogFileName);
                    }
                    counts += result.getItemsNumbers();
                    binlogDownloader.batchDownload(binlogFileList);
                    log.warn("binlogfilelist: {}", binlogFileList);
                    if (result.getTotalRecords() <= counts) {
                        break;
                    }
                } while (true);

                ((LocalBinlogEventParser) localParser).setBinlogList(binlogList);
                log.warn("binloglist: {}", binlogList);
                binlogDownloader.start();
                // 等待第一个文件下载完成，这样才能使用localConnection
                while (binlogDownloader.getNumberOfDownloadedFile() == 0) {
                    Thread.sleep(2000L);
                }
                parser = localParser;
                log.warn("connecting to local");
            } else {
                parser = remoteParser;
                log.warn("connecting to : " + srcAuthInfo.getAddress() + " with : " + srcAuthInfo.getUsername());
                log.warn("find remote start position: {}", newPosition);
            }

            ((MysqlEventParser) parser).setBinlogParser(logEventConvert);
            parser.start(srcAuthInfo, position, new CanalBinlogEventSink());
        } catch (Exception e) {
            log.error("extractor start error: ", e);
            stop();
        }

    }

    @Override
    public void stop() {
        log.warn("stopping extractor");
        parser.stop();
        log.warn("extractor stopped");
        running = false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    private void initRemoteEventParser() {
        remoteParser = new RdsEventParser(extractorConfig.getEventBufferSize(),
            new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
    }

    private void initLocalEventParser() {
        localParser =
            new LocalBinlogEventParser(extractorConfig.getEventBufferSize(), true, new ILocalBinlogEventListener() {
                @Override
                public void onEnd() {
                    log.info("local file parser end!");
                }

                @Override
                public void onFinishFile(File binlogFile, BinlogPosition pos) {
                    log.error("one local file parser finish!: {}, last pos: {}", binlogFile.getName(), pos);
                    binlogFile.delete();
                    binlogDownloader.releaseOne();
                    position = new BinlogPosition(nextBinlogFileName(binlogFile.getName()),
                        0, pos.getMasterId(), pos.getTimestamp());
                    log.error("one local file parser finish!: {} with new binlog pos: ", position);
                    if (binlogDownloader.isFinish()) {
                        log.error("all local file parser finish , switch parser !: {}", binlogFile.getName());
                        switchParser();
                    }
                }

            }, true, new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
        if (allHostIdTestFlag) {
            log.warn("no need to care server id since now");
            localParser.setCurrentServerId(-1);
        } else {
            log.warn("need to care server id, now remote master server id: {}", remoteParser.getCurrentServerId());
            localParser.setCurrentServerId(remoteParser.getCurrentServerId());
        }
        ((MysqlEventParser) localParser).setUserDefinedHandler(throwable -> {
            if (throwable instanceof ServerIdNotMatchException) {
                log.warn("receive server id not match exception");
                switchAnotherHost();
                switchParser();
                return true;
            }
            return false;
        });
    }

    public String nextBinlogFileName(String fileName) {
        String prefix = fileName.split("\\.")[0];
        String suffix = fileName.split("\\.")[1];
        int suffixNbr = Integer.parseInt(suffix);
        String newSuffix = String.format("%0" + suffix.length() + "d", ++suffixNbr);
        return prefix + "." + newSuffix;
    }

    private void switchParserInner() {
        synchronized (this) {
            ((MysqlEventParser) parser).setDirectExitWhenStop(false);
            binlogDownloader.stop();
            parser.stop();
            startInternal();
        }
    }

    private void switchParser() {
        Thread t = new Thread(() -> {
            try {
                log.info("switching parser");
                switchParserInner();
            } catch (Exception e) {
                log.error("switch parser error!", e);
            }
        });
        t.start();
    }

    public void switchAnotherHost() {
        ignoreHostIds.add(preferHostId);
        log.warn("switch -> add prefer : {} to ignore, ignoreHostId: {}", preferHostId, ignoreHostIds);
    }

    /**
     * master and slave binlog all analysis and not found right server, maybe rds change
     **/
    public void choosePreferId() {
        log.warn("pre allHostId: {}", allHostIds);
        log.warn("pre ignoreHostId: {}", ignoreHostIds);
        log.warn("pre preferHostId: {}", preferHostId);
        allHostIds.removeAll(ignoreHostIds);
        if (allHostIds.isEmpty()) {
            MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_INC_ERROR,
                TaskContext.getInstance().getTaskId(),
                "no appropriate oss binlog or has test all host id but still not find binlog with same server id，需要值班介入");
            log.warn("has test all host id but still not find binlog with same server id，需要值班介入");
            System.exit(1);
        }
        preferHostId = allHostIds.iterator().next();
        log.warn("allHostId: {}", allHostIds);
        log.warn("ignoreHostId: {}", ignoreHostIds);
        log.warn("preferHostId: {}", preferHostId);
    }

}

