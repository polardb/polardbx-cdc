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
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.BinlogEventParser;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.extractor.BaseExtractor;
import com.aliyun.polardbx.rpl.extractor.LogEventConvert;
import com.aliyun.polardbx.rpl.extractor.MysqlEventParser;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.storage.RplStorage;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryExtractorConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author fanfei
 */
@Slf4j
public class RecoveryExtractor extends BaseExtractor {

    private BinlogDownloader binlogDownloader;

    private BinlogEventParser localParser;

    private BinlogEventParser remoteParser;

    private BinlogEventParser parser;

    private final Object parserSwitchLock = new Object();

    private final HostInfo srcHostInfo;

    private AuthenticationInfo srcAuthInfo;

    private final List<String> binlogList;

    private final BaseFilter filter;

    private String switchToRemoteFileName;

    public RecoveryExtractor(RecoveryExtractorConfig config,
                             BaseFilter filter) {
        super(config);
        this.srcHostInfo = config.getHostInfo();
        this.binlogList = config.getBinlogList();
        this.filter = filter;
    }

    @Override
    public boolean init() {
        try {
            if (binlogList.isEmpty()) {
                log.error("no binlog provided!");
                return false;
            }
            RetryTemplate template = RetryTemplate.builder()
                .maxAttempts(120)
                .fixedBackoff(1000)
                .retryOn(RetryableException.class)
                .build();
            // override src host ip/port
            ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
            ServerInfo serverInfo = template.execute((RetryCallback<ServerInfo, Throwable>) retryContext -> {
                List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
                    c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                        .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
                );
                if (serverInfoList.isEmpty()) {
                    throw new RetryableException("server info is not ready");
                }
                return serverInfoList.get(0);
            }, retryContext -> null);
            String hostIp = serverInfo.getIp();
            Integer port = serverInfo.getPort();
            String user = DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME);
            String pwd = DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD);
            srcAuthInfo = new AuthenticationInfo();
            srcAuthInfo.setAddress(new InetSocketAddress(hostIp, port));
            srcAuthInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
            srcAuthInfo.setUsername(user);
            srcAuthInfo.setPassword(pwd);
            return true;
        } catch (Throwable e) {
            log.error("RecoveryExtractor init failed " + e);
            return false;
        }
    }

    @Override
    public void start() {
        log.info("start recovery extractor ...");
        startInternal();
        running = true;
        log.info("recovery extractor started");
    }

    private void startInternal() {
        try {
            BinlogPosition startPosition = findStartPosition();
            log.info("startPosition: " + startPosition);

            binlogDownloader = new BinlogDownloader();
            binlogDownloader.init();
            initRemoteEventParser();
            initLocalEventParser();

            LogEventConvert logEventConvert = new LogEventConvert(srcHostInfo, filter, startPosition, HostType.POLARX2);
            logEventConvert.init();
            BinlogEventSink binlogEventSink = new RecoveryEventSink();

            Set<String> rdsBinlog = new HashSet<>(getRdsBinlogList());
            if (!rdsBinlog.contains(startPosition.getFileName())) {
                List<String> downLoadFileList = new ArrayList<>();
                while (!binlogList.isEmpty()) {
                    if (rdsBinlog.contains(binlogList.get(0))) {
                        break;
                    }
                    downLoadFileList.add(binlogList.get(0));
                    switchToRemoteFileName = nextBinlogFileName(binlogList.get(0));
                    // RDS上可能会有部分binlog,首先使用Local模式，将RDS上没有的binlog下载到本地消费
                    // 然后切换到Remote模式，此时Remote模式需要知道从哪个文件开始消费
                    // 所以这里将binlogList中本地消费的那些fileName删掉
                    binlogList.remove(0);
                }

                //如果所有文件在oss都已经没有了，则需要手动往binlogList增加下一个文件，保证switchToRemote的时候，能正常消费
                if (binlogList.isEmpty()) {
                    binlogList.add(switchToRemoteFileName);
                }

                ((LocalBinlogEventParser) localParser).setBinlogList(downLoadFileList);
                binlogDownloader.batchDownload(downLoadFileList);
                binlogDownloader.start();
                // 等待第一个文件下载完成，这样才能使用localConnection
                while (binlogDownloader.getNumberOfDownloadedFile() == 0) {
                    Thread.sleep(2000L);
                }
                parser = localParser;
            } else {
                parser = remoteParser;
            }
            ((MysqlEventParser) parser).setBinlogParser(logEventConvert);
            ((MysqlEventParser) parser).setAutoRetry(false);
            parser.start(srcAuthInfo, startPosition, binlogEventSink);
        } catch (Exception e) {
            log.error("extractor start error: ", e);
            stop();
        }
    }

    /**
     * binlogList中保存的是还没有消费的binlog，且有序
     * 从当前没有消费的binlog中编号最小的那个文件的0位置处开始消费
     */
    private BinlogPosition findStartPosition() {
        return new BinlogPosition(binlogList.get(0), 0, -1, -1);
    }

    private List<String> getRdsBinlogList() {
        List<String> binlogList = new ArrayList<>();
        MysqlConnection connection = new MysqlConnection(srcAuthInfo);
        try {
            connection.connect();
            connection.query("show binary logs", rs -> {
                while (rs.next()) {
                    String fileName = rs.getString(1);
                    binlogList.add(fileName);
                }
                return binlogList;
            });
            connection.disconnect();
            return binlogList;
        } catch (IOException e) {
            log.error("connected failed");
        }
        return binlogList;
    }

    @Override
    public void stop() {
        log.info("stopping parser");
        parser.stop();
        log.info("parser stopped");
        running = false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    private void initRemoteEventParser() {
        remoteParser = new MysqlEventParser(extractorConfig.getEventBufferSize(),
            new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
    }

    private void initLocalEventParser() {
        localParser =
            new LocalBinlogEventParser(extractorConfig.getEventBufferSize(), true, new ILocalBinlogEventListener() {
                @Override
                public void onEnd() {
                    log.info("local file parser end, switch to remote!");
                    switchToRemote();
                }

                @Override
                public void onFinishFile(File binlogFile, BinlogPosition pos) {
                    binlogFile.delete();
                    binlogDownloader.releaseOne();
                    if (binlogDownloader.isFinish()) {
                        log.error("local file parser finish , switch to remote !");
                        switchToRemote();
                    }
                }
            }, false, new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
    }

    private void switchToRemote() {
        Thread t = new Thread(() -> {
            try {
                log.info("switching to remote parser");
                switchToRemoteParser();
            } catch (Exception e) {
                log.error("switch to remote error!", e);
            }
        });
        t.start();
    }

    private void switchToRemoteParser() {
        synchronized (parserSwitchLock) {
            ((MysqlEventParser) parser).setDirectExitWhenStop(false);
            binlogDownloader.stop();
            parser.stop();
            startInternal();
        }
    }

    public String nextBinlogFileName(String fileName) {
        String prefix = fileName.split("\\.")[0];
        String suffix = fileName.split("\\.")[1];
        int suffixNbr = Integer.parseInt(suffix);
        String newSuffix = String.format("%0" + suffix.length() + "d", ++suffixNbr);
        return prefix + "." + newSuffix;
    }

    private class RecoveryEventSink implements BinlogEventSink {

        private String tid;

        private long lastHeartTimestamp = 0;

        private boolean filterTransactionEnd = true;

        @Override
        public boolean sink(List<MySQLDBMSEvent> events) {
            long now = System.currentTimeMillis();
            Timestamp extractTimestamp = new Timestamp(now);
            List<MessageEvent> data = new ArrayList<>(events.size());

            for (MySQLDBMSEvent event : events) {
                DBMSEvent dbmsEvent = event.getDbMessageWithEffect();
                if (dbmsEvent instanceof DBMSTransactionBegin) {
                    // 只处理正常的数据,事务头和尾就忽略了,避免占用ringBuffer空间
                    DBMSTransactionBegin begin = (DBMSTransactionBegin) dbmsEvent;
                    tid = begin.getThreadId() + "";
                    continue;
                }
                if (filterTransactionEnd && dbmsEvent instanceof DBMSTransactionEnd) {
                    if (now - lastHeartTimestamp > 1000) {
                        lastHeartTimestamp = now;
                    } else {
                        continue;
                    }
                }

                if (StringUtils.isEmpty(tid)) {
                    tid = Math.floor(Math.random() * 10000000) + ""; // 随机生成一个
                }

                if (dbmsEvent instanceof DefaultRowChange) {
                    DefaultRowChange rowChange = (DefaultRowChange) dbmsEvent;
                    if (rowChange.getRowSize() == 1) {
                        MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                        e.setDbmsEvent(dbmsEvent);
                        e.setPosition(event.getPosition().toString());
                        e.setSourceTimestamp(new Timestamp(event.getPosition().getTimestamp() * 1000));
                        e.setExtractTimestamp(extractTimestamp);
                        rowChange.putOption(
                            new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                        rowChange.putOption(
                            new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));
                        rowChange.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                        tryPersist(e, event);
                        data.add(e);
                    } else {
                        BinlogPosition position = event.getPosition();
                        long innerOffset = 0;
                        for (int rownum = 1; rownum <= rowChange.getRowSize(); rownum++) {
                            // 多行记录,拆分为单行进行处理
                            DefaultRowChange split = new DefaultRowChange(rowChange.getAction(),
                                rowChange.getSchema(),
                                rowChange.getTable(),
                                rowChange.getColumnSet());
                            if (DBMSAction.UPDATE == rowChange.getAction()) {
                                // 需要复制一份changeColumns,避免更新同一个引用
                                split.setChangeColumnsBitSet((BitSet) rowChange.getChangeIndexes().clone());
                                split.setChangeData(1, rowChange.getChangeData(rownum));
                            }
                            split.setRowData(1, rowChange.getRowData(rownum));
                            // 每一行一个事件
                            MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                            e.setDbmsEvent(split);
                            position.setInnerOffset(innerOffset++);
                            e.setPosition(position.toString());
                            e.setSourceTimestamp(new Timestamp(event.getPosition().getTimestamp() * 1000));
                            e.setExtractTimestamp(extractTimestamp);
                            split.putOption(
                                new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                            split.putOption(
                                new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));
                            split.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                            tryPersist(e, event);

                            data.add(e);
                        }
                    }
                } else {
                    // query log event
                    MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                    e.setDbmsEvent(dbmsEvent);
                    e.setPosition(event.getPosition().toString());
                    e.setSourceTimestamp(new Timestamp(event.getPosition().getTimestamp() * 1000));
                    if (dbmsEvent instanceof DBMSQueryLog) {
                        dbmsEvent.putOption(
                            new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                        dbmsEvent
                            .putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));
                        dbmsEvent.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                    }
                    e.setExtractTimestamp(extractTimestamp);
                    tryPersist(e, event);
                    data.add(e);
                }
                event.tryRelease();
            }

            pipeline.writeRingbuffer(data);
            return true;
        }

        @Override
        public boolean sink(Throwable e) {
            log.error("sink error", e);
            stop();
            return true;
        }
    }

    @Override
    public String toString() {
        return "Recovery extractor";
    }

}
