/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.GLOBAL_BINLOG_LATEST_CURSOR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RECOVER_TSO_BINLOG_NUM_LIMIT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RECOVER_TSO_TIME_LIMIT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RECOVER_TSO_TYPE;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * 目前支持两种recover方式：
 * 1. 从binlog_oss_record表中提取last_tso作为recover tso
 * 2. 从heartbeat的latest_cursor中提取latest_tso作为recover tso
 *
 * @author yudong
 * @since 2022/11/28 17:02
 **/
@Slf4j
public class RecoverTsoBuilder {

    /**
     * 为指定stream构造recover info
     *
     * @param streamName 流的名字
     * @return {recover tso, binlog file name, recover type}
     */
    public static List<String> buildRecoverInfo(String groupName, String streamName, String expectedStorageTso) {
        String recoverType = DynamicApplicationConfig.getString(TOPOLOGY_RECOVER_TSO_TYPE);
        RecoverTsoType recoverTsoEnum = RecoverTsoType.typeOf(recoverType);

        // left: tso, right: fileName
        Pair<String, String> pair;
        if (recoverTsoEnum == RecoverTsoType.BINLOG_RECORD) {
            log.info("get recover tso from binlog record");
            pair = extractRecoverTsoFromBinlogRecord(groupName, streamName, expectedStorageTso);
        } else if (recoverTsoEnum == RecoverTsoType.LATEST_CURSOR) {
            log.info("get recover tso from latest cursor");
            pair = extractRecoverTsoFromLatestCursor(groupName, streamName);
        } else {
            log.info("recover tso type:{}, will set recover tso to origin tso", recoverType);
            pair = Pair.of(ExecutionConfig.ORIGIN_TSO, ExecutionConfig.ORIGIN_BINLOG_FILE);
        }
        return Lists.newArrayList(pair.getLeft(), pair.getRight(), recoverType);
    }

    private static Pair<String, String> extractRecoverTsoFromBinlogRecord(String groupName, String streamName,
                                                                          String expectedStorageTso) {
        String tso;
        String fileName;

        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        int recoverHourLimit = Integer.parseInt(DynamicApplicationConfig.getString(
            TOPOLOGY_RECOVER_TSO_TIME_LIMIT));
        int recoverNumLimit = Integer.parseInt(DynamicApplicationConfig.getString(
            TOPOLOGY_RECOVER_TSO_BINLOG_NUM_LIMIT));
        log.info("recover time limit:{} hours, recover num limit:{}", recoverHourLimit, recoverNumLimit);

        Optional<BinlogOssRecord> record =
            getObject(BinlogOssRecordService.class).getRecordForRecovery(groupName, streamName, clusterId,
                recoverHourLimit, recoverNumLimit, expectedStorageTso);
        if (record.isPresent()) {
            tso = record.get().getLastTso();
            fileName = record.get().getBinlogFile();
        } else {
            log.info("recover record in time limit and count limit is null");
            record = getObject(BinlogOssRecordService.class).getLastTsoRecord(groupName, streamName,
                clusterId);
            if (record.isPresent()) {
                log.info("find a record with last tso:{}", record.get().getLastTso());
                tso = record.get().getLastTso();
                fileName = record.get().getBinlogFile();
            } else {
                log.info("cannot find a tso, will use origin tso");
                tso = ExecutionConfig.ORIGIN_TSO;
                fileName = ExecutionConfig.ORIGIN_BINLOG_FILE;
            }
        }
        log.info("recover tso:{}, file name:{}", tso, fileName);
        return Pair.of(tso, fileName);
    }

    private static Pair<String, String> extractRecoverTsoFromLatestCursor(String groupName, String streamName) {
        if (CommonUtils.isGlobalBinlog(groupName, streamName)) {
            return extractRecoverTsoFromLatestCursorForGlobalBinlog();
        } else {
            return extractRecoverTsoFromLatestCursorForStream(groupName, streamName);
        }
    }

    private static Pair<String, String> extractRecoverTsoFromLatestCursorForGlobalBinlog() {
        String cursorStr = SystemDbConfig.getSystemDbConfig(GLOBAL_BINLOG_LATEST_CURSOR);
        log.info("latest cursor for recover :{}", cursorStr);
        if (StringUtils.isNotBlank(cursorStr)) {
            BinlogCursor latestCursor = JSONObject.parseObject(cursorStr, BinlogCursor.class);
            return Pair.of(latestCursor.getTso(), latestCursor.getFileName());
        } else {
            return Pair.of(ExecutionConfig.ORIGIN_TSO, ExecutionConfig.ORIGIN_BINLOG_FILE);
        }
    }

    private static Pair<String, String> extractRecoverTsoFromLatestCursorForStream(String groupName,
                                                                                   String streamName) {
        XStreamMapper xStreamMapper =
            SpringContextHolder.getObject(XStreamMapper.class);
        Optional<XStream> xStream =
            xStreamMapper.selectOne(s -> s.where(XStreamDynamicSqlSupport.groupName, SqlBuilder.isEqualTo(groupName))
                .and(XStreamDynamicSqlSupport.streamName, SqlBuilder.isEqualTo(streamName)));
        if (xStream.isPresent()) {
            String cursorStr = xStream.get().getLatestCursor();
            log.info("latest cursor of stream:{} for recover:{}", streamName, cursorStr);
            BinlogCursor latestCursor = JSONObject.parseObject(cursorStr, BinlogCursor.class);
            return Pair.of(latestCursor.getTso(), latestCursor.getFileName());
        } else {
            return Pair.of(ExecutionConfig.ORIGIN_TSO, ExecutionConfig.ORIGIN_BINLOG_FILE);
        }
    }
}
