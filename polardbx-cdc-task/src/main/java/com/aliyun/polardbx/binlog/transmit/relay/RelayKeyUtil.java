/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.google.common.base.Joiner;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.util.ByteUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 **/
public class RelayKeyUtil {

    public static String buildMinRelayKeyStr(String tso) {
        return tso + "_" + LogEventUtil.buildTraceId(null, null) + "_" + StringUtils
            .leftPad(String.valueOf(0), 19, "0");
    }

    public static byte[] buildMinRelayKey(String tso) {
        return ByteUtil.bytes(buildMinRelayKeyStr(tso));
    }

    public static String buildRelayKeyStr(String tso, String traceId, long subSeq) {
        return tso + "_" + traceId + "_" + StringUtils.leftPad(String.valueOf(subSeq), 19, "0");
    }

    public static byte[] buildRelayKey(String tso, String traceId, long subSeq) {
        return ByteUtil.bytes(buildRelayKeyStr(tso, traceId, subSeq));
    }

    public static byte[] buildRelayKey(String keyStr) {
        return ByteUtil.bytes(keyStr);
    }

    public static String extractTsoFromKey(byte[] bytes) {
        String keyStr = new String(bytes);
        return StringUtils.substringBefore(keyStr, "_");
    }

    public static String buildPrimaryKeyString(List<ByteString> keyList) {
        if (keyList == null || keyList.isEmpty()) {
            return "NULL";
        } else if (keyList.size() == 1) {
            return keyList.get(0).toStringUtf8();
        } else {
            List<String> strList = keyList.stream().map(ByteString::toStringUtf8).collect(Collectors.toList());
            return Joiner.on("-").join(strList);
        }
    }
}
