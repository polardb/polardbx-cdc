/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_CLEAN_RELAY_DATA_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_CLEAN_RELAY_DATA_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static io.grpc.internal.GrpcUtil.getThreadFactory;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Data
@Slf4j
public class RelayLogEventCleaner {
    private static final XStreamMapper X_STREAM_MAPPER =
        SpringContextHolder.getObject(XStreamMapper.class);
    private static final BinlogOssRecordMapper OSS_RECORD_MAPPER =
        SpringContextHolder.getObject(BinlogOssRecordMapper.class);

    private RelayLogEventTransmitter logEventTransmitter;
    private Map<Integer, StoreEngine> storeEngines;
    private ScheduledExecutorService executor;
    private AtomicBoolean running = new AtomicBoolean(false);

    public RelayLogEventCleaner(RelayLogEventTransmitter logEventTransmitter) {
        this.logEventTransmitter = logEventTransmitter;
    }

    public void start() {
        boolean enable = DynamicApplicationConfig.getBoolean(BINLOGX_CLEAN_RELAY_DATA_ENABLED);
        if (!enable) {
            log.info("cleaning relay data is disabled, log event cleaner will not start!");
            return;
        }

        int interval = DynamicApplicationConfig.getInt(BINLOGX_CLEAN_RELAY_DATA_INTERVAL_MINUTE);
        if (running.compareAndSet(false, true)) {
            this.executor = Executors.newSingleThreadScheduledExecutor(
                getThreadFactory("hash-log-event-cleaner" + "-%d", false));
            this.executor.scheduleAtFixedRate(() -> {
                try {
                    doClean();
                } catch (Throwable t) {
                    log.error("clean hash log event error!!", t);
                }
            }, 1, interval, TimeUnit.MINUTES);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (this.executor != null) {
                this.executor.shutdownNow();
            }
        }
    }

    private void doClean() {
        String streamGroupName = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        int streamCount = DynamicApplicationConfig.getInt(BINLOGX_STREAM_COUNT);

        //check
        List<String> streamsList = X_STREAM_MAPPER.select(
            s -> s.where(XStreamDynamicSqlSupport.groupName, isEqualTo(streamGroupName))
                .orderBy(XStreamDynamicSqlSupport.streamName)).stream().map(
            XStream::getStreamName).collect(Collectors.toList());
        if (streamsList.size() != streamCount) {
            throw new PolardbxException("find mismatched stream count, configuration count is " + streamCount
                + ", count in binlog_x_stream table is " + streamsList.size());
        }

        // 以binlog_oss_record表作为checkpoint的依据
        // 1.如果没有启用备份策略，即binlog.backup.type配置的值为NULL，此时集群不具备HA能力，只要一个全局binlog文件写完，
        //   就可以立即对hash log event执行clean操作，判断写完的依据是binlog_oss_record表的last_tso不为空且upload_status=3
        // 2.如果启用了备份策略，即binlog.backup.type配置的值不为NULL，此时集群具备HA能力，需要等待binlog已经成功上传到备份存储之后
        //   才可以对hash log event执行clean操作，以保证当Stream在节点间发生漂移导致全局binlog位点回退到最后一个备份成功的binlog文件时，
        //   hash log event不会被误删除，换句话说就是不能认为binlog文件写完就可以作为checkpoint了，因为在HA场景可能会发生回退。判断备份
        //   成功的依据是last_tso不为空且upload_status=2
        for (String streamName : streamsList) {
            int streamSeq = Integer.parseInt(StringUtils.substringAfterLast(streamName, "_"));
            BinlogOssRecord record = logEventTransmitter.getCheckpointTsoFromBackup(streamName);
            if (record != null) {
                String lastTso = record.getLastTso();
                StoreEngine storeEngine = storeEngines.get(streamSeq);
                storeEngine.clean(lastTso);
                log.info("stream : " + streamName + ", relay log event is cleaned which tso is less than " + lastTso);
            }
        }
    }
}
