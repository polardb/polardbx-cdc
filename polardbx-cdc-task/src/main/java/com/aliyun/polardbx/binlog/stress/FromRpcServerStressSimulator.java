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
package com.aliyun.polardbx.binlog.stress;

import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnEnd;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnTag;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.rpc.TxnMessageProvider;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class FromRpcServerStressSimulator {

    private static final int EVENT_SIZE = 1024;//单位：byte
    private static final int BATCH_SIZE = 9;//每个DumpReply包含的事务个数，SEND_MODE为2时有效
    private static final int SEND_MODE = 2;// 0-no whole;1-whole & single;2-whole & batch
    private static final int EVENT_COUNT_PER_TXN = 8;
    private static final byte[] FORMAT_DESC_DATA = new byte[] {
        -103, -107, 113, 95, 15, 1, 0, 0, 0, -71, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 4, 0, 53, 46, 54, 46, 50, 57, 45, 84, 68, 68, 76, 45, 53,
        46, 52, 46, 54, 45, 83, 78, 65, 80, 83, 72, 79, 84, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 19, 56, 13, 0, 8, 0, 18, 0, 4, 4, 4, 4, 18, 0, 0, -95,
        0, 4, 26, 8, 0, 0, 0, 8, 8, 8, 2, 0, 0, 0, 10, 10, 10, 42, 42,
        0, 18, 52, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 10, 0, 0, 0, 0, 1, 60, -27, 100, 74};

    public static void main(String[] args) throws IOException, InterruptedException {
        TxnStreamRpcServer rpcServer = new TxnStreamRpcServer(9999, new TxnMessageProvider() {

            @Override
            public boolean checkTSO(String startTSO, TxnOutputStream outputStream, boolean keepWaiting) {
                return true;
            }

            @Override
            public void dump(String startTSO, TxnOutputStream outputStream) {

                sendFormatDesc(outputStream, TxnMergedToken.newBuilder()
                    .setTso("0000000000000000000.01601362878_000000_178094002")
                    .setType(TxnType.FORMAT_DESC)
                    .setPayload(ByteString.copyFrom(FORMAT_DESC_DATA))
                    .build());

                while (true) {
                    //背压控制
                    if (!outputStream.isReady()) {
                        LockSupport.parkNanos(1000);
                        continue;
                    }

                    //构造batchSize个事务
                    List<TxnMergedToken> tokens = Lists.newArrayListWithCapacity(BATCH_SIZE);
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        tokens.add(buildToken());
                    }

                    //发送
                    send(outputStream, tokens, null);
                }
            }

            @Override
            public void restart(String startTSO) {

            }
        });
        rpcServer.start();
        rpcServer.blockUntilShutdown();
    }

    private static void sendFormatDesc(TxnOutputStream outputStream, TxnMergedToken formatDescToken) {
        TxnTag txnTag = TxnTag.newBuilder().setTxnMergedToken(formatDescToken).build();
        TxnMessage txnMessage = TxnMessage.newBuilder().setType(MessageType.TAG).setTxnTag(txnTag).build();
        outputStream.onNext(DumpReply.newBuilder().addTxnMessage(txnMessage).build());
    }

    private static void send(TxnOutputStream outputStream, List<TxnMergedToken> tokens, List<TxnItem> items) {
        List<TxnMessage> messages = new ArrayList<>();
        for (TxnMergedToken token : tokens) {
            TxnBegin begin = TxnBegin.newBuilder().setTxnMergedToken(token).build();
            TxnData data = TxnData.newBuilder().addAllTxnItems(items == null ? buildTxnItems() : items).build();
            TxnEnd end = TxnEnd.newBuilder().build();

            if (SEND_MODE == 0) {
                outputStream.onNext(DumpReply.newBuilder()
                    .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.BEGIN).setTxnBegin(begin).build())
                    .build());
                outputStream.onNext(DumpReply.newBuilder()
                    .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(data).build())
                    .build());
                outputStream.onNext(DumpReply.newBuilder()
                    .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.END).setTxnEnd(end).build())
                    .build());
            } else if (SEND_MODE == 1) {
                TxnMessage message = TxnMessage.newBuilder()
                    .setType(MessageType.WHOLE)
                    .setTxnBegin(begin)
                    .setTxnData(data)
                    .setTxnEnd(end).build();
                outputStream.onNext(DumpReply.newBuilder().addTxnMessage(message).build());
            } else if (SEND_MODE == 2) {
                messages.add(TxnMessage.newBuilder()
                    .setType(MessageType.WHOLE)
                    .setTxnBegin(begin)
                    .setTxnData(data)
                    .setTxnEnd(end)
                    .build());
            } else {
                throw new PolardbxException("invalid send mode.");
            }
        }

        if (SEND_MODE == 2) {
            outputStream.onNext(DumpReply.newBuilder().addAllTxnMessage(messages).build());
        }
    }

    private static List<TxnItem> buildTxnItems() {
        List<TxnItem> result = new ArrayList<>();
        for (int i = 0; i < EVENT_COUNT_PER_TXN / 2; i++) {
            result.add(TxnItem.newBuilder()
                .setEventType(LogEvent.TABLE_MAP_EVENT)
                .setPayload(ByteString.copyFrom(new byte[EVENT_SIZE]))
                .build());
            result.add(TxnItem.newBuilder()
                .setEventType(LogEvent.WRITE_ROWS_EVENT)
                .setPayload(ByteString.copyFrom(new byte[EVENT_SIZE]))
                .build());
        }
        return result;
    }

    private static String generateTso() {
        long localTso = CommonUtils.nextLocalTso();
        long xid = CommonUtils.randomXid();
        return CommonUtils.generateTSO(localTso, String.valueOf(xid), "111111");
    }

    private static TxnMergedToken buildToken() {
        return TxnMergedToken.newBuilder()
            .setSchema("test")
            .setTso(generateTso())
            .setType(TxnType.DML)
            .build();
    }
}
