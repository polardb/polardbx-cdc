/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnEnd;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnTag;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import lombok.Data;

import java.util.List;

/**
 * created by ziyang.lb
 **/
@Data
public class WriteItem {
    int streamSeq;
    TxnToken txnToken;
    String traceId;
    Long subSeq;
    List<TxnItem> itemList;
    String keyStr;
    byte[] key;

    public WriteItem(int streamSeq, TxnToken txnToken, String traceId, Long subSeq,
                     List<TxnItem> itemList) {
        this.streamSeq = streamSeq;
        this.txnToken = txnToken;
        this.traceId = traceId;
        this.subSeq = subSeq;
        this.itemList = itemList;
    }

    public WriteItem(int streamSeq, TxnToken txnToken) {
        this.streamSeq = streamSeq;
        this.txnToken = txnToken;
    }

    public TxnMessage toMessage() {
        if (txnToken.getType() == TxnType.DML) {
            return buildMessage(keyStr, txnToken, itemList);
        } else {
            return buildMessage(keyStr, txnToken);
        }
    }

    private TxnMessage buildMessage(String newTso, TxnToken token, List<TxnItem> txnItems) {
        TxnMergedToken mergedToken = buildTxnMergedToken(token, newTso);
        TxnBegin txnBegin = TxnBegin.newBuilder().setTxnMergedToken(mergedToken).build();
        TxnData txnData = TxnData.newBuilder().addAllTxnItems(txnItems).build();
        TxnEnd txnEnd = TxnEnd.newBuilder().build();
        return TxnMessage.newBuilder().setType(MessageType.WHOLE)
            .setTxnBegin(txnBegin).setTxnData(txnData).setTxnEnd(txnEnd).build();
    }

    private TxnMessage buildMessage(String newTso, TxnToken token) {
        TxnTag txnTag = TxnTag.newBuilder().setTxnMergedToken(buildTxnMergedToken(token, newTso)).build();
        return TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
    }

    public static TxnMergedToken buildTxnMergedToken(TxnToken token, String newTso) {
        TxnMergedToken mergedToken = TxnMergedToken.newBuilder()
            .setType(token.getType())
            .setSchema(token.getSchema())
            .setTso(newTso)
            .setPayload(token.getPayload())
            .setTable(token.getTable())
            .setTxnFlag(token.getTxnFlag())
            .build();
        if (token.hasServerId()) {
            mergedToken = mergedToken.toBuilder().setServerId(token.getServerId()).build();
        }
        return mergedToken;
    }
}
