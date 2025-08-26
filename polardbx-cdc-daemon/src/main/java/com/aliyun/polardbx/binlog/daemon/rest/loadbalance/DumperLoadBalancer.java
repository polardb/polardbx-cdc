/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.loadbalance;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DumperLoadBalanceMode;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.rest.tools.NodeAddressUtil.NodeAddress;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * @author zm
 */
public class DumperLoadBalancer {
    private static final long BPS_INTERVAL = 40L;
    private final DumperLoadBalanceMode mode;
    private final Map<String, DumperInfoForLoadBalance> ip2DumperInfo;
    private final NodeAddress masterAddress;

    public DumperLoadBalancer(DumperLoadBalanceMode mode, Map<String, DumperInfoForLoadBalance> ip2DumperInfo,
                              NodeAddress masterAddress) {
        this.mode = mode;
        this.ip2DumperInfo = ip2DumperInfo;
        this.masterAddress = masterAddress;
        int masterWeight = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_MASTER_WEIGHT);
        int taskWeight = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_FINAL_WEIGHT);
        int slaveWeight = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_SLAVE_WEIGHT);
        for (DumperInfoForLoadBalance dumperInfo : ip2DumperInfo.values()) {
            if (dumperInfo.isMasterNode()) {
                dumperInfo.setWeight(masterWeight);
            } else if (dumperInfo.isTaskNode()) {
                dumperInfo.setWeight(taskWeight);
            } else {
                dumperInfo.setWeight(slaveWeight);
            }
        }
    }

    /**
     * @return ip:port
     */
    public NodeAddress nextDumperNode() {
        switch (mode) {
        case COUNT:
            return nextDumperNodeByCount();
        case MIXED:
            return nextDumperNodeByMixed();
        case ASSIGNED:
        default:
            return nextDumperNodeByAssigned();
        }
    }

    /**
     * 优先选链路数最少的dumper，如果两个dumper链路数相同则选bps少的，如果bps差不多，选cpu利用率低的
     */
    private NodeAddress nextDumperNodeByCount() {
        Comparator<DumperInfoForLoadBalance> comparator = (o1, o2) -> {
            int ret = Double.compare(o1.getSessionCountWithWeight(), o2.getSessionCountWithWeight());
            if (ret == 0) {
                long res = o1.getResponse().getAvgDumpBpsSum() - o2.getResponse().getAvgDumpBpsSum();
                if (Math.abs(res) <= BPS_INTERVAL) {
                    return Double.compare(o1.getResponse().getCpuUsage(), o2.getResponse().getCpuUsage());
                } else {
                    return Long.compare(res, 0);
                }
            }
            return ret;
        };
        Optional<DumperInfoForLoadBalance> dumperInfo = ip2DumperInfo.values().stream().min(comparator);
        return dumperInfo.map(
                dumperInfoForLoadBalance -> new NodeAddress(dumperInfoForLoadBalance.getIp(),
                    dumperInfoForLoadBalance.getPort()))
            .orElse(masterAddress);
    }

    /**
     * 根据不同参数的权重，计算出一个总和，然后选总和最小的dumper
     */
    private NodeAddress nextDumperNodeByMixed() {
        Comparator<DumperInfoForLoadBalance> comparator =
            Comparator.comparingDouble(DumperInfoForLoadBalance::getMixedWeight);
        Optional<DumperInfoForLoadBalance> dumperInfo = ip2DumperInfo.values().stream().min(comparator);
        return dumperInfo.map(
                dumperInfoForLoadBalance -> new NodeAddress(dumperInfoForLoadBalance.getIp(),
                    dumperInfoForLoadBalance.getPort()))
            .orElse(masterAddress);
    }

    /**
     * 返回指定的dumper ip:port
     * 如果没有指定的dumper，则返回master
     */
    private NodeAddress nextDumperNodeByAssigned() {
        String address = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_FROM_SLAVE_ASSIGNED_IP_PORT);
        if (!StringUtils.isBlank(address)) {
            address = address.replaceAll("^['\"]|['\"]$", "");
            String ip = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);
            return new NodeAddress(ip, port);
        }
        return masterAddress;
    }
}
