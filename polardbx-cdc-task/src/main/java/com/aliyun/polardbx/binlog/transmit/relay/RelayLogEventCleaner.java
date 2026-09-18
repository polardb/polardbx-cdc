/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_CLEAN_RELAY_DATA_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_CLEAN_RELAY_DATA_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD;
import static com.aliyun.polardbx.binlog.transmit.relay.RelayStreamUtils.getStreamListAndCheck;
import static io.grpc.internal.GrpcUtil.getThreadFactory;

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

    void doClean() {

        //check
        List<String> streamsList = getStreamListAndCheck();

        // 简化的relay log清理算法，三条规则按优先级依次判断：
        // Rule 1: 优先使用getCheckpointTsoFromBackup作为cleanupTso（最安全，有upload_status校验）
        // Rule 2: 当checkpoint距离maxReadTso超过指定时间阈值时，清理阈值时间之前的数据
        // Rule 3: 当空间压力达到阈值时，使用aggressive buffer进行清理
        // 安全兜底：RelayDataReaderBase.checkValid() 在DumperX请求TSO < relay log min(TSO)时触发JVM halt
        for (String streamName : streamsList) {
            int streamSeq = Integer.parseInt(StringUtils.substringAfterLast(streamName, "_"));
            StoreEngine storeEngine = storeEngines.get(streamSeq);

            // Step 0: maxReadTso为空则不触发清理
            String maxReadTso = storeEngine.getMaxReadTso();
            if (StringUtils.isBlank(maxReadTso)) {
                log.info("stream : {} , maxReadTso is blank, skip cleaning.", streamName);
                continue;
            }

            // Step 1: 优先使用getCheckpointTsoFromBackup（最安全策略）
            // 该方法查询upload_status=SUCCESS/IGNORE的记录，确保数据已完整上传到备份存储
            BinlogOssRecord checkpointRecord = logEventTransmitter.getCheckpointTsoFromBackup(streamName);
            String cleanupTso;
            String cleanupReason;

            if (checkpointRecord != null && StringUtils.isNotBlank(checkpointRecord.getLastTso())
                && checkpointRecord.getLastTso().compareTo(maxReadTso) < 0) {
                // Step 2: 判断checkpoint是否距离maxReadTso过远
                long checkpointLagMs = CommonUtils.getTsoPhysicalTime(maxReadTso, TimeUnit.MILLISECONDS)
                    - CommonUtils.getTsoPhysicalTime(checkpointRecord.getLastTso(), TimeUnit.MILLISECONDS);
                long maxLagMinutes = DynamicApplicationConfig.getInt(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES);
                long checkpointLagMinutes = checkpointLagMs / (60 * 1000L);

                if (checkpointLagMinutes >= maxLagMinutes) {
                    // Rule 2: checkpoint距离maxReadTso超过阈值，清理阈值时间之前的数据
                    cleanupTso = RelayLogEventTransmitter.computeTsoBefore(maxReadTso,
                        (int) maxLagMinutes);
                    cleanupReason = String.format("checkpoint_lag_too_large(checkpointLag=%dm, maxLag=%dm)",
                        checkpointLagMinutes, maxLagMinutes);
                } else {
                    // Rule 1: checkpoint在安全范围内，直接使用checkpoint的lastTso作为cleanupTso
                    // getCheckpointTsoFromBackup已确保upload_status=SUCCESS/IGNORE，可安全清理
                    // lastTso是已完成文件的结束TSO，即使deleteRange包含边界也不会影响DumperX重启
                    // （DumperX用lastTso做seek时，>= lastTso会自动找到下一个文件的起始数据）
                    cleanupTso = checkpointRecord.getLastTso();
                    cleanupReason = String.format("checkpoint(checkpointTso=%s)",
                        checkpointRecord.getLastTso());
                }
            } else {
                // 没有可用的checkpoint，降级为基于maxReadTso的时间窗口清理
                long maxLagMinutes = DynamicApplicationConfig.getInt(BINLOGX_RELAY_CLEANUP_CHECKPOINT_MAX_LAG_MINUTES);
                cleanupTso = RelayLogEventTransmitter.computeTsoBefore(maxReadTso,
                    (int) maxLagMinutes);
                cleanupReason = String.format("no_checkpoint(maxLag=%dm)", maxLagMinutes);
            }

            // Step 3: 空间压力覆盖
            // 当文件数/slowdown阈值 >= 配置比例时，使用aggressive buffer进行更积极的清理
            try {
                int currentFileCount = RelayFileStoreEngine.getRelayFileCounter().getTotalRelayFileCount();
                int slowdownThreshold = DynamicApplicationConfig.getInt(BINLOGX_TRANSMIT_WRITE_SLOWDOWN_THRESHOLD);
                double slowdownRatio = slowdownThreshold > 0 ? (double) currentFileCount / slowdownThreshold : 0;
                double spaceSlowdownRatio = DynamicApplicationConfig.getDouble(
                    BINLOGX_RELAY_CLEANUP_SPACE_SLOWDOWN_RATIO);

                if (slowdownRatio >= spaceSlowdownRatio) {
                    // Rule 3: 空间压力触发，使用aggressive buffer
                    int aggressiveBuffer = DynamicApplicationConfig.getInt(
                        BINLOGX_RELAY_CLEANUP_AGGRESSIVE_BUFFER_MINUTES);
                    String spaceCleanupTso = RelayLogEventTransmitter.computeTsoBefore(
                        maxReadTso, aggressiveBuffer);
                    // 空间压力下取更激进的（更大的）cleanupTso
                    if (StringUtils.isNotBlank(spaceCleanupTso)
                        && (StringUtils.isBlank(cleanupTso) || spaceCleanupTso.compareTo(cleanupTso) > 0)) {
                        cleanupTso = spaceCleanupTso;
                        cleanupReason = String.format(
                            "space_pressure(slowdownRatio=%.2f, threshold=%.2f, buffer=%dm)",
                            slowdownRatio, spaceSlowdownRatio, aggressiveBuffer);
                    }
                }
            } catch (Exception e) {
                log.warn("failed to assess space pressure, skip space override", e);
            }

            // Step 4: 执行清理（只在有推进时才执行）
            String maxCleanTso = storeEngine.getMaxCleanTso();
            if (StringUtils.isNotBlank(cleanupTso)
                && (StringUtils.isBlank(maxCleanTso) || cleanupTso.compareTo(maxCleanTso) > 0)) {
                storeEngine.clean(cleanupTso);
                log.info("stream : {} , relay log event is cleaned which tso is less than {} , reason={}",
                    streamName, cleanupTso, cleanupReason);
            } else {
                log.info("stream : {} , no progress for cleaning, cleanupTso={} , maxCleanTso={}",
                    streamName, cleanupTso, maxCleanTso);
            }
        }
    }
}
