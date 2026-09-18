/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.task;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TaskRuntimeConfigProvider;
import com.aliyun.polardbx.binlog.dao.BinlogDumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.TaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.DumperType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.GLOBAL_BINLOG_LATEST_CURSOR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WAIT_SUB_VERSION_CALLBACK_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class TaskHeartbeat extends AbstractBinlogTimerTask {
    private final long version;
    private final String taskRole;
    private final TaskRuntimeConfigProvider taskConfigProvider;
    private final ISubVersionChangeCallback subVersionChangeCallback;
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
    private final XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
    private long subVersion;
    private ExecutionConfig executionConfig;
    @Setter
    private Consumer<Void> processExitCallback;
    @Setter
    private Supplier<Map<String, IDumperStatisticProvider>> dumperStatisticSupplier;

    public TaskHeartbeat(String clusterId,
                         String clusterType,
                         String name,
                         int interval,
                         TaskRuntimeConfig originTaskRuntimeConfig,
                         TaskRuntimeConfigProvider taskRuntimeConfigProvider,
                         ISubVersionChangeCallback subVersionChangeCallback) {

        super(clusterId, clusterType, name, interval);
        this.taskConfigProvider = taskRuntimeConfigProvider;
        this.subVersionChangeCallback = subVersionChangeCallback;

        this.version = originTaskRuntimeConfig.getBinlogTaskConfig().getVersion();
        this.subVersion = originTaskRuntimeConfig.getBinlogTaskConfig().getSubVersion();
        this.taskRole = originTaskRuntimeConfig.getBinlogTaskConfig().getRole();
        this.executionConfig = originTaskRuntimeConfig.getExecutionConfig();
        this.processExitCallback = i -> Runtime.getRuntime().halt(1);
    }

    @Override
    public void exec() {
        checkRuntimeVersion();
        updateHeartbeat();
    }

    void updateHeartbeat() {
        if (taskRole.equals(TaskType.Dumper.name())) {
            updateDumperHeartbeat();
        } else if (TaskType.isTask(taskRole)) {
            updateTaskHeartbeat();
        } else if (taskRole.equals(TaskType.DumperX.name())) {
            updateDumperXHeartbeat();
        }
    }

    void checkRuntimeVersion() {
        ClusterSnapshot clusterSnapshot = queryClusterSnapshot();
        checkMainVersion(clusterSnapshot);
        checkSubVersion(clusterSnapshot);
    }

    ClusterSnapshot queryClusterSnapshot() {
        String snapshotConfigStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        return JSONObject.parseObject(snapshotConfigStr, ClusterSnapshot.class);
    }

    void checkMainVersion(ClusterSnapshot clusterSnapshot) {
        // 判断一下拓扑版本是否已经晋升到了更高的版本，如果是的话，本进程已经没有存在的必要了，直接退出即可
        if (clusterSnapshot != null && version < clusterSnapshot.getVersion()) {
            log.warn("Cluster topology has been migrated to new version, "
                + "this process will exit,  stale old version is {},"
                + "latest new version is {}", version, clusterSnapshot.getVersion());
            processExitCallback.accept(null);
        }
    }

    void checkSubVersion(ClusterSnapshot clusterSnapshot) {
        // sub_version发生变更，不重启进程，但需要进行调用回调函数
        if (clusterSnapshot != null && subVersion < clusterSnapshot.getSubVersion()) {
            log.warn("check sub version chang, from {} to {}.", subVersion, clusterSnapshot.getSubVersion());
            this.onSubVersionChange(clusterSnapshot);
        }
    }

    void updateDumperHeartbeat() {
        BinlogDumperInfoMapper binlogDumperInfoMapper = SpringContextHolder.getObject(BinlogDumperInfoMapper.class);
        BinlogCursor cursor = dumperStatisticSupplier.get().get(STREAM_NAME_GLOBAL).getLatestFileCursor();
        long dumperDelay = dumperStatisticSupplier.get().get(STREAM_NAME_GLOBAL).getDumperDelay();
        final boolean dumperLeader = RuntimeLeaderElector.isDumperMaster(version, name);

        // 更新心跳（包括一些统计信息，现在只有delay）
        String dumperRole = dumperLeader ? DumperType.MASTER.getName() : DumperType.SLAVE.getName();
        int result = binlogDumperInfoMapper.updateDumperHeartbeatWithDelay(name, dumperRole,
            clusterId, dumperDelay, subVersion, true);
        if (result == 0) {
            log.error("Dumper info has been removed from database, this process will exit");
            processExitCallback.accept(null);
        }

        // 类似贪心算法，强制把其它dumper的状态设置为S的角色，因为在分布式环境下，相同名字的Dumper是可能存在短暂共存状态的，需要进行矫正
        if (dumperLeader) {
            dumperInfoMapper.update(
                s -> s
                    .set(DumperInfoDynamicSqlSupport.role)
                    .equalTo(DumperType.SLAVE.getName())
                    .where(DumperInfoDynamicSqlSupport.clusterId,
                        SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID)))
                    .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isNotEqualTo(name)));
            SystemDbConfig.updateSystemDbConfig(GLOBAL_BINLOG_LATEST_CURSOR, JSONObject.toJSONString(cursor));
        }

        // 一个Node只会运行一个Dumper，将cursor信息记录到Node，方便Daemon调度时进行参考(选Cursor最大的Node上的Dumper为Master)
        if (cursor != null) {
            nodeInfoMapper.update(
                u -> u
                    .set(NodeInfoDynamicSqlSupport.latestCursor)
                    .equalTo(JSONObject.toJSONString(cursor))
                    .where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(NodeInfoDynamicSqlSupport.containerId,
                        SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.INST_ID)))
            );
        }
    }

    void updateTaskHeartbeat() {
        TaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(TaskInfoMapper.class);
        int result = taskInfoMapper.updateTaskHeartbeat(name, clusterId, subVersion, true);
        if (result == 0) {
            log.error("Task info has been removed from database, this process will exit");
            processExitCallback.accept(null);
        }
    }

    void updateDumperXHeartbeat() {
        BinlogDumperInfoMapper binlogDumperInfoMapper = SpringContextHolder.getObject(BinlogDumperInfoMapper.class);
        int result = binlogDumperInfoMapper.updateDumperHeartbeatBasic(name,
            DumperType.XSTREAM.getName(), clusterId, subVersion, true);
        if (result == 0) {
            log.error("Dumper info has been removed from database, this process will exit");
            processExitCallback.accept(null);
        }

        executionConfig.getStreamNameSet().forEach(streamName -> {
            IDumperStatisticProvider statisticProvider = dumperStatisticSupplier.get().get(streamName);
            if (statisticProvider != null) {
                // 在sub version callback的过程中执行心跳更新，可能获取不到statisticProvider
                BinlogCursor cursor = statisticProvider.getLatestFileCursor();
                if (cursor != null) {
                    xStreamMapper.update(
                        u -> u.set(XStreamDynamicSqlSupport.latestCursor).equalTo(JSONObject.toJSONString(cursor))
                            .where(XStreamDynamicSqlSupport.streamName, isEqualTo(streamName)));
                }
            }
        });
    }

    void onSubVersionChange(ClusterSnapshot clusterSnapshot) {
        TaskRuntimeConfig taskRuntimeConfig = this.taskConfigProvider.getTaskRuntimeConfig();
        BinlogTaskConfig binlogTaskConfig = taskRuntimeConfig.getBinlogTaskConfig();

        // 有可能刚刚获取到sub_version，紧接着又发生了rebalance，因此需要确保版本一致
        // 如果main version不一致，什么都不做，靠下一次心跳继续检测，走进程退出逻辑
        // 如果main version一致，但sub_version不一致，则采取ignore的策略，等下次心跳取更新的版本再进行回调
        if (binlogTaskConfig.getVersion() == this.version && Objects.equals(binlogTaskConfig.getSubVersion(),
            clusterSnapshot.getSubVersion())) {

            this.notifySubVersionChange(subVersion, clusterSnapshot.getSubVersion(), taskRuntimeConfig);
            this.subVersion = clusterSnapshot.getSubVersion();
            this.executionConfig = taskRuntimeConfig.getExecutionConfig();
        }
    }

    void notifySubVersionChange(final long oldSubVersion, final long newSubVersion,
                                final TaskRuntimeConfig taskRuntimeConfig) {

        ExecutorService executorService = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("sub-version-callback-thread-%d").build());
        Future<?> future = executorService.submit(
            () -> subVersionChangeCallback.onSubVersionChange(oldSubVersion, newSubVersion, taskRuntimeConfig));

        try {
            log.info("sub version callback started, from {} to {} with main version {}",
                oldSubVersion, newSubVersion, version);
            long start = System.currentTimeMillis();

            while (!future.isDone()) {
                try {
                    future.get(interval, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    checkInWaitSubVersionCallback(start, oldSubVersion, newSubVersion);
                }
            }

            long end = System.currentTimeMillis();
            log.info("sub version callback finished, from {} to {} with main version {}, cost {} ms",
                oldSubVersion, newSubVersion, version, end - start);
        } catch (Throwable t) {
            log.error("something goes wrong when handling sub version change, from {} to {}, process will exit.",
                oldSubVersion, newSubVersion, t);
            processExitCallback.accept(null);
        }
    }

    void checkInWaitSubVersionCallback(long start, long oldSubVersion, long newSubVersion) {
        ClusterSnapshot clusterSnapshot = queryClusterSnapshot();
        checkMainVersion(clusterSnapshot);
        updateHeartbeat();

        long timeoutMills = getLong(TOPOLOGY_WAIT_SUB_VERSION_CALLBACK_TIMEOUT_MS);
        if (System.currentTimeMillis() - start >= timeoutMills) {
            throw new PolardbxException("wait sub version callback timeout, from " + oldSubVersion + " to "
                + newSubVersion);
        }
    }
}
