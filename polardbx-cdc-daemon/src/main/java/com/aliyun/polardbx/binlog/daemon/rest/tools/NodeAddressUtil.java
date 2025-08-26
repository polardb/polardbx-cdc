/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.tools;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;

import com.github.rholder.retry.WaitStrategies;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author zm
 */
@Slf4j
public class NodeAddressUtil {
    public static final Map<String, NodeAddress> MASTER_ADDRESS_MAP = new ConcurrentHashMap<>();

    public static List<NodeAddress> getDumperAddressList(String instId) {
        List<NodeAddress> addressList = new ArrayList<>();
        List<DumperInfo> dumperInfoList = getMetaDbDumperInfos(instId);
        for (DumperInfo dumperInfo : dumperInfoList) {
            addressList.add(new NodeAddress(dumperInfo.getIp(), dumperInfo.getPort()));
            if ("M".equalsIgnoreCase(dumperInfo.getRole())) {
                MASTER_ADDRESS_MAP.put(instId,
                    new NodeAddress(dumperInfo.getIp(), dumperInfo.getPort()));
            }
        }
        return addressList;
    }

    public static List<NodeAddress> getTaskAddressList(String instId) {
        List<NodeAddress> addressList = new ArrayList<>();
        List<BinlogTaskInfo> taskInfoList = getMetaDbTaskInfos(instId);
        for (BinlogTaskInfo taskInfo : taskInfoList) {
            addressList.add(new NodeAddress(taskInfo.getIp(), taskInfo.getPort()));
        }
        return addressList;
    }

    private static List<DumperInfo> getMetaDbDumperInfos(String instId) {
        DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        Retryer<List<DumperInfo>> retryer =
            RetryerBuilder.<List<DumperInfo>>newBuilder().retryIfResult(List::isEmpty)
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();

        List<DumperInfo> dumperInfoList;
        try {
            dumperInfoList = retryer.call(() -> dumperInfoMapper.select(s -> s
                .where(DumperInfoDynamicSqlSupport.status, isEqualTo(0))
                .and(DumperInfoDynamicSqlSupport.polarxInstId, isEqualTo(instId))
            ));
        } catch (Exception e) {
            log.error("No Dumper Infos in metaDB!", e);
            throw new RuntimeException(e);
        }
        return dumperInfoList;
    }

    private static List<BinlogTaskInfo> getMetaDbTaskInfos(String instId) {
        BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
        Retryer<List<BinlogTaskInfo>> retryer =
            RetryerBuilder.<List<BinlogTaskInfo>>newBuilder().retryIfResult(List::isEmpty)
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(10)).build();

        List<BinlogTaskInfo> taskInfoList;
        try {
            taskInfoList = retryer.call(() -> taskInfoMapper.select(s -> s
                .where(BinlogTaskInfoDynamicSqlSupport.status, isEqualTo(0))
                .and(BinlogTaskInfoDynamicSqlSupport.polarxInstId, isEqualTo(instId))
            ));
        } catch (Exception e) {
            log.error("No Task Infos in metaDB!", e);
            throw new RuntimeException(e);
        }

        return taskInfoList;
    }

    @AllArgsConstructor
    @Data
    public static class NodeAddress {
        public String ip;
        public int port;

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
