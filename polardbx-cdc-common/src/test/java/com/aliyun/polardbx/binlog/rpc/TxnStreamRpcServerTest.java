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
package com.aliyun.polardbx.binlog.rpc;

import com.aliyun.polardbx.binlog.base.BaseTest;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.MessageType;
import com.aliyun.polardbx.binlog.protocol.TxnBegin;
import com.aliyun.polardbx.binlog.protocol.TxnData;
import com.aliyun.polardbx.binlog.protocol.TxnItem;
import com.aliyun.polardbx.binlog.protocol.TxnMessage;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.google.protobuf.ByteString;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class TxnStreamRpcServerTest extends BaseTest {

    @Test
    @Ignore
    public void testServer() throws InterruptedException, IOException {
        TxnStreamRpcServer server = new TxnStreamRpcServer(8980, new TxnMessageProvider() {

            @Override
            public boolean checkTSO(String startTSO, TxnOutputStream outputStream, boolean keepWaiting) {
                return false;
            }

            @Override
            public void dump(String startTso, TxnOutputStream outputStream) throws InterruptedException {
                int traceIdSeed = 0;
                int tsoSeed = 0;
                for (int j = 0; j < 200000; j++) {
                    ArrayList<TxnItem> items = new ArrayList<>();

                    for (int i = 0; i < 10; i++) {
                        TxnItem item = TxnItem.newBuilder()
                            .setTraceId(String.valueOf(traceIdSeed++))
                            .setPayload(ByteString.copyFrom(new byte[10]))
                            .build();
                        items.add(item);
                    }

                    TxnToken token = TxnToken.newBuilder()
                        .setTso(String.valueOf(tsoSeed++))
                        .setTxnId(UUID.randomUUID().toString())
                        .setPartitionId("11")
                        .build();

                    try {
                        TxnBegin txnBegin = TxnBegin.newBuilder().setTxnToken(token).build();
                        outputStream.onNext(DumpReply.newBuilder()
                            .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.BEGIN).setTxnBegin(txnBegin))
                            .build());

                        TxnData txnData = TxnData.newBuilder().addAllTxnItems(items).build();
                        outputStream.onNext(DumpReply.newBuilder()
                            .addTxnMessage(TxnMessage.newBuilder().setType(MessageType.DATA).setTxnData(txnData))
                            .build());
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
                outputStream.onNext(DumpReply.newBuilder().build());
            }

            @Override
            public void restart(String startTSO) {

            }
        });
        server.start();
        server.blockUntilShutdown();
    }
}
