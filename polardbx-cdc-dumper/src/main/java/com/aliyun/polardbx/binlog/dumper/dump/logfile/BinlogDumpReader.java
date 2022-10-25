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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.dumper.dump.util.ByteArray;
import com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by ShuGuang
 */
@Slf4j
public class BinlogDumpReader {
    final int maxPacketSize;
    /**
     * Command-Line Format	--max-binlog-size=#
     * System Variable	max_binlog_size
     * Scope	Global
     * Dynamic	Yes
     * Type	Integer
     * Default Value	1073741824
     * Minimum Value	4096
     * Maximum Value	1073741824
     */
    final int readBufferSize;

    // https://dev.mysql.com/doc/refman/5.7/en/replication-options-binary-log.html
    String fileName;
    long pos;
    long fp;
    FileInputStream inputStream;
    FileChannel channel;
    ByteBuffer buffer;
    LogFileManager logFileManager;
    int left = 0;
    private byte seq = 1;

    public BinlogDumpReader(LogFileManager logFileManager, String fileName, long pos, int maxPacketSize,
                            int readBufferSize)
        throws IOException {
        if (pos == 0) {
            pos = 4;
        }
        this.logFileManager = logFileManager;
        this.fileName = fileName;
        this.pos = pos;
        this.inputStream = new FileInputStream(logFileManager.getFullName(fileName));
        this.channel = inputStream.getChannel();
        this.maxPacketSize = maxPacketSize;
        this.readBufferSize = readBufferSize;
        this.buffer = ByteBuffer.allocate(readBufferSize);
    }

    public void valid() throws IOException {
        int ret = logFileManager.getLatestFileCursor().getFileName().compareTo(fileName);
        if (ret == 0) {
            if (pos > logFileManager.getLatestFileCursor().getFilePosition()) {
                log.info("valid fileName={}, pos={}, cursor={}", fileName, pos, logFileManager.getLatestFileCursor());
                throw new PolardbxException("invalid log position");
            }
            if (pos == logFileManager.getLatestFileCursor().getFilePosition()) {
                return;
            }
        } else if (ret < 0) {
            throw new PolardbxException("invalid log file");
        }

        byte[] data = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.read(buffer, pos);
        buffer.flip();
        ByteArray ba = new ByteArray(data);
        long timestamp = ba.readLong(4);
        int eventType = ba.read();
        ba.skip(4);
        long eventSize = ba.readLong(4);
        long endPos = ba.readLong(4);

        if (timestamp < 0) {
            throw new PolardbxException("invalid event");
        }
        if (eventType < 0 || eventType > 0x23) {
            throw new PolardbxException("invalid event type");
        }
        if (eventSize != endPos - pos) {
            throw new PolardbxException(
                "Found invalid event in binary log [" + fileName + "@" + pos + "#" + endPos + ", timestamp=" + timestamp
                    + ", eventType=" + eventType + ", eventSize=" + eventSize + "]");
        }
        channel.position(0);
    }

    public ByteString prepareFakeEvents() throws IOException {
        return fakeRotateEvent().concat(fakeFormatEvent());
    }

    public ByteString fakeRotateEvent() {
        long length = logFileManager.getLatestFileCursor().getFilePosition();
        Pair<byte[], Integer> rotateEvent = EventGenerator.makeFakeRotate(0L, fileName,
            pos < length ? pos : length, true);
        ByteArray ba = new ByteArray(new byte[5]);
        ba.writeLong(rotateEvent.getRight() + 1, 3);
        ba.write(seq++);
        ByteString rotateEventStr = ByteString.copyFrom(rotateEvent.getLeft(), 0,
            rotateEvent.getRight());
        ByteString rotateEventPack = ByteString.copyFrom(
            ArrayUtils.addAll(ba.getData(), rotateEventStr.toByteArray()));
        return rotateEventPack;
    }

    public ByteString fakeFormatEvent() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        channel.read(buffer, 4);
        buffer.flip();
        buffer.mark();
        buffer.position(9);//go to length
        int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
            | ((buffer.get()) << 24);
        byte[] data = new byte[5 + length];
        buffer.reset();
        buffer.get(data, 5, length);
        fp += 4 + length;
        //相比show binlog events, payload 前面增加了以下5个byte
        ByteArray ba = new ByteArray(data);
        ba.writeLong(length + 1, 3);
        ba.write(seq++);
        // Network streams are requested with COM_BINLOG_DUMP and prepend each Binlog Event with 00 OK-byte.
        ba.write((byte) 0);

        ba.skip(13);
        if (pos > 4) {
            //fake format with next position 0
            // make real format or fake format, different of next position
            ba.writeLong(0, 4);
            channel.position(pos);
        } else {
            ba.skip(4);
            channel.position(4 + length);
        }
        ba.writeLong(0, 2);
        EventGenerator.updateChecksum(data, 5, length);
        ByteString bytes = ByteString.copyFrom(data);
        return bytes;
    }

    public ByteString heartbeatEvent() {
        Pair<byte[], Integer> heartBeat = EventGenerator.makeHeartBeat(this.fileName, this.fp, true);
        ByteArray ba = new ByteArray(new byte[5]);
        ba.writeLong(heartBeat.getRight() + 1, 3);
        ba.write(seq++);
        ByteString heartbeat = ByteString.copyFrom(heartBeat.getLeft(), 0,
            heartBeat.getRight());
        ByteString rotateEventPack = ByteString.copyFrom(
            ArrayUtils.addAll(ba.getData(), heartbeat.toByteArray()));
        return rotateEventPack;
    }

    public void start() throws IOException {
        final long position = channel.position();
        if (pos > position) {
            channel.position(pos);
            fp = pos;
        } else {
            fp = position;
        }
        read();
    }

    public int nextDumpPackLength() {
        if (buffer.remaining() < 13) {
            return 0;
        }
        int cur = buffer.position();
        buffer.position(cur + 9);//go to length
        int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
            | ((buffer.get()) << 24);
        buffer.position(cur);
        return length;
    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    public ByteString nextDumpPack() {
        int length = 0;// length
        try {
            if (buffer.remaining() == 0) {
                buffer.compact();
                this.read();
            }
            if (buffer.remaining() == 0 && hasNext() && fp == channel.size()) {
                log.info("transfer, buffer={}, {}, {}<->{}", buffer, hasNext(), channel.position(), channel.size());
                transfer();
                return fakeRotateEvent();
            }
            int cur = buffer.position();
            boolean withStatus = true;
            if (left > 0) {
                withStatus = false;
                length = left;
            } else {
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
            }
            if (length >= 0xFFFFFF) {
                left = withStatus ? (length - 0xFFFFFF + 1) : length - 0xFFFFFF;
                length = withStatus ? 0xFFFFFF - 1 : 0xFFFFFF;
            } else {
                left = 0;
            }
            if (buffer.remaining() < length - 13) {
                if (log.isDebugEnabled()) {
                    log.debug("buffer.remaining() < length - 13  cause read, length={},buffer={}", length, buffer);
                }
                buffer.position(cur);//go to length
                buffer.compact();
                cur = 0;
                this.read();
            }
            //2do > 16M 包处理 https://dev.mysql.com/doc/internals/en/sending-more-than-16mbyte.html
            //packet #n:   3 bytes length + sequence + status + [event_header + (event data - 1)]
            //packet #n+1: 3 bytes length + sequence + last byte of the event data.
            int nrp_len = withStatus ? 5 : 4;
            byte[] data = new byte[nrp_len + length];
            //相比show binlog events, payload 前面增加了以下5个byte https://mariadb.com/kb/en/3-binlog-network-stream/
            //Network Replication Protocol, 5 Bytes
            //packet size [3] = 23 00 00 => 00 00 23 => 35 (ok byte + event size)
            //pkt sequence [1] = 04
            //OK indicator [1] = 0 (OK)
            ByteArray ba = new ByteArray(data);
            ba.writeLong(withStatus ? length + 1 : length, 3);
            ba.write(seq++);
            // Network streams are requested with COM_BINLOG_DUMP and prepend each Binlog Event with 00 OK-byte.
            if (withStatus) {
                ba.write((byte) 0x00);
            }
            buffer.position(cur);
            buffer.get(data, nrp_len, length);
            fp += length;
            //ByteString bytes = ByteString.copyFrom(data);
            ByteString bytes = UnsafeByteOperations.unsafeWrap(data);
            if (log.isDebugEnabled()) {
                log.debug("dumpPack {}@{}#{}", fileName, fp - length, fp);
            }
            return bytes;
        } catch (Exception e) {
            log.warn("buffer parse fail {}@{} {} {}", fileName, fp, length, buffer, e);
            throw new PolardbxException();
        }
    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    public ByteString nextDumpPacks() {
        ByteString result = ByteString.EMPTY;
        while (hasNext()) {
            result = result.concat(nextDumpPack());
            if (result.size() > maxPacketSize) {
                break;
            }
            int nextDumpPackLength = nextDumpPackLength();
            if (nextDumpPackLength == 0) {
                break;
            }
            if (nextDumpPackLength + result.size() > maxPacketSize) {
                break;
            }
        }
        return result;
    }

    void read() throws IOException {
        if (channel.position() == 0) {
            fp = 4;
            channel.position(4);
        }

        if (log.isDebugEnabled()) {
            log.debug("will read from {}#{}, fp={}, buffer={}", fileName, channel.position(), fp,
                bufferMessage(buffer));
        }

        int read = channel.read(buffer);
        buffer.flip();

        if (log.isDebugEnabled()) {
            log.debug("read from {}, read={},buffer={}", fileName, read, bufferMessage(buffer));
        }
    }

    public boolean hasNext() {
        Cursor cursor = logFileManager.getLatestFileCursor();
        int ret = cursor.getFileName().compareTo(fileName);
        if (ret == 0) {
            long latestCursor = cursor.getFilePosition();
            return fp < latestCursor;
        } else if (ret > 0) {
            int seq1 = logFileManager.parseFileNumber(fileName);
            int seq2 = logFileManager.parseFileNumber(cursor.getFileName());
            if (seq2 - seq1 == 1) {
                return cursor.getFilePosition() > 4;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    void transfer() throws IOException {
        this.close();
        this.fileName = logFileManager.nextBinlogFileName(fileName);
        this.pos = 4;
        this.inputStream = new FileInputStream(logFileManager.getFullName(fileName));
        this.channel = inputStream.getChannel();
        log.info("transfer to next file {}", this.fileName);
        this.read();
    }

    private String bufferMessage(ByteBuffer buffer) {
        return "[" + buffer.position() + "," + buffer.limit() + "," + buffer.capacity() + "]";
    }

    public void close() {
        try {
            buffer.clear();
            channel.close();
            inputStream.close();
        } catch (IOException e) {
            log.warn("{} close fail ", fileName, e);
        }
    }

}
