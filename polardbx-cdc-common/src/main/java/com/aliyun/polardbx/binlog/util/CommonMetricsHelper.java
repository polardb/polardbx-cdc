/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.proc.ProcSnapshot;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author ShuGuang
 */
@Slf4j
public class CommonMetricsHelper {
    private static final String TYPE = "平均值";
    private static final Map<String, CommonMetrics> DUMPER_M = Maps.newHashMap();
    private static final Map<String, CommonMetrics> DUMPER_S = Maps.newHashMap();
    private static final Map<String, CommonMetrics> DUMPER_X = Maps.newHashMap();
    private static final Map<String, CommonMetrics> DUMPER = Maps.newHashMap();
    private static final Map<String, CommonMetrics> TASK = Maps.newHashMap();
    private static final Map<String, CommonMetrics> DAEMON = Maps.newHashMap();
    private static final Map<String, CommonMetrics> COLUMNAR = Maps.newHashMap();
    private static final Map<String, CommonMetrics> REPLICA = Maps.newHashMap();
    private static final Map<String, CommonMetrics> ALL = Maps.newHashMap();

    private static final AtomicBoolean INITED = new AtomicBoolean(false);

    private static void init() {
        if (INITED.getAndSet(true)) {
            return;
        }
        List<String> lines;
        try {
            lines = FileUtils.readLines(
                new File(Resources.getResource("metrics.txt").getFile()), StandardCharsets.UTF_8);
            if (log.isDebugEnabled()) {
                log.debug("init metrics lines {}", lines);
            }

            for (String line : lines) {
                String[] ss = StringUtils.split(line, "|");
                if (ss.length < 5) {
                    continue;
                }
                if (ss[0].contains("_dumper_")) {
                    if (ss[0].contains("_dumper_m_")) {
                        DUMPER_M.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                            .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                    } else if (ss[0].contains("_dumper_s_")) {
                        DUMPER_S.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                            .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                    } else if (ss[0].contains("_dumper_x_")) {
                        DUMPER_X.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                            .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                    } else {
                        DUMPER.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                            .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                    }
                }

                if (ss[0].contains("_task_")) {
                    TASK.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                if (ss[0].contains("_daemon_")) {
                    DAEMON.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                if (ss[0].contains("_columnar_")) {
                    COLUMNAR.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                if (ss[0].contains("_replica_")) {
                    COLUMNAR.put(ss[3], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                        .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
                }
                ALL.put(ss[0], CommonMetrics.builder().key(ss[0]).desc(ss[4])
                    .type(StringUtils.equals(TYPE, ss[2]) ? 2 : 1).build());
            }

            if (log.isDebugEnabled()) {
                log.debug("init metrics done DUMPER_M {}", DUMPER_M);
                log.debug("init metrics done DUMPER_S {}", DUMPER_S);
                log.debug("init metrics done DUMPER {}", DUMPER);
                log.debug("init metrics done TASK {}", TASK);
                log.debug("init metrics done COLUMNAR {}", COLUMNAR);
                log.debug("init metrics done REPLICA {}", REPLICA);
            }
        } catch (IOException e) {
            log.error("prepare metrics fail", e);
        }

    }

    public static Map<String, CommonMetrics> getDumperM() {
        init();
        return DUMPER_M;
    }

    public static Map<String, CommonMetrics> getDumperS() {
        init();
        return DUMPER_S;
    }

    public static Map<String, CommonMetrics> getDumperX() {
        init();
        return DUMPER_X;
    }

    public static Map<String, CommonMetrics> getDumper() {
        init();
        return DUMPER;
    }

    public static Map<String, CommonMetrics> getTask() {
        init();
        return TASK;
    }

    public static Map<String, CommonMetrics> getColumnar() {
        init();
        return COLUMNAR;
    }

    public static Map<String, CommonMetrics> getReplica() {
        return REPLICA;
    }

    public static Map<String, CommonMetrics> getALL() {
        init();
        return ALL;
    }

    public static void addJvmMetrics(List<CommonMetrics> commonMetrics, JvmSnapshot jvmSnapshot, String prefix) {
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "youngUsed")
            .type(1)
            .value(jvmSnapshot.getYoungUsed())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "youngMax")
            .type(1)
            .value(jvmSnapshot.getYoungMax())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "oldUsed")
            .type(1)
            .value(jvmSnapshot.getOldUsed())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "oldMax")
            .type(1)
            .value(jvmSnapshot.getOldMax())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "heapUsage")
            .type(1)
            .value(calcHeapUseRatio(jvmSnapshot))
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "youngCollectionCount")
            .type(1)
            .value(jvmSnapshot.getYoungCollectionCount())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "oldCollectionCount")
            .type(1)
            .value(jvmSnapshot.getOldCollectionCount())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "youngCollectionTime")
            .type(1)
            .value(jvmSnapshot.getYoungCollectionTime())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "oldCollectionTime")
            .type(1)
            .value(jvmSnapshot.getOldCollectionTime())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "currentThreadCount")
            .type(1)
            .value(jvmSnapshot.getCurrentThreadCount())
            .build());
    }

    public static void addReplicaMetrics(List<CommonMetrics> commonMetrics, RplStatMetrics statMetrics, String prefix) {
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "trueDelayMills")
            .type(1)
            .value(statMetrics.getTrueDelayMills())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "outRps")
            .type(1)
            .value(statMetrics.getOutRps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "applyCount")
            .type(1)
            .value(statMetrics.getApplyCount())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "inEps")
            .type(1)
            .value(statMetrics.getInEps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "outBps")
            .type(1)
            .value(statMetrics.getOutBps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "inBps")
            .type(1)
            .value(statMetrics.getInBps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "outInsertRps")
            .type(1)
            .value(statMetrics.getOutInsertRps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "outUpdateRps")
            .type(1)
            .value(statMetrics.getOutUpdateRps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "outDeleteRps")
            .type(1)
            .value(statMetrics.getOutDeleteRps())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "receiveDelay")
            .type(1)
            .value(statMetrics.getReceiveDelay())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "processDelay")
            .type(1)
            .value(statMetrics.getProcessDelay())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "mergeBatchSize")
            .type(1)
            .value(statMetrics.getMergeBatchSize())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "rt")
            .type(1)
            .value(statMetrics.getRt())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "skipCounter")
            .type(1)
            .value(statMetrics.getSkipCounter())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "skipExceptionCounter")
            .type(1)
            .value(statMetrics.getSkipExceptionCounter())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "persistMsgCounter")
            .type(1)
            .value(statMetrics.getPersistMsgCounter())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "msgCacheSize")
            .type(1)
            .value(statMetrics.getMsgCacheSize())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "cpuUseRatio")
            .type(1)
            .value(statMetrics.getCpuUseRatio())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "memUseRatio")
            .type(1)
            .value(statMetrics.getMemUseRatio())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "fullGcCount")
            .type(1)
            .value(statMetrics.getFullGcCount())
            .build());
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "totalCommitCount")
            .type(1)
            .value(statMetrics.getTotalCommitCount())
            .build());
    }

    private static double calcHeapUseRatio(JvmSnapshot jvmSnapshot) {
        BigDecimal decimal1 = BigDecimal.valueOf(
            100 * (jvmSnapshot.getYoungUsed() + jvmSnapshot.getOldUsed() + jvmSnapshot.getMetaUsed()));
        BigDecimal decimal2 = BigDecimal.valueOf(
            (jvmSnapshot.getYoungMax() + jvmSnapshot.getOldMax() + jvmSnapshot.getMetaMax()));
        return decimal2.intValue() == 0 ? 0d : decimal1.divide(decimal2, 2, RoundingMode.HALF_UP).doubleValue();
    }

    public static void addProcMetrics(List<CommonMetrics> commonMetrics, ProcSnapshot procSnapshot, String prefix) {
        commonMetrics.add(CommonMetrics.builder()
            .key(prefix + "cpu_percent")
            .type(1)
            .value(Double.valueOf(procSnapshot.getCpuPercent() * 100.0D).longValue())
            .build());
    }
}
