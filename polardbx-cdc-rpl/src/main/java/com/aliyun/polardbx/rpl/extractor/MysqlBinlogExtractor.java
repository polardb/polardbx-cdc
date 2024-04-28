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
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.XATransactionType;
import com.aliyun.polardbx.binlog.canal.core.AbstractEventParser;
import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;
import com.aliyun.polardbx.rpl.storage.RplEventRepository;
import com.aliyun.polardbx.rpl.storage.RplStorage;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * @author shicai.xsc 2020/11/29 21:19
 * @since 5.0.0.0
 */
@Slf4j
@Data
public class MysqlBinlogExtractor extends BaseExtractor {

    protected BinlogPosition position;
    protected AbstractEventParser parser;
    protected long lastHeartTimestamp = 0;
    protected boolean filterTransactionEnd = true;
    protected HostInfo srcHostInfo;
    protected HostInfo metaHostInfo;
    protected BaseFilter filter;
    protected int extractorType;

    protected AuthenticationInfo srcAuthInfo;
    protected AuthenticationInfo metaAuthInfo;

    public MysqlBinlogExtractor(ExtractorConfig extractorConfig, HostInfo srcHostInfo, HostInfo metaHostInfo,
                                BinlogPosition position,
                                BaseFilter filter) {
        super(extractorConfig);
        this.srcHostInfo = srcHostInfo;
        this.metaHostInfo = metaHostInfo;
        this.position = position;
        this.filter = filter;
        initAuthInfo();
    }

    @Override
    public void start() throws Exception {
        try {
            parser = new MysqlEventParser(extractorConfig.getEventBufferSize(),
                new RplEventRepository(pipeline.getPipeLineConfig().getPersistConfig()));
            LogEventConvert logEventConvert =
                new LogEventConvert(metaHostInfo, filter, position, srcHostInfo.getType());
            logEventConvert.init();
            ((MysqlEventParser) parser).setPolarx(srcHostInfo.getType() == HostType.POLARX2);
            ((MysqlEventParser) parser).setBinlogParser(logEventConvert);
            log.warn("connecting to : " + srcAuthInfo.getAddress() + " with : " + srcAuthInfo.getUsername());
            parser.start(srcAuthInfo, position, new CanalBinlogEventSink());
            running = true;
        } catch (Exception e) {
            log.error("extractor start error: ", e);
            throw e;
        }
    }

    @Override
    public void stop() {
        log.warn("stopping extractor");
        parser.stop();
        log.warn("extractor stopped");
        running = false;
    }

    protected void initAuthInfo() {
        srcAuthInfo = new AuthenticationInfo();
        srcAuthInfo.setAddress(new InetSocketAddress(srcHostInfo.getHost(), srcHostInfo.getPort()));
        srcAuthInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        srcAuthInfo.setUsername(srcHostInfo.getUserName());
        srcAuthInfo.setPassword(srcHostInfo.getPassword());

        metaAuthInfo = new AuthenticationInfo();
        metaAuthInfo.setAddress(new InetSocketAddress(metaHostInfo.getHost(), metaHostInfo.getPort()));
        metaAuthInfo.setCharset(RplConstants.EXTRACTOR_DEFAULT_CHARSET);
        metaAuthInfo.setUsername(metaHostInfo.getUserName());
        metaAuthInfo.setPassword(metaHostInfo.getPassword());
    }

    protected class CanalBinlogEventSink implements BinlogEventSink {

        private String tid;
        private DBMSXATransaction nowXa = null;

        @Override
        public boolean sink(Throwable e) {
            log.error("sink error", e);
            stop();
            return true;
        }

        /**
         * This will be called by MysqlWithTsoEventParser
         */
        @Override
        public boolean sink(List<MySQLDBMSEvent> events) {
            long now = System.currentTimeMillis();
            List<MessageEvent> datas = new ArrayList<>(events.size());

            // 一次完整的 XA 事务，会 3 次调用本函数
            // 1. XA_START
            // 2. DML SQL(一条或多条) + XA_END
            // 3. XA_COMMIT

            for (MySQLDBMSEvent event : events) {
                DBMSEvent dbmsEvent = event.getDbmsEventPayload();
                if (dbmsEvent instanceof DBMSTransactionBegin) {
                    // 只处理正常的数据,事务头和尾就忽略了,避免占用ringbuffer空间
                    DBMSTransactionBegin begin = (DBMSTransactionBegin) dbmsEvent;
                    tid = begin.getThreadId() + "";
                    continue;
                }

                // 默认也过滤掉 DBMSTransactionEnd，避免占用ringbuffer空间，但是要保持心跳以推动位点，所以不能全部过滤
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

                // 需将 XA 事务信息赋予其关联 DML
                if (event.getXaTransaction() != null
                    && (event.getXaTransaction().getType() == XATransactionType.XA_START
                    || event.getXaTransaction().getType() == XATransactionType.XA_END)) {
                    if (event.getXaTransaction().getType() == XATransactionType.XA_START) {
                        nowXa = event.getXaTransaction();
                    } else if (event.getXaTransaction().getType() == XATransactionType.XA_END) {
                        nowXa = null;
                    }
                    // continue;
                }
                if (dbmsEvent instanceof DefaultRowChange) {
                    DefaultRowChange rowChange = (DefaultRowChange) dbmsEvent;
                    if (rowChange.getRowSize() == 1) {
                        MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                        e.setDbmsEvent(dbmsEvent);
                        rowChange.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                        // 如果此 DML 与某 XA 事务关联，将 XA 事务信息赋予其关联 DML
                        if (nowXa != null) {
                            e.setXaTransaction(nowXa);
                        }
                        tryPersist(e, event);
                        datas.add(e);
                    } else {
                        BinlogPosition position = event.getPosition();
                        // long innerOffset = 0;
                        for (int rownum = 1; rownum <= rowChange.getRowSize(); rownum++) {
                            // 多行记录,拆分为单行进行处理
                            DefaultRowChange split = new DefaultRowChange(rowChange.getAction(),
                                rowChange.getSchema(),
                                rowChange.getTable(),
                                rowChange.getColumnSet(),
                                null,
                                new ArrayList<>(rowChange.getOptions()));
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
                            if (nowXa != null) {
                                e.setXaTransaction(nowXa);
                            }
                            tryPersist(e, event);
                            datas.add(e);
                        }
                    }
                } else {
                    // query log event
                    MessageEvent e = new MessageEvent(RplStorage.getRepoUnit());
                    e.setDbmsEvent(dbmsEvent);
                    if (dbmsEvent instanceof DefaultQueryLog) {
                        dbmsEvent.putOption(new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_T_ID, tid));
                    }
                    e.setXaTransaction(event.getXaTransaction());
                    tryPersist(e, event);
                    datas.add(e);
                }
                event.tryRelease();
            }

            pipeline.writeRingbuffer(datas);
            return true;
        }
    }

}
