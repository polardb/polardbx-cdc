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
package com.aliyun.polardbx.binlog.task;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.DumperType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Date;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class TaskHeartbeat extends AbstractBinlogTimerTask {
    private final BinlogTaskConfig config;
    private final long version;
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
    private ICursorProvider cursorProvider;

    public TaskHeartbeat(String clusterId, String clusterType, String name, int interval, BinlogTaskConfig config) {
        super(clusterId, clusterType, name, interval);
        this.config = config;
        this.version = config.getVersion();
    }

    @Override
    public void exec() {
        String config = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot clusterSnapshot = JSONObject.parseObject(config, ClusterSnapshot.class);

        // 判断一下拓扑版本是否已经晋升到了更高的版本，如果是的话，本进程已经没有存在的必要了，直接退出即可
        if (clusterSnapshot != null && version < clusterSnapshot.getVersion()) {
            log.warn("Cluster topology has been migrated to new version, "
                + "this process will exit,  stale old version is {},"
                + "latest new version is {}", version, clusterSnapshot.getVersion());
            Runtime.getRuntime().halt(1);
        }

        if (name.startsWith("Dumper")) {
            Cursor cursor = cursorProvider.getLatestFileCursor();
            final boolean dumperLeader = RuntimeLeaderElector.isDumperLeader(name);

            //更新心跳
            int result = dumperInfoMapper.update(
                u -> u
                    .set(DumperInfoDynamicSqlSupport.gmtHeartbeat)
                    .equalTo(new Date())
                    .set(DumperInfoDynamicSqlSupport.role)
                    .equalTo(dumperLeader ? DumperType.MASTER.getName() : DumperType.SLAVE.getName())
                    .where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name))
            );

            if (result == 0) {
                log.error("Dumper info has been removed from database, this process will exit");
                Runtime.getRuntime().halt(1);
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
        } else {
            RelayFinalTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(RelayFinalTaskInfoMapper.class);
            int result = taskInfoMapper.update(
                u -> u
                    .set(RelayFinalTaskInfoDynamicSqlSupport.gmtHeartbeat).equalTo(new Date())
                    .where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name))
            );

            if (result == 0) {
                log.error("Task info has been removed from database, this process will exit");
                Runtime.getRuntime().halt(1);
            }
        }
    }

    public void setCursorProvider(ICursorProvider cursorProvider) {
        this.cursorProvider = cursorProvider;
    }
}
