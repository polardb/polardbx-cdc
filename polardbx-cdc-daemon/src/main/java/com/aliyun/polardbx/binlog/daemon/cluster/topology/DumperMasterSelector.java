/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.GetDumperInfoResponse;
import com.aliyun.polardbx.rpc.cdc.ShowBinlogDumpStatusRequest;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.alibaba.fastjson.JSON.parseObject;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY;

@Slf4j
@Data
public class DumperMasterSelector {

    private String clusterId;
    private NodeInfoMapper nodeInfoMapper;
    private DumperInfoMapper dumperInfoMapper;

    public DumperMasterSelector(String clusterId, NodeInfoMapper nodeInfoMapper,
                                DumperInfoMapper dumperInfoMapper) {
        this.clusterId = clusterId;
        this.nodeInfoMapper = nodeInfoMapper;
        this.dumperInfoMapper = dumperInfoMapper;
    }

    public String selectDumperMasterNode(Set<String> containers, ClusterSnapshot preClusterSnapshot) {

        //如果强制指定了master node, 且node状态正常，则使用强制指定的node
        String assignedDumperNode = DynamicApplicationConfig.getString(CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY);
        if (StringUtils.isNotBlank(assignedDumperNode) && containers.contains(assignedDumperNode)) {
            log.info("Dumper master node is selected in force mode, with name {}", assignedDumperNode);
            return assignedDumperNode;
        }

        //取位点最大的Container对应的Dumper为MasterDumper
        log.info("prepare to select dumper master node, with containers {}.", containers);
        List<Pair<String, BinlogCursor>> allCursors = getDumperCursors(containers);
        Map<BinlogCursor, List<Pair<String, BinlogCursor>>> cursorAsKeyMap = allCursors.stream()
            .collect(Collectors.groupingBy(Pair::getRight));
        Optional<BinlogCursor> maxCursorOptional = cursorAsKeyMap.keySet().stream().max(Comparator.comparing(s -> s));

        String preDumperMasterNode = preClusterSnapshot.getDumperMasterNode();
        Optional<Pair<String, BinlogCursor>> cursorForPreDumperMaster = allCursors.stream()
            .filter(p -> p.getKey().equals(preDumperMasterNode)).findFirst();

        if (maxCursorOptional.isPresent()) {
            log.info("find max cursor success, with cursor {}.", maxCursorOptional.get());

            //如果有多个container的cursor并列最大，优先取上一个dumper master
            Set<String> maxCursorContainers = cursorAsKeyMap.get(maxCursorOptional.get())
                .stream().map(Pair::getKey).collect(Collectors.toSet());
            if (StringUtils.isNotBlank(preDumperMasterNode) && cursorForPreDumperMaster.isPresent()) {
                if (maxCursorContainers.contains(preDumperMasterNode)) {
                    log.info("Dumper master node is selected in inherit mode by using max cursor, with name {}.",
                        preDumperMasterNode);
                    return preDumperMasterNode;
                } else if (recheckDumperMasterCursor(preDumperMasterNode, preClusterSnapshot.getDumperMaster(),
                    maxCursorOptional.get())) {
                    log.info("Dumper master node is selected in inherit mode by querying latest cursor, with name {}.",
                        preDumperMasterNode);
                    return preDumperMasterNode;
                }
            }

            // 如果获取不到，则随机取一个
            String selectedContainer = cursorAsKeyMap.get(maxCursorOptional.get()).get(0).getKey();
            log.info("Dumper master node is selected in random mode by using max cursor, with name {}.",
                selectedContainer);
            return selectedContainer;
        }

        //优先取上一次的Node继续当master
        if (StringUtils.isNotBlank(preDumperMasterNode) && containers.contains(preDumperMasterNode)) {
            log.info("Dumper master node is selected in inherit mode without using max cursor, with name {}.",
                preDumperMasterNode);
            return preDumperMasterNode;
        }

        //随机取一个container作为MasterNode
        String masterNode = containers.stream().findAny().get();
        log.warn("Dumper master is selected in random mode by find any, with name {}.", masterNode);
        return masterNode;
    }

    List<Pair<String, BinlogCursor>> getDumperCursors(Set<String> containers) {
        List<Pair<String, BinlogCursor>> result = nodeInfoMapper.select(s -> s
                .where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isIn(containers)))
            .stream()
            .filter(d -> StringUtils.isNotBlank(d.getLatestCursor()))
            .map(s -> new ImmutablePair<>(
                s.getContainerId(),
                parseObject(s.getLatestCursor(), BinlogCursor.class)))
            .collect(Collectors.toList());
        log.info("find dumper cursors success, with cursors {}.", result);
        return result;
    }

    boolean recheckDumperMasterCursor(String dumperMasterNode, String dumperMasterName, BinlogCursor maxCursor) {
        log.info("prepare to recheck dumper master cursor, {}:{}.", dumperMasterName, dumperMasterNode);
        List<DumperInfo> list = dumperInfoMapper.select(s -> s
            .where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
            .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(dumperMasterName))
            .and(DumperInfoDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(dumperMasterNode)));

        if (!list.isEmpty()) {
            log.info("dumper master node is found, will query latest cursor , {}:{}:{}:{}.", dumperMasterName,
                dumperMasterNode, list.get(0).getIp(), list.get(0).getPort());
            ManagedChannel channel = null;
            try {
                channel = NettyChannelBuilder.forAddress(list.get(0).getIp(), list.get(0).getPort())
                    .usePlaintext().maxInboundMessageSize(Integer.MAX_VALUE).build();
                ShowBinlogDumpStatusRequest request = ShowBinlogDumpStatusRequest
                    .newBuilder().setStreamName(STREAM_NAME_GLOBAL).build();
                GetDumperInfoResponse response = CdcServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(2, TimeUnit.SECONDS).getDumperInfo(request);
                if (response != null) {
                    log.info("dumper master node latest cursor is {}:{}, the comparing cursor is {}:{} .",
                        response.getFile(), response.getPosition(), maxCursor.getFileName(),
                        maxCursor.getFilePosition());
                    BinlogCursor cursor = new BinlogCursor(response.getFile(), response.getPosition());
                    return cursor.compareTo(maxCursor) >= 0;
                }
            } catch (Throwable e) {
                log.error("get dumper master latest cursor error, {}:{}:{}:{}.", dumperMasterName, dumperMasterNode,
                    list.get(0).getIp(), list.get(0).getPort(), e);
            } finally {
                if (channel != null) {
                    channel.shutdown();
                }
            }
        } else {
            log.warn("skip recheck dumper master cursor, because dumper master node not exist, {}:{}.",
                dumperMasterName, dumperMasterNode);
        }

        return false;
    }
}
