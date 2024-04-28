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
package com.aliyun.polardbx.binlog.columnar;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ColumnarCheckpointsMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarCheckpointsDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.ColumnarCheckpoints;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.FullMasterStatus;
import com.aliyun.polardbx.rpc.cdc.Request;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author wenki
 */
@Slf4j
public class ColumnarMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("COLUMNAR_MONITOR");

    private final ColumnarCheckpointsMapper columnarCheckpointsMapper;
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private final ScheduledExecutorService scheduledExecutorService;
    private volatile boolean running;

    private final AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
    private String lastFile;
    private long lastPos;

    public ColumnarMonitor() {
        this.columnarCheckpointsMapper = SpringContextHolder.getObject(ColumnarCheckpointsMapper.class);

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "columnar-metrics-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                monitorColumnarOffset();
            } catch (Throwable e) {
                LOGGER.error("columnar offset monitor error!", e);
            }
        }, INTERVAL, INTERVAL, TimeUnit.MILLISECONDS);
        LOGGER.info("columnar offset monitor started.");
    }

    public Date getColumnarProcessHeartbeat() {
        List<ColumnarCheckpoints> checkpoints = columnarCheckpointsMapper
            .select(s -> s.orderBy(ColumnarCheckpointsDynamicSqlSupport.id.descending()).limit(1));
        return checkpoints.get(0).getUpdateTime();
    }

    public void monitorColumnarOffset() throws Throwable {
        ColumnarCheckpoints columnarLatency = columnarCheckpointsMapper
            .select(s -> s
                .where(ColumnarCheckpointsDynamicSqlSupport.checkpointType,
                    SqlBuilder.isIn(CheckPointType.STREAM.name(), CheckPointType.DDL.name(),
                        CheckPointType.HEARTBEAT.name()))
                .orderBy(ColumnarCheckpointsDynamicSqlSupport.checkpointTso.descending(),
                    ColumnarCheckpointsDynamicSqlSupport.updateTime.descending())
                .limit(1))
            .get(0);

        long cdcMs = getCdcOffset() >>> 22;
        ObjectMapper mapper = new ObjectMapper();
        SourceInfo sourceInfo = mapper.readValue(columnarLatency.getOffset(), SourceInfo.class);
        long columnarMs = sourceInfo.tso >>> 22;

        long latency = cdcMs - columnarMs;

        // latency > 10分钟
        boolean excessiveLatency = false;
        if (latency > 60 * 10 * 1000) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.COLUMNAR_EXCESSIVE_LATENCY_WARNING);
            excessiveLatency = true;
        }

        String currentFile = sourceInfo.file;
        long currentPos = sourceInfo.pos;

        // 如果file和pos都没有改变
        if (currentFile.equals(lastFile) && currentPos == lastPos) {
            // 检查是否已经过了10分钟
            if ((System.currentTimeMillis() - lastUpdateTime.get()) >= 10 * 60 * 1000) {
                // 已经10分钟了，发出警报
                MonitorManager.getInstance().triggerAlarm(MonitorType.COLUMNAR_BINLOG_POSITION_WARNING);
                // 同时延迟超过10分钟，上升为严重警报
                if (excessiveLatency) {
                    MonitorManager.getInstance().triggerAlarm(MonitorType.COLUMNAR_FATAL_ERROR,
                        "Columnar offset延迟和binlog消费位点都已经超过10分钟阈值！");
                }
                // 重置更新时间
                lastUpdateTime.set(System.currentTimeMillis());
            }
        } else {
            // 如果file或pos有变化，更新缓存的值和时间戳
            lastFile = currentFile;
            lastPos = currentPos;
            lastUpdateTime.set(System.currentTimeMillis());
        }

    }

    public long getCdcOffset() throws Throwable {
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        RetryTemplate template =
            RetryTemplate.builder().maxAttempts(120).fixedBackoff(1000).retryOn(RetryableException.class).build();
        DumperInfo info = template.execute((RetryCallback<DumperInfo, Throwable>) retryContext -> {
            Optional<DumperInfo> dumperInfo =
                dumperInfoMapper.selectOne(c -> c.where(DumperInfoDynamicSqlSupport.role, IsEqualTo.of(() -> "M")));
            if (!dumperInfo.isPresent()) {
                throw new RetryableException("dumper leader is not ready");
            }
            return dumperInfo.get();
        }, retryContext -> null);

        ManagedChannel channel = ManagedChannelBuilder.forAddress(info.getIp(), info.getPort()).usePlaintext()
            .maxInboundMessageSize(0xFFFFFF + 0xFF).build();

        CdcServiceGrpc.CdcServiceStub cdcServiceStub = CdcServiceGrpc.newStub(channel);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicLong cdcTso = new AtomicLong();
        cdcServiceStub.showFullMasterStatus(Request.newBuilder().build(), new StreamObserver<FullMasterStatus>() {
            @Override
            public void onNext(FullMasterStatus value) {
                cdcTso.set(getTsoTimestamp(value.getLastTso()));
                countDownLatch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                countDownLatch.countDown();
                throw new PolardbxException(t);
            }

            @Override
            public void onCompleted() {
            }
        });

        return cdcTso.get();
    }

    public enum CheckPointType {
        DDL,
        STREAM,
        SNAPSHOT,
        HEARTBEAT,
        COMPACTION,
        SNAPSHOT_END,
        SNAPSHOT_FINISHED;

        public static CheckPointType from(String value) {
            switch (value.toLowerCase()) {
            case "ddl":
                return DDL;
            case "stream":
                return STREAM;
            case "snapshot":
                return SNAPSHOT;
            case "heartbeat":
                return HEARTBEAT;
            case "compaction":
                return COMPACTION;
            case "snapshot_end":
                return SNAPSHOT_END;
            case "snapshot_finished":
                return SNAPSHOT_FINISHED;
            default:
                throw new IllegalArgumentException("Illegal CheckPointType: " + value);
            }
        }
    }

    private static Long getTsoTimestamp(String tso) {
        return Long.valueOf(tso.substring(0, 19));
    }

    private static class SourceInfo {
        @JsonProperty("file")
        private String file;
        @JsonProperty("pos")
        private long pos = 0L;
        @JsonProperty("row")
        private int row = 0;
        @JsonProperty("server_id")
        private long server_id = 0;
        @JsonProperty("tso")
        private long tso = 0;
    }

}
