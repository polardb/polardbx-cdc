/*
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.entity.ContentType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonConstants.SUCCESS_CODE;
import static com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport.role;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author zm
 */
@Slf4j
public class BackupUtils {
    /**
     * 通过metaDB找daemon master 地址
     * 暂时没有代码会调用这里的函数，以待日后开发
     */
    public static Pair<String, Integer> getDaemonAddress(String groupName, String clusterId) {
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        Retryer<Optional<NodeInfo>> retryer = RetryerBuilder.<Optional<NodeInfo>>newBuilder()
            .retryIfResult(s -> !s.isPresent())
            .retryIfException()
            .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterAttempt(120)).build();

        Optional<NodeInfo> res;
        try {
            res = retryer.call(
                () -> nodeInfoMapper.selectOne(
                    s -> s.where(NodeInfoDynamicSqlSupport.groupName, isEqualTo(groupName))
                    .and(NodeInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                    .and(role, isEqualTo("M"))));
        } catch (Exception e) {
            log.error("The daemon node is not ready ......", e);
            throw new RuntimeException("The daemon node is not ready ......");
        }

        return Pair.of(res.get().getIp(), res.get().getDaemonPort());
    }
}
