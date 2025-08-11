/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.BinlogDumpStatus;
import com.aliyun.polardbx.rpc.cdc.GetDumperInfoResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static com.aliyun.polardbx.binlog.CommonConstants.FAILURE_CODE;
import static com.aliyun.polardbx.binlog.CommonConstants.SUCCESS_CODE;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class DumperApiResourceTest extends BaseTest {
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final BinlogTaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
    @Mock
    private ThreadPoolExecutor executor;
    private final List<Future<Object>> futures = new ArrayList<>();
    private final List<Pair<String, GetDumperInfoResponse>> pairs = new ArrayList<>();
    private final String[] ips = new String[] {"127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.4"};

    @Test
    @SneakyThrows
    public void testGetTarget() {
        setConfig(ConfigKeys.BINLOG_DUMP_API_SESSION_COUNT_CACHE_EXPIRE_MILLISECOND, "2000");
        final DumperApiResource dumperApiResource = new DumperApiResource();
        setConfig(ConfigKeys.BINLOG_DUMP_FROM_SLAVE_ENABLED, "true");
        Map<String, String> params = new HashMap<>(3);
        params.put("instId", "pxc-test-get-dumper-target");
        params.put("fileName", "binlog.000003");
        params.put("pos", "124");

        prepareDumper();
        prepareTask();
        prepareGetDumperInfoResponse();
        prepareGetDumperInfoFutures();

        DumperApiResource.setExecutor(executor);
        when(executor.invokeAll(anyList())).thenReturn(futures);

        // 测试指定dumper 消费
        setConfig(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_MODE, "ASSIGNED");
        setConfig(ConfigKeys.BINLOG_DUMP_FROM_SLAVE_ASSIGNED_IP_PORT, "'127.0.0.3:1203'");
        ResultCode<String> code = dumperApiResource.getTarget(params);
        Assert.assertEquals(SUCCESS_CODE, code.getCode());
        Assert.assertEquals("127.0.0.3:1203", code.getData());

        // 测试链路数优先
        setConfig(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_MODE, "COUNT");
        code = dumperApiResource.getTarget(params);
        Assert.assertEquals(SUCCESS_CODE, code.getCode());
        Assert.assertEquals("127.0.0.4:1204", code.getData());

        // 测试混合负载
        setConfig(ConfigKeys.BINLOG_DUMP_LOAD_BALANCE_MODE, "MIXED");
        code = dumperApiResource.getTarget(params);
        log.info(code.getCode() + code.getMsg());
        Assert.assertEquals(SUCCESS_CODE, code.getCode());
        Assert.assertEquals("127.0.0.4:1204", code.getData());

        setConfig(ConfigKeys.BINLOG_DUMP_FROM_SLAVE_ENABLED, "false");
        code = dumperApiResource.getTarget(params);
        Assert.assertTrue("127.0.0.1:1201".equalsIgnoreCase(code.getData()));
    }

    @Test
    @SneakyThrows
    public void testShowBinlogDumperStatus() {
        setConfig(ConfigKeys.BINLOG_DUMP_API_SESSION_COUNT_CACHE_EXPIRE_MILLISECOND, "2000");
        final DumperApiResource dumperApiResource = new DumperApiResource();
        Map<String, String> params = new HashMap<>(1);
        params.put("instId", "pxc-test-get-dumper-target");
        prepareDumper();
        List<Pair<String, List<BinlogDumpStatus>>> pairs = prepareShowDumpStatusResponse();
        List<Future<Object>> futures = prepareShowDumpStatusFutures(pairs);
        DumperApiResource.setExecutor(executor);
        when(executor.invokeAll(anyList())).thenReturn(futures);
        ResultCode<String> code = dumperApiResource.showBinlogDumpStatus(params);
        Assert.assertEquals(SUCCESS_CODE, code.getCode());
    }

    private void prepareDumper() {
        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setIp("127.0.0.1");
        dumperInfo.setPort(1201);
        dumperInfo.setRole("M");
        dumperInfo.setStatus(0);
        dumperInfo.setGmtCreated(new Date(System.currentTimeMillis()));
        dumperInfo.setGmtModified(new Date(System.currentTimeMillis()));
        dumperInfo.setGmtHeartbeat(new Date(System.currentTimeMillis()));
        dumperInfo.setClusterId("cluster-test-get-dumper-target");
        dumperInfo.setTaskName("Dumper-1");
        dumperInfo.setPolarxInstId("pxc-test-get-dumper-target");
        dumperInfo.setDelay(0L);
        dumperInfo.setContainerId("45862");
        dumperInfo.setVersion(3L);
        dumperInfoMapper.insert(dumperInfo);
        dumperInfo.setIp("127.0.0.2");
        dumperInfo.setTaskName("Dumper-2");
        dumperInfo.setPort(1202);
        dumperInfo.setRole("S");
        dumperInfoMapper.insert(dumperInfo);
        dumperInfo.setIp("127.0.0.3");
        dumperInfo.setTaskName("Dumper-3");
        dumperInfo.setPort(1203);
        dumperInfoMapper.insert(dumperInfo);
        dumperInfo.setIp("127.0.0.4");
        dumperInfo.setTaskName("Dumper-4");
        dumperInfo.setPort(1204);
        dumperInfoMapper.insert(dumperInfo);
    }

    private void prepareTask() {
        BinlogTaskInfo taskInfo = new BinlogTaskInfo();
        taskInfo.setGmtCreated(new Date(System.currentTimeMillis()));
        taskInfo.setGmtModified(new Date(System.currentTimeMillis()));
        taskInfo.setGmtHeartbeat(new Date(System.currentTimeMillis()));
        taskInfo.setClusterId("cluster-test-get-dumper-target");
        taskInfo.setPolarxInstId("pxc-test-get-dumper-target");
        taskInfo.setTaskName("Final");
        taskInfo.setIp("127.0.0.2");
        taskInfo.setStatus(0);
        taskInfo.setPort(5555);
        taskInfo.setContainerId("45862");
        taskInfo.setVersion(3L);
        taskInfoMapper.insert(taskInfo);
    }

    private void prepareGetDumperInfoResponse() {
        String[] fileName = {"binlog.000003", "binlog.000003", "binlog.000003", "binlog.000003"};
        long[] position = {125, 125, 4, 125};
        double[] cpuUsage = {0.3, 0.2, 0.1, 0.4};
        int[] sessionCount = {4, 3, 2, 1};
        long[] bps = {100, 200, 300, 400};
        for (int i = 0; i < ips.length; i++) {
            GetDumperInfoResponse response =
                GetDumperInfoResponse.newBuilder()
                    .setLastEventTimestamp(System.currentTimeMillis())
                    .setFile(fileName[i])
                    .setPosition(position[i])
                    .setCpuUsage(cpuUsage[i])
                    .setSessionCount(sessionCount[i])
                    .setAvgDumpBpsSum(bps[i])
                    .build();
            pairs.add(Pair.of(ips[i], response));
        }
    }

    private void prepareGetDumperInfoFutures() throws ExecutionException, InterruptedException {
        for (int i = 0; i < ips.length; i++) {
            Pair<String, GetDumperInfoResponse> pair = pairs.get(i);
            futures.add(Mockito.mock(Future.class));
            when(futures.get(i).get()).thenReturn(pair);
        }
    }

    private List<Pair<String, List<BinlogDumpStatus>>> prepareShowDumpStatusResponse() {
        List<Pair<String, List<BinlogDumpStatus>>> pairs = new ArrayList<>();
        for (int i = 0; i < ips.length; i++) {
            BinlogDumpStatus dumpStatus =
                BinlogDumpStatus.newBuilder().setId(i).setTraceId("" + i).setDelay(0).setAliveSecond(0).setBps(0)
                    .setIp(ips[i]).setPort(i).setFileName("binlog.000001").setPosition(4).setBps(200)
                    .setLastSyncTimeStamp(0).build();
            List<BinlogDumpStatus> list = new ArrayList<>();
            list.add(dumpStatus);
            pairs.add(Pair.of(ips[i] + ":1201", list));
        }
        return pairs;
    }

    private List<Future<Object>> prepareShowDumpStatusFutures(List<Pair<String, List<BinlogDumpStatus>>> pairs)
        throws ExecutionException, InterruptedException {
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < ips.length; i++) {
            Pair<String, List<BinlogDumpStatus>> pair = pairs.get(i);
            futures.add(Mockito.mock(Future.class));
            when(futures.get(i).get()).thenReturn(pair);
        }
        return futures;
    }
}
