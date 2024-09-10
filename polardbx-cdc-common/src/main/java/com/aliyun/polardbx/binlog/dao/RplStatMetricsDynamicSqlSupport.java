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
package com.aliyun.polardbx.binlog.dao;

import java.sql.JDBCType;
import java.util.Date;
import javax.annotation.Generated;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlTable;

public final class RplStatMetricsDynamicSqlSupport {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.644+08:00", comments="Source Table: rpl_stat_metrics")
    public static final RplStatMetrics rplStatMetrics = new RplStatMetrics();

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.id")
    public static final SqlColumn<Long> id = rplStatMetrics.id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.gmt_created")
    public static final SqlColumn<Date> gmtCreated = rplStatMetrics.gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.gmt_modified")
    public static final SqlColumn<Date> gmtModified = rplStatMetrics.gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.task_id")
    public static final SqlColumn<Long> taskId = rplStatMetrics.taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.out_rps")
    public static final SqlColumn<Long> outRps = rplStatMetrics.outRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.apply_count")
    public static final SqlColumn<Long> applyCount = rplStatMetrics.applyCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.646+08:00", comments="Source field: rpl_stat_metrics.in_eps")
    public static final SqlColumn<Long> inEps = rplStatMetrics.inEps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.out_bps")
    public static final SqlColumn<Long> outBps = rplStatMetrics.outBps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.in_bps")
    public static final SqlColumn<Long> inBps = rplStatMetrics.inBps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.out_insert_rps")
    public static final SqlColumn<Long> outInsertRps = rplStatMetrics.outInsertRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.out_update_rps")
    public static final SqlColumn<Long> outUpdateRps = rplStatMetrics.outUpdateRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.out_delete_rps")
    public static final SqlColumn<Long> outDeleteRps = rplStatMetrics.outDeleteRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.receive_delay")
    public static final SqlColumn<Long> receiveDelay = rplStatMetrics.receiveDelay;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.process_delay")
    public static final SqlColumn<Long> processDelay = rplStatMetrics.processDelay;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.merge_batch_size")
    public static final SqlColumn<Long> mergeBatchSize = rplStatMetrics.mergeBatchSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.647+08:00", comments="Source field: rpl_stat_metrics.rt")
    public static final SqlColumn<Long> rt = rplStatMetrics.rt;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.skip_counter")
    public static final SqlColumn<Long> skipCounter = rplStatMetrics.skipCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.skip_exception_counter")
    public static final SqlColumn<Long> skipExceptionCounter = rplStatMetrics.skipExceptionCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.persist_msg_counter")
    public static final SqlColumn<Long> persistMsgCounter = rplStatMetrics.persistMsgCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.msg_cache_size")
    public static final SqlColumn<Long> msgCacheSize = rplStatMetrics.msgCacheSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.cpu_use_ratio")
    public static final SqlColumn<Integer> cpuUseRatio = rplStatMetrics.cpuUseRatio;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.mem_use_ratio")
    public static final SqlColumn<Integer> memUseRatio = rplStatMetrics.memUseRatio;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.full_gc_count")
    public static final SqlColumn<Long> fullGcCount = rplStatMetrics.fullGcCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.worker_ip")
    public static final SqlColumn<String> workerIp = rplStatMetrics.workerIp;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.fsm_id")
    public static final SqlColumn<Long> fsmId = rplStatMetrics.fsmId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.total_commit_count")
    public static final SqlColumn<Long> totalCommitCount = rplStatMetrics.totalCommitCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.648+08:00", comments="Source field: rpl_stat_metrics.true_delay_mills")
    public static final SqlColumn<Long> trueDelayMills = rplStatMetrics.trueDelayMills;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.645+08:00", comments="Source Table: rpl_stat_metrics")
    public static final class RplStatMetrics extends SqlTable {
        public final SqlColumn<Long> id = column("id", JDBCType.BIGINT);

        public final SqlColumn<Date> gmtCreated = column("gmt_created", JDBCType.TIMESTAMP);

        public final SqlColumn<Date> gmtModified = column("gmt_modified", JDBCType.TIMESTAMP);

        public final SqlColumn<Long> taskId = column("task_id", JDBCType.BIGINT);

        public final SqlColumn<Long> outRps = column("out_rps", JDBCType.BIGINT);

        public final SqlColumn<Long> applyCount = column("apply_count", JDBCType.BIGINT);

        public final SqlColumn<Long> inEps = column("in_eps", JDBCType.BIGINT);

        public final SqlColumn<Long> outBps = column("out_bps", JDBCType.BIGINT);

        public final SqlColumn<Long> inBps = column("in_bps", JDBCType.BIGINT);

        public final SqlColumn<Long> outInsertRps = column("out_insert_rps", JDBCType.BIGINT);

        public final SqlColumn<Long> outUpdateRps = column("out_update_rps", JDBCType.BIGINT);

        public final SqlColumn<Long> outDeleteRps = column("out_delete_rps", JDBCType.BIGINT);

        public final SqlColumn<Long> receiveDelay = column("receive_delay", JDBCType.BIGINT);

        public final SqlColumn<Long> processDelay = column("process_delay", JDBCType.BIGINT);

        public final SqlColumn<Long> mergeBatchSize = column("merge_batch_size", JDBCType.BIGINT);

        public final SqlColumn<Long> rt = column("rt", JDBCType.BIGINT);

        public final SqlColumn<Long> skipCounter = column("skip_counter", JDBCType.BIGINT);

        public final SqlColumn<Long> skipExceptionCounter = column("skip_exception_counter", JDBCType.BIGINT);

        public final SqlColumn<Long> persistMsgCounter = column("persist_msg_counter", JDBCType.BIGINT);

        public final SqlColumn<Long> msgCacheSize = column("msg_cache_size", JDBCType.BIGINT);

        public final SqlColumn<Integer> cpuUseRatio = column("cpu_use_ratio", JDBCType.INTEGER);

        public final SqlColumn<Integer> memUseRatio = column("mem_use_ratio", JDBCType.INTEGER);

        public final SqlColumn<Long> fullGcCount = column("full_gc_count", JDBCType.BIGINT);

        public final SqlColumn<String> workerIp = column("worker_ip", JDBCType.VARCHAR);

        public final SqlColumn<Long> fsmId = column("fsm_id", JDBCType.BIGINT);

        public final SqlColumn<Long> totalCommitCount = column("total_commit_count", JDBCType.BIGINT);

        public final SqlColumn<Long> trueDelayMills = column("true_delay_mills", JDBCType.BIGINT);

        public RplStatMetrics() {
            super("rpl_stat_metrics");
        }
    }
}