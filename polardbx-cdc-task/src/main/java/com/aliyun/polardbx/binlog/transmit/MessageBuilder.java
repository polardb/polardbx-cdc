/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.EventData;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.PacketMode;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnEnd;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_TXN_STREAM_PACKET_MODE;

/**
 * created by ziyang.lb
 **/
public class MessageBuilder {
    public static PacketMode packetMode = getActualPacketMode();

    private static PacketMode getActualPacketMode() {
        PacketMode packetMode = PacketMode.valueOf(DynamicApplicationConfig.getString(
            BINLOG_TXN_STREAM_PACKET_MODE));
        if (packetMode == PacketMode.RANDOM) {
            List<PacketMode> list = Lists.newArrayList(PacketMode.BYTES, PacketMode.OBJECT);
            Collections.shuffle(list);
            return list.get(0);
        } else {
            return packetMode;
        }
    }

    public static DumpReply buildDumpReply(TxnMessage message) {
        if (packetMode == PacketMode.OBJECT) {
            return DumpReply.newBuilder().addTxnMessage(message).setPacketMode(packetMode).build();
        } else {
            return DumpReply.newBuilder().addTxnMessageBytes(message.toByteString())
                .setPacketMode(packetMode).build();
        }
    }

    public static TxnBegin buildTxnBegin(TxnToken token, TaskType taskType) {
        TxnBegin txnBegin;
        if (taskType == TaskType.Relay) {
            txnBegin = TxnBegin.newBuilder().setTxnToken(token).build();
        } else {
            txnBegin = TxnBegin.newBuilder().setTxnMergedToken(buildTxnMergedToken(token)).build();
        }
        return txnBegin;
    }

    public static TxnMessage buildTxnMessage(TxnToken token, TaskType taskType, TxnBuffer buffer) {
        // make begin
        TxnBegin txnBegin = buildTxnBegin(token, taskType);

        // make data
        List<TxnItem> items = new ArrayList<>();
        Iterator<TxnItemRef> iterator = buffer.parallelRestoreIterator();
        while (iterator.hasNext()) {
            TxnItemRef txnItemRef = iterator.next();
            EventData eventData = txnItemRef.getEventData();
            TxnItem txnItem = TxnItem.newBuilder()
                .setTraceId(txnItemRef.getTraceId())
                .setRowsQuery(eventData.getRowsQuery())
                .setEventType(txnItemRef.getEventType())
                .setPayload(eventData.getPayload())
                .setSchema(checkAndGetSchemaName(eventData))
                .setTable(checkAndGetTableName(eventData))
                .build();
            items.add(txnItem);
            txnItemRef.clearEventData();//尽快释放内存空间，防止内存溢出
        }
        TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();

        // make end
        TxnEnd end = TxnEnd.newBuilder().build();

        // return
        return TxnMessage.newBuilder().setType(MessageType.WHOLE).setTxnBegin(txnBegin)
            .setTxnData(txnData).setTxnEnd(end).build();
    }

    public static TxnMergedToken buildTxnMergedToken(TxnToken token) {
        TxnMergedToken mergedToken = TxnMergedToken.newBuilder()
            .setType(token.getType())
            .setSchema(token.getSchema())
            .setTso(token.getTso())
            .setPayload(token.getPayload())
            .setTable(token.getTable())
            .setTxnFlag(token.getTxnFlag())
            .build();
        if (token.hasServerId()) {
            mergedToken = mergedToken.toBuilder().setServerId(token.getServerId()).build();
        }
        return mergedToken;
    }

    public static void tryRestore(TxnToken token, Storage storage) {
        if (token.getAllPartiesCount() > 0) {
            token.getAllPartiesList().stream().forEach(p -> {
                TxnKey key = new TxnKey(token.getTxnId(), p);
                storage.fetch(key).restore();
            });
        } else {
            TxnKey key = new TxnKey(token.getTxnId(), token.getPartitionId());
            storage.fetch(key).restore();
        }
    }

    public static void tryRestore(List<TxnBuffer> txnBuffers) {
        txnBuffers.forEach(TxnBuffer::restore);
    }

    public static String checkAndGetSchemaName(EventData eventData) {
        if (StringUtils.isBlank(eventData.getSchemaName())) {
            throw new PolardbxException("schema name should not be null");
        }
        return eventData.getSchemaName();
    }

    public static String checkAndGetTableName(EventData eventData) {
        if (StringUtils.isBlank(eventData.getTableName())) {
            throw new PolardbxException("table name should not be null");
        }
        return eventData.getTableName();
    }
}
