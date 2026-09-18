/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class RelayStreamUtils {

    public static List<String> getStreamListAndCheck() {

        XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
        String streamGroupName = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        int streamCount = DynamicApplicationConfig.getInt(BINLOGX_STREAM_COUNT);

        List<String> streamsList = xStreamMapper.select(
            s -> s.where(XStreamDynamicSqlSupport.groupName, isEqualTo(streamGroupName))
                .orderBy(XStreamDynamicSqlSupport.streamName)).stream().map(
            XStream::getStreamName).collect(Collectors.toList());
        if (streamsList.size() != streamCount) {
            throw new PolardbxException("find mismatched stream count, configuration count is " + streamCount
                + ", count in binlog_x_stream table is " + streamsList.size());
        }
        return streamsList;
    }
}
