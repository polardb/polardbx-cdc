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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_SYNC_PACKET_SIZE;

/**
 * Created by ShuGuang
 */
@Slf4j
public class BinlogSyncReader extends BinlogDumpReader {

    private static final int PACKAGE_LENGTH_LIMIT = DynamicApplicationConfig.getInt(BINLOG_SYNC_PACKET_SIZE);
    private final EventSplitMode eventSplitMode;

    public BinlogSyncReader(LogFileManager logFileManager, String fileName, long pos, EventSplitMode eventSplitMode,
                            int maxPacketSize, int readBufferSize)
        throws IOException {
        super(logFileManager, fileName, pos, maxPacketSize, readBufferSize);
        this.eventSplitMode = eventSplitMode;
        log.info("event split mode for binlog sync is " + eventSplitMode);
    }

    public ByteString nextSyncPacks() {
        ByteString result = ByteString.EMPTY;
        for (; hasNext(); ) {
            result = result.concat(nextSyncPack());
            if (result.size() >= PACKAGE_LENGTH_LIMIT) {
                break;
            }
        }
        return result;
    }

    public ByteString nextSyncPack() {
        if (eventSplitMode == EventSplitMode.CLIENT) {
            return nextSyncPackWithClientSplit();
        } else {
            return nextSyncPackWithServerSplit();
        }
    }

    public ByteString nextSyncPackWithClientSplit() {
        try {
            if (buffer.remaining() == 0) {
                buffer.clear();
                if (fp == channel.size() && hasNext()) {
                    log.info("transfer, buffer={}, {}, {}<->{}", buffer, hasNext(), channel.position(), channel.size());
                    rotate();
                } else {
                    this.read();
                }
            }

            int length = buffer.limit() - buffer.position();
            ByteString byteString = ByteString.copyFrom(buffer);
            buffer.position(buffer.limit());
            fp += length;
            return byteString;
        } catch (Exception e) {
            log.warn("buffer parse fail with client split mode {}@{} {}", fileName, fp, buffer, e);
            throw new PolardbxException(e);
        }

    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    public ByteString nextSyncPackWithServerSplit() {
        int length = 0;// length
        try {
            if (buffer.remaining() == 0) {
                buffer.compact();
                this.read();
            }
            if (buffer.remaining() == 0 && hasNext() && fp == channel.size()) {
                log.info("transfer, buffer={}, {}, {}<->{}", buffer, hasNext(), channel.position(), channel.size());
                rotate();
            }

            int cur = buffer.position();

            if (buffer.remaining() < 13) {
                if (log.isDebugEnabled()) {
                    log.debug("buffer.remaining() < 13 cause read, buffer={}", buffer);
                }
                buffer.compact();
                cur = 0;
                this.read();
            }
            buffer.position(cur + 9);//go to length
            length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
                | ((buffer.get()) << 24);
            byte[] data = new byte[length];
            if (buffer.remaining() < length - 13) {
                //buffer不足时，通过文件直接发送当前event
                if (log.isDebugEnabled()) {
                    log.debug("buffer.remaining() < length - 13  cause read, length={},buffer={}", length, buffer);
                }
                channel.position(fp);
                channel.read(ByteBuffer.wrap(data));
                buffer.position(buffer.limit());
            } else {
                buffer.position(cur);
                buffer.get(data);
            }
            fp += length;
            ByteString bytes = ByteString.copyFrom(data);
            if (log.isDebugEnabled()) {
                log.debug("dumpPack {}@{}#{}", fileName, fp - length, fp);
            }
            return bytes;
        } catch (Exception e) {
            log.warn("buffer parse fail with server split mode {}@{} {} {}", fileName, fp, length, buffer, e);
            throw new PolardbxException(e);
        }
    }

    @Override
    public ByteString heartbeatEvent() {
        Pair<byte[], Integer> heartBeat = EventGenerator.makeHeartBeat(this.fileName, this.fp, true);
        return ByteString.copyFrom(heartBeat.getLeft(), 0, heartBeat.getRight());
    }
}
