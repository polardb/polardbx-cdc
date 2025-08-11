/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class XStreamService {

    public static String extractStorageInstId(String streamName) {
        int index = streamName.lastIndexOf("_");
        return streamName.substring(index + 1);
    }

    public static XStream buildAndSaveXStream(StorageInfo storageInfo, int index, String expectedStorageTso) {
        XStream xStream = buildXStream(storageInfo, index, expectedStorageTso);
        saveXStream(xStream);
        return xStream;
    }

    public static XStream buildXStream(StorageInfo storageInfo, int index, String expectedStorageTso) {
        String streamGroupName = getString(BINLOGX_STREAM_GROUP_NAME);
        String streamName = "";
        if (HashLevel.DATANODE == HashLevel.getCurrentHashLevel()) {
            Objects.requireNonNull(storageInfo);
            streamName = streamGroupName + "_" + storageInfo.getStorageInstId();
        } else {
            streamName = streamGroupName + "_stream_" + index;
        }
        XStream xStream = new XStream();
        xStream.setGroupName(streamGroupName);
        xStream.setStreamName(streamName);
        xStream.setExpectedStorageTso(expectedStorageTso);
        xStream.setStreamDesc("Binlog-X " + streamName);
        return xStream;
    }

    public static List<XStream> getXStreamsInCurrentCluster() {
        String streamGroupName = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        XStreamMapper mapper = SpringContextHolder.getObject(XStreamMapper.class);
        return mapper.select(s -> s.where(XStreamDynamicSqlSupport.groupName, isEqualTo(streamGroupName))
            .orderBy(XStreamDynamicSqlSupport.streamName));
    }

    public static XStream getXStreamByName(String streamName) {
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        Optional<XStream> xStream = xStreamMapper.selectOne(s -> s.where(XStreamDynamicSqlSupport.streamName,
            isEqualTo(streamName)));
        return xStream.orElse(null);
    }

    public static void markStreamAsPending(String streamName) {
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        xStreamMapper.update(s -> s.set(XStreamDynamicSqlSupport.status).equalTo(1)
            .where(XStreamDynamicSqlSupport.streamName, isEqualTo(streamName)));
    }

    public static boolean isStreamInPendingState(String streamName) {
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        Optional<XStream> xStream = xStreamMapper.selectOne(s -> s.where(XStreamDynamicSqlSupport.streamName,
            isEqualTo(streamName)));
        return xStream.get().getStatus() == 1;
    }

    public static void saveXStream(XStream xStream) {
        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        try {
            xStreamMapper.insertSelective(xStream);
        } catch (DuplicateKeyException e) {
            //do nothing
        }
    }
}
