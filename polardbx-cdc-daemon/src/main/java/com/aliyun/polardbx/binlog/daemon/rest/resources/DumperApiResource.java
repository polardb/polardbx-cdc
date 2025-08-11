/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.DumperLoadBalanceMode;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.daemon.rest.tools.NodeAddressUtil.NodeAddress;
import com.aliyun.polardbx.binlog.daemon.rest.loadbalance.DumperInfoForLoadBalance;
import com.aliyun.polardbx.binlog.daemon.rest.loadbalance.DumperLoadBalancer;
import com.aliyun.polardbx.binlog.rpc.DumperRpcClient;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.rpc.cdc.BinlogDumpStatus;
import com.aliyun.polardbx.rpc.cdc.GetDumperInfoResponse;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sun.jersey.spi.resource.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonConstants.FAILURE_CODE;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.SUCCESS_CODE;
import static com.aliyun.polardbx.binlog.daemon.rest.tools.NodeAddressUtil.MASTER_ADDRESS_MAP;
import static com.aliyun.polardbx.binlog.daemon.rest.tools.NodeAddressUtil.getDumperAddressList;
import static com.aliyun.polardbx.binlog.daemon.rest.tools.NodeAddressUtil.getTaskAddressList;

/**
 * @author zm
 */
@Slf4j
@Path("/dumper")
@Produces(MediaType.APPLICATION_JSON)
@ACL
@Singleton
public class DumperApiResource {
    /**
     * 用于并发向dumper发送rpc请求
     */
    @Setter
    private static ThreadPoolExecutor executor =
        new ThreadPoolExecutor(2, 32, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("binlog-dump-api-thread-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy());
    private static final long EXPIRE_MILLIS_SECOND =
        DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_API_SESSION_COUNT_CACHE_EXPIRE_MILLISECOND);
    private static final LoadingCache<String, Map<String, Integer>> DUMPER_CONSUMER_COUNT =
        CacheBuilder.newBuilder().expireAfterWrite(EXPIRE_MILLIS_SECOND, TimeUnit.MILLISECONDS).build(
            new CacheLoader<String, Map<String, Integer>>() {
                @Override
                public Map<String, Integer> load(@NotNull String instId) throws Exception {
                    List<NodeAddress> addressList = getDumperAddressList(instId);
                    Map<String, Integer> map = new HashMap<>(addressList.size());
                    List<Callable<Pair<String, GetDumperInfoResponse>>> tasks = createDumperInfoTasks(addressList);
                    List<Future<Pair<String, GetDumperInfoResponse>>> futures = executor.invokeAll(tasks);
                    for (Future<Pair<String, GetDumperInfoResponse>> future : futures) {
                        try {
                            Pair<String, GetDumperInfoResponse> pair = future.get();
                            if (pair.getValue() != null) {
                                map.put(pair.getKey(), pair.getValue().getSessionCount());
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            log.error("get dumper info failed", e);
                        }
                    }
                    return map;
                }
            }
        );

    /**
     * 为下游DUMP选出一个可服务的dumper ip:port,
     * 该方法在dumper返回第一个packet之前应该是串行的，
     * 串行控制逻辑在CN侧ServerConnection.dump实现
     */
    @POST
    @Path("/getTarget")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResultCode<String> getTarget(Map<String, String> params) {
        log.info("receive get target request with params: {}", params);

        String instId = params.get("instId");
        String fileName = params.get("fileName");
        long position = Long.parseLong(params.get("pos"));
        synchronized (DumperApiResource.class) {
            // 获取dumper，task节点地址
            List<NodeAddress> dumperAddressList = getDumperAddressList(instId);
            List<NodeAddress> taskAddressList = getTaskAddressList(instId);
            NodeAddress masterAddress = MASTER_ADDRESS_MAP.get(instId);

            if (masterAddress == null) {
                return new ResultCode<>(FAILURE_CODE, "No Dumper Master!", "");
            }

            // 没开启从节点对外服务,返回主节点
            if (!DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_FROM_SLAVE_ENABLED)) {
                log.info("BINLOG_DUMP_FROM_SLAVE_ENABLED=false, return master: {}", masterAddress.ip);
                return new ResultCode<>(SUCCESS_CODE, "", masterAddress.ip + ":" + masterAddress.port);
            }

            // 初始化ip2DumperInfoForLoadBalance
            Map<String, DumperInfoForLoadBalance> ip2DumperInfoForLoadBalance = new HashMap<>(dumperAddressList.size());
            initIp2DumperInfoForLoadBalance(dumperAddressList, taskAddressList, masterAddress,
                ip2DumperInfoForLoadBalance);

            // 初始化rpc task，并发执行获取dumper统计信息，之后按统计信息对dumper进行过滤
            List<Callable<Pair<String, GetDumperInfoResponse>>> tasks = createDumperInfoTasks(dumperAddressList);
            try {
                List<Future<Pair<String, GetDumperInfoResponse>>> futures = executor.invokeAll(tasks);
                log.info("all possible dumper before filter:{}, master address:{}",
                    ip2DumperInfoForLoadBalance.keySet(), masterAddress.ip);
                // 过滤延迟或位点差距过大的节点
                dumperNodeFilter(futures, ip2DumperInfoForLoadBalance, masterAddress.ip, fileName, position);
                log.info("all possible dumper after filter:{}", ip2DumperInfoForLoadBalance.keySet());
            } catch (Exception e) {
                log.error("Error invoking get binlog dump info tasks: ", e);
                return new ResultCode<>(SUCCESS_CODE, "success", masterAddress.ip + ":" + masterAddress.port);
            }

            // 仅对session count使用缓存
            Map<String, Integer> sessionCountMap = DUMPER_CONSUMER_COUNT.getUnchecked(instId);
            initSessionCount(sessionCountMap, ip2DumperInfoForLoadBalance);

            // 初始化负载均衡器
            DumperLoadBalanceMode
                mode = DumperLoadBalanceMode.valueOf(
                DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_MODE).toUpperCase());
            if (mode == DumperLoadBalanceMode.RANDOM) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                mode = DumperLoadBalanceMode.typeOf(random.nextInt(3));
            }
            if (mode == null) {
                mode = DumperLoadBalanceMode.COUNT;
                log.warn("do not set load balance mode, use COUNT as default.");
            }
            log.info("load balance mode is: {}", mode);
            DumperLoadBalancer dumperLoadBalancer =
                new DumperLoadBalancer(mode, ip2DumperInfoForLoadBalance, masterAddress);

            // 由负载均衡模式，获取下一个可服务的dumper节点
            NodeAddress address = dumperLoadBalancer.nextDumperNode();
            // 在获取到dumper节点后，更新session count缓存(如果有)
            if (sessionCountMap.containsKey(address.ip)) {
                sessionCountMap.put(address.ip, sessionCountMap.get(address.ip) + 1);
            }
            log.info("get dumper target address: {}", address);
            return new ResultCode<>(SUCCESS_CODE, "success", address.toString());
        }
    }

    /**
     * show binlog dump status with [ip:port]
     */
    @POST
    @Path("/showBinlogDumpStatus")
    public ResultCode<String> showBinlogDumpStatus(Map<String, String> params) {
        log.info("receive show binlog dump status request with params: {}", params);
        List<NodeAddress> addressList = getDumperAddressList(params.get("instId"));
        // 构建并发task
        List<Callable<Pair<String, List<BinlogDumpStatus>>>> tasks = createDumperStatusTasks(addressList);

        // 执行并拼接结果
        List<Map<String, String>> data = new ArrayList<>();
        try {
            List<Future<Pair<String, List<BinlogDumpStatus>>>> futures = executor.invokeAll(tasks);
            for (Future<Pair<String, List<BinlogDumpStatus>>> future : futures) {
                Pair<String, List<BinlogDumpStatus>> pair = future.get();
                String address = pair.getLeft();
                for (BinlogDumpStatus status : pair.getRight()) {
                    addBinlogDumpStatus(data, address, status);
                    log.info(address);
                    log.info(status.toString());
                }
            }
        } catch (Exception e) {
            log.error("Error invoking get binlog dump status tasks: ", e);
            return new ResultCode<>(FAILURE_CODE, e.getMessage(), JSON.toJSONString(data));
        }

        return new ResultCode<>(SUCCESS_CODE, "", JSON.toJSONString(data));
    }

    private void initIp2DumperInfoForLoadBalance(List<NodeAddress> dumperInfoList, List<NodeAddress> taskInfoList,
                                                 NodeAddress masterAddress,
                                                 Map<String, DumperInfoForLoadBalance> ip2DumperInfoForLoadBalance) {
        for (NodeAddress address : dumperInfoList) {
            DumperInfoForLoadBalance dumperInfoForLoadBalance =
                new DumperInfoForLoadBalance(address.getIp(), address.getPort());
            if (masterAddress.ip.equalsIgnoreCase(address.getIp())) {
                dumperInfoForLoadBalance.setMasterNode(true);
            }
            ip2DumperInfoForLoadBalance.putIfAbsent(address.getIp(), dumperInfoForLoadBalance);
        }

        for (NodeAddress address : taskInfoList) {
            DumperInfoForLoadBalance dumperInfoForLoadBalance = ip2DumperInfoForLoadBalance.get(address.getIp());
            if (dumperInfoForLoadBalance != null) {
                dumperInfoForLoadBalance.setTaskNode(true);
            }
        }
    }

    private void dumperNodeFilter(List<Future<Pair<String, GetDumperInfoResponse>>> futures,
                                  Map<String, DumperInfoForLoadBalance> ip2DumperInfo,
                                  String masterIp, String fileName, long position)
        throws ExecutionException, InterruptedException {
        long masterLastEventTimestamp = 0;

        // 过滤没有请求的位点的slave
        // 请求文件为空的情况下，默认请求的是最小文件
        if (!StringUtils.isBlank(fileName)) {
            long maxAcceptCursorDelay =
                DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_CURSOR_DELAY_BYTES_THRESHOLD);
            long fileSize = DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_FILE_SIZE);
            for (Future<Pair<String, GetDumperInfoResponse>> future : futures) {
                Pair<String, GetDumperInfoResponse> pair = future.get();
                if (pair != null) {
                    String ip = pair.getLeft();
                    GetDumperInfoResponse response = pair.getRight();
                    if (response != null) {
                        ip2DumperInfo.get(ip).setResponse(response);
                        log.info("ip: {}, dumperInfoResponse: {}", ip, response);
                        if (ip.equalsIgnoreCase(masterIp)) {
                            masterLastEventTimestamp = response.getLastEventTimestamp();
                            continue;
                        }
                        long ret = BinlogFileUtil.compareBinlogPosition(fileName, position, response.getFile(),
                            response.getPosition(), fileSize);
                        if (ret > maxAcceptCursorDelay) {
                            log.warn(
                                "filter dumper:{}, cursor is {}:{}, request cursor is {}:{}, maxAcceptCursorDelay: {}",
                                ip, response.getFile(), response.getPosition(), fileName, position,
                                maxAcceptCursorDelay);
                            ip2DumperInfo.remove(ip);
                        }
                    } else {
                        ip2DumperInfo.remove(ip);
                    }
                }
            }
        }

        // 过滤与master延迟较高的slave
        long maxAcceptDelay =
            DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DUMP_DELAY_THRESHOLD_MILLISECOND);
        for (Future<Pair<String, GetDumperInfoResponse>> future : futures) {
            Pair<String, GetDumperInfoResponse> pair = future.get();
            if (pair != null) {
                String ip = pair.getLeft();
                GetDumperInfoResponse response = pair.getRight();
                if (ip.equalsIgnoreCase(masterIp) || !ip2DumperInfo.containsKey(ip)) {
                    continue;
                }
                long delaySecond = masterLastEventTimestamp - response.getLastEventTimestamp();
                if (delaySecond * 1000 > maxAcceptDelay) {
                    log.warn("filter dumper:{}, delay is {}ms, max accept delay is {}ms",
                        ip, delaySecond * 1000, maxAcceptDelay);
                    ip2DumperInfo.remove(ip);
                }
            }
        }
    }

    private static List<Callable<Pair<String, GetDumperInfoResponse>>> createDumperInfoTasks(
        List<NodeAddress> addressList) {

        List<Callable<Pair<String, GetDumperInfoResponse>>> tasks = new ArrayList<>();
        for (NodeAddress address : addressList) {
            DumperRpcClient client = new DumperRpcClient(address.ip, address.port);
            client.connect();
            tasks.add(() -> {
                Pair<String, GetDumperInfoResponse> response;
                try {
                    response = client.getDumperInfo(STREAM_NAME_GLOBAL);
                } finally {
                    client.disconnect();
                }
                return response;
            });
        }
        return tasks;
    }

    private static List<Callable<Pair<String, List<BinlogDumpStatus>>>> createDumperStatusTasks(
        List<NodeAddress> addressList) {
        List<Callable<Pair<String, List<BinlogDumpStatus>>>> tasks = new ArrayList<>();
        for (NodeAddress address : addressList) {
            DumperRpcClient client = new DumperRpcClient(address.ip, address.port);
            client.connect();
            tasks.add(() -> {
                Pair<String, List<BinlogDumpStatus>> res;
                try {
                    res = client.showDumperStatus();
                } finally {
                    client.disconnect();
                }
                return res;
            });
        }
        return tasks;
    }

    private void addBinlogDumpStatus(List<Map<String, String>> data, String address,
                                     BinlogDumpStatus dumpStatus) {
        Map<String, String> map = new HashMap<>(10);
        String lastSyncTime = DateFormatUtils.format(dumpStatus.getLastSyncTimeStamp(), "yyyy-MM-dd HH:mm:ss");
        map.put("Process_Id", String.valueOf(dumpStatus.getId()));
        map.put("Trace_Id", dumpStatus.getTraceId());
        map.put("Dumper_Address", address);
        map.put("Client_Ip", dumpStatus.getIp());
        map.put("Client_Port", String.valueOf(dumpStatus.getPort()));
        map.put("Filename", dumpStatus.getFileName());
        map.put("Position", String.valueOf(dumpStatus.getPosition()));
        map.put("Delay", String.valueOf(dumpStatus.getDelay()));
        map.put("Bps", String.valueOf(dumpStatus.getBps()));
        map.put("Last_Sync_Timestamp", String.valueOf(lastSyncTime));
        map.put("Alive_Second", String.valueOf(dumpStatus.getAliveSecond()));
        data.add(map);
    }

    private void initSessionCount(Map<String, Integer> sessionCountMap,
                                  Map<String, DumperInfoForLoadBalance> ip2DumperInfoForLoadBalance) {
        for (Map.Entry<String, DumperInfoForLoadBalance> entry : ip2DumperInfoForLoadBalance.entrySet()) {
            if (sessionCountMap.containsKey(entry.getKey())) {
                entry.getValue().setSessionCount(sessionCountMap.get(entry.getKey()));
            } else {
                entry.getValue().setSessionCount(entry.getValue().getResponse().getSessionCount());
                log.error("sessionCountMap cache not contains ip:{}", entry.getKey());
            }
        }
    }
}


