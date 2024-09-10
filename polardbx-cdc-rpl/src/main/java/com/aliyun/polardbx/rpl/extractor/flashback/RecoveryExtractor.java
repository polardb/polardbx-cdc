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
package com.aliyun.polardbx.rpl.extractor.flashback;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.BinlogEventParser;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author fanfei
 */
@Slf4j
public class RecoveryExtractor extends BaseExtractor {

    private BinlogEventParser parser;

    private final HostInfo srcHostInfo;

    private AuthenticationInfo srcAuthInfo;

    private final List<String> binlogList;

    private final BaseFilter filter;

    public RecoveryExtractor(RecoveryExtractorConfig config,
                             BaseFilter filter) {
        super(config);
        this.srcHostInfo = config.getHostInfo();
        this.binlogList = config.getBinlogList();
        this.filter = filter;
    }

    @Override
    public void init() throws Exception {
        super.init();
        if (binlogList.isEmpty()) {
            log.error("no binlog provided!");
            return;
        }
        RetryTemplate template = RetryTemplate.builder()
            .maxAttempts(120)
            .fixedBackoff(1000)
            .retryOn(RetryableException.class)
            .build();
        // override src host ip/port
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        ServerInfo serverInfo = template.execute((RetryCallback<ServerInfo, Exception>) retryContext -> {
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
    }

    @Override
    public void start() throws Exception {
        log.info("start recovery extractor ...");
        startInternal();
        running = true;
        log.info("recovery extractor started");
    }

    private void startInternal() throws Exception {
        try {
            BinlogPosition startPosition = findStartPosition();
            log.info("startPosition: " + startPosition);
            LogEventConvert logEventConvert = new LogEventConvert(srcHostInfo, filter, startPosition, HostType.POLARX2,
                true);
            logEventConvert.init();
            BinlogEventSink binlogEventSink = new RecoveryEventSink();
            parser = new MysqlEventParser(extractorConfig.getEventBufferSize(),
                new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
            ((MysqlEventParser) parser).setBinlogParser(logEventConvert);
            ((MysqlEventParser) parser).setAutoRetry(false);
            parser.start(srcAuthInfo, startPosition, binlogEventSink);
        } catch (Exception e) {
            log.error("extractor start error: ", e);
            stop();
            throw e;
        }
    }

    /**
     * binlogList中保存的是还没有消费的binlog，且有序
     * 从当前没有消费的binlog中编号最小的那个文件的0位置处开始消费
     */
    private BinlogPosition findStartPosition() {
        return new BinlogPosition(binlogList.get(0), 0, -1, -1);
    }

    @Override
    public void stop() {
        log.info("stopping parser");
        parser.stop();
        log.info("parser stopped");
        running = false;
    }

    private class RecoveryEventSink implements BinlogEventSink {

        private String tid;

        private long lastHeartTimestamp = 0;

        private boolean filterTransactionEnd = true;

        @Override
        public boolean sink(List<MySQLDBMSEvent> events) {
            long now = System.currentTimeMillis();
            List<MessageEvent> data = new ArrayList<>(events.size());

            for (MySQLDBMSEvent event : events) {
                DBMSEvent dbmsEvent = event.getDbmsEventPayload();
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
                        rowChange.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                        tryPersist(e, event);
                        data.add(e);
                    } else {
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
                            split.setSourceTimeStamp(rowChange.getSourceTimeStamp());
                            split.setExtractTimeStamp(rowChange.getExtractTimeStamp());
                            split.setEventSize(rowChange.getEventSize());
                            split.setPosition(rowChange.getPosition());
                            split.setRtso(rowChange.getRtso());
                            split.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                            tryPersist(e, event);
                            data.add(e);
                        }
                    }
                } else {
                    // query log event
                    MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                    e.setDbmsEvent(dbmsEvent);
                    if (dbmsEvent instanceof DefaultQueryLog) {
                        dbmsEvent.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                    }
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
