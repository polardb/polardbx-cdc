/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.loadbalance;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpc.cdc.GetDumperInfoResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author zm
 */
@Data
@Slf4j
public class DumperInfoForLoadBalance {
    private String ip;
    private int port;
    private GetDumperInfoResponse response;
    private int sessionCount = -1;
    private boolean taskNode;
    private boolean masterNode;
    private int weight;
    private static final double WEIGHT_PER_BPS = 1L;
    /**
     * 100%  = 150M cost
     */
    private static final double WEIGHT_PER_CPU = 157286400;
    /**
     * 1 session  = 80M cost
     */
    private static final double WEIGHT_PER_SESSION = 83886080;
    private double costSum = -1;

    public DumperInfoForLoadBalance(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public double getSessionCountWithWeight() {
        if (weight <= 0) {
            return Double.MAX_VALUE;
        }
        // 权重越大的dumper 越应该被选中
        return sessionCount / (double) weight;
    }

    public double getMixedWeight() {
        if (weight <= 0) {
            return Double.MAX_VALUE;
        }
        if (costSum < 0) {
            double cpuWeight = DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_CPU_WEIGHT);
            double sessionWeight =
                DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_SESSION_COUNT_WEIGHT);
            double bpsWeight = DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_BPS_WEIGHT);
            double cpuCost = response.getCpuUsage() * WEIGHT_PER_CPU * cpuWeight;
            double sessionCost = sessionCount * WEIGHT_PER_SESSION * sessionWeight;
            double bpsCost = response.getAvgDumpBpsSum() * WEIGHT_PER_BPS * bpsWeight;
            costSum = cpuCost + bpsCost + sessionCost;
            // 权重越大的dumper 越应该被选中
            costSum /= weight;
            log.info("ip: {}, cpu cost: {}, bps cost: {}, session cost: {}. sum/weight: {}",
                ip, cpuCost, bpsCost, sessionCost, costSum);
        }
        return costSum;
    }
}
