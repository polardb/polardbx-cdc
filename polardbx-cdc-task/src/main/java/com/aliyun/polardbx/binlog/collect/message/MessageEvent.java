/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
