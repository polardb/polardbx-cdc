/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by ShuGuang
 */
@Slf4j
public class BinlogSyncReader extends BinlogDumpReader {

    private static final int PACKAGE_LENGTH_LIMIT = 16 * 1024;//聚合小的event，一次发送

    public BinlogSyncReader(LogFileManager logFileManager, String fileName, long pos) throws IOException {
        super(logFileManager, fileName, pos);
    }

    public ByteString nextSyncPacks() {
        ByteString result = ByteString.EMPTY;
        for (; hasNext(); ) {
            result = result.concat(nextSyncPack());
            if (result.size() > PACKAGE_LENGTH_LIMIT) {
                break;
            }
        }
        return result;
    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    public ByteString nextSyncPack() {
        int length = 0;// length
        try {
            if (buffer.remaining() == 0) {
                buffer.compact();
                this.read();
            }
            if (buffer.remaining() == 0 && hasNext() && fp == channel.size()) {
                log.info("transfer, buffer={}, {}, {}<->{}", buffer, hasNext(), channel.position(), channel.size());
                transfer();
            }
            int cur = buffer.position();

            if (buffer.remaining() < 13) {
                log.debug("buffer.remaining() < 13 cause read, buffer={}", buffer);
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
                log.debug("buffer.remaining() < length - 13  cause read, length={},buffer={}", length, buffer);
                channel.position(fp);
                channel.read(ByteBuffer.wrap(data));
                buffer.position(buffer.limit());
            } else {
                buffer.position(cur);
                buffer.get(data);
            }
            fp += length;
            ByteString bytes = ByteString.copyFrom(data);
            log.debug("dumpPack {}@{}#{}", fileName, fp - length, fp);
            return bytes;
        } catch (Exception e) {
            log.warn("buffer parse fail {}@{} {} {}", fileName, fp, length, buffer, e);
            throw new PolardbxException();
        }
    }

    @Override
    public ByteString heartbeatEvent() {
        Pair<byte[], Integer> heartBeat = EventGenerator.makeHeartBeat(this.fileName, this.fp, true);
        return ByteString.copyFrom(heartBeat.getLeft(), 0, heartBeat.getRight());
    }
}
