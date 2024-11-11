/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.message;

import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;

import java.util.List;

/**
 * created by ziyang.lb
 **/
public class MessageEvent {

    private TxnToken token;
    private TxnMessage txnMessage;
    private ByteString txnMessageBytes;
    private long memSize;
    private boolean merged;
    private long tsoTimestamp;
    private List<TxnBuffer> txnBuffers;

    public MessageEvent() {
    }

    public MessageEvent(TxnToken token) {
        this.token = token;
    }

    public TxnToken getToken() {
        return token;
    }

    public void setToken(TxnToken token) {
        this.token = token;
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    public long getMemSize() {
        return memSize;
    }

    public void setMemSize(long memSize) {
        this.memSize = memSize;
    }

    public TxnMessage getTxnMessage() {
        return txnMessage;
    }

    public void setTxnMessage(TxnMessage txnMessage) {
        this.txnMessage = txnMessage;
    }

    public ByteString getTxnMessageBytes() {
        return txnMessageBytes;
    }

    public void setTxnMessageBytes(ByteString txnMessageBytes) {
        this.txnMessageBytes = txnMessageBytes;
    }

    public boolean isAlreadyBuild() {
        return txnMessage != null || txnMessageBytes != null;
    }

    public long getTsoTimestamp() {
        return tsoTimestamp;
    }

    public void setTsoTimestamp(long tsoTimestamp) {
        this.tsoTimestamp = tsoTimestamp;
    }

    public List<TxnBuffer> getTxnBuffers() {
        return txnBuffers;
    }

    public void setTxnBuffers(List<TxnBuffer> txnBuffers) {
        this.txnBuffers = txnBuffers;
    }

    public void clear() {
        this.token = null;
        this.merged = false;
        this.memSize = 0;
        this.txnMessage = null;
        this.txnMessageBytes = null;
        this.tsoTimestamp = 0;
        this.txnBuffers = null;
    }

    public MessageEvent copy() {
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.merged = merged;
        messageEvent.memSize = memSize;
        messageEvent.token = token;
        messageEvent.txnMessage = txnMessage;
        messageEvent.txnMessageBytes = txnMessageBytes;
        messageEvent.tsoTimestamp = tsoTimestamp;
        messageEvent.txnBuffers = txnBuffers == null ? null : Lists.newArrayList(txnBuffers);
        return messageEvent;
    }
}
