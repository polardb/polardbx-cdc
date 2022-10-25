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
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.aliyun.polardbx.binlog.ServerConfigUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.getTableIdLength;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class EventGenerator {
    public static final int BEGIN_EVENT_LENGTH = 42;
    public static final int COMMIT_EVENT_LENGTH = 31;
    public static final int ROWS_QUERY_FIXED_LENGTH = 24;

    private static final int EVENT_LEN_OFFSET = 9;
    private static final ThreadLocal<byte[]> BYTES = ThreadLocal.withInitial(() -> new byte[1024]);
    private static final long SERVER_ID = ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
    private static final byte[] BEGIN_BYTES = "BEGIN".getBytes();

    public static Pair<byte[], Integer> makeMarkEvent(long timestamp, long serverId, String markContent, long nextPos) {
        return makeMarkEvent(timestamp, serverId, markContent, nextPos, BYTES.get(), 0);
    }

    //高频使用，为了性能，复用byte数组
    //后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
    public static Pair<byte[], Integer> makeMarkEvent(long timestamp, long serverId, String markContent, long nextPos,
                                                      byte[] data, int offset) {
        ByteArray tsoEvent = new ByteArray(data, offset);

        //write tso event header
        tsoEvent.writeLong(timestamp, 4);// write timestamp
        tsoEvent.write((byte) LogEvent.ROWS_QUERY_LOG_EVENT);// write event type
        tsoEvent.writeLong(serverId, 4);// write serverId
        tsoEvent.skip(4);// we don't know the size now
        tsoEvent.writeLong(nextPos, 4);// we don't know the log pos now
        tsoEvent.writeLong(0, 2);//

        //write tso event body
        tsoEvent.write((byte) 1);//
        tsoEvent.writeString(markContent);// content
        tsoEvent.writeLong(0, 4);//crc32  checksum

        // rewrite size, log pos
        int length = tsoEvent.getPos() - offset;
        tsoEvent.reset();
        tsoEvent.skip(EVENT_LEN_OFFSET);
        tsoEvent.writeLong(length, 4);// write event size
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeBegin(long timestamp, long serverId, long nextPos) {
        return makeBegin(timestamp, serverId, nextPos, BYTES.get(), 0);
    }

    //高频使用，为了性能，复用byte数组
    //后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
    public static Pair<byte[], Integer> makeBegin(long timestamp, long serverId, long nextPos, byte[] data,
                                                  int offset) {
        ByteArray begin = new ByteArray(data, offset);

        //write query event header
        begin.writeLong(timestamp, 4);// write timestamp
        begin.write((byte) LogEvent.QUERY_EVENT);// write event type
        begin.writeLong(serverId, 4);// write serverId
        begin.skip(4);//we don't know the size now
        begin.writeLong(nextPos, 4);//we don't know the log pos now
        begin.writeLong(8,
            2);//LOG_EVENT_SUPPRESS_USE_F event doesn't need default database to be updated (CREATE DATABASE, ...)

        //write query event body
        begin.writeLong(0, 4);//slave_proxy_id is not needed
        begin.writeLong(0, 4);//execution time is not needed
        begin.write((byte) 0);//schema length
        begin.writeLong(0, 2);//error-code is not needed
        begin.writeLong(0, 2);//status-vars is not needed
        begin.writeString("");
        begin.write((byte) 0);
        begin.writeString(BEGIN_BYTES);
        begin.writeLong(0, 4);//crc32  checksum

        // rewrite size, log pos
        int length = begin.getPos() - offset;
        begin.reset();
        begin.skip(EVENT_LEN_OFFSET);
        begin.writeLong(length, 4);// event size
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeCommit(long timestamp, long serverId, long xid, long nextPos) {
        return makeCommit(timestamp, serverId, xid, nextPos, BYTES.get(), 0);
    }

    //高频使用，为了性能，复用byte数组
    //后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
    public static Pair<byte[], Integer> makeCommit(long timestamp, long serverId, long xid, long nextPos, byte[] data,
                                                   int offset) {
        ByteArray commit = new ByteArray(data, offset);

        //write xid event header
        commit.writeLong(timestamp, 4);
        commit.write((byte) LogEvent.XID_EVENT);
        commit.writeLong(serverId, 4);// write serverId
        commit.skip(4);// we don't know the size now
        commit.writeLong(nextPos, 4);// we don't know the log pos now
        commit.writeLong(0, 2);//

        //write xid event body
        commit.writeLong(xid, 8);
        commit.writeLong(0, 4);// crc32 checksum

        //rewrite size, log pos
        int length = commit.getPos() - offset;
        commit.reset();
        commit.skip(EVENT_LEN_OFFSET);
        commit.writeLong(length, 4);
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeRotate(long timestamp, String fileName, long nextPos) {
        byte[] data = new byte[128];
        ByteArray rotateEvent = new ByteArray(data);

        // write rotate event header
        rotateEvent.writeLong(timestamp, 4);
        rotateEvent.write((byte) LogEvent.ROTATE_EVENT);
        rotateEvent.writeLong(SERVER_ID, 4);// write serverId
        rotateEvent.skip(4);// we don't know the size now
        rotateEvent.writeLong(nextPos, 4);// we don't know the log pos now
        rotateEvent.writeLong(0, 2);//

        // write rotate event body
        rotateEvent.writeLong(4, 8);// The position of the first event in the next log file
        rotateEvent.writeString(fileName);
        rotateEvent.writeLong(0, 4);// crc32 checksum holder

        // rewrite size, log pos
        int length = rotateEvent.getPos();
        rotateEvent.reset();
        rotateEvent.skip(EVENT_LEN_OFFSET);
        rotateEvent.writeLong(length, 4);

        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeHeartBeat(String fileName, long pos, boolean updateCheckSum) {
        byte[] data = new byte[128];
        ByteArray heartbeatEvent = new ByteArray(data);
        // write heartbeat event header
        heartbeatEvent.writeLong(0, 4);
        heartbeatEvent.write((byte) LogEvent.HEARTBEAT_LOG_EVENT);
        heartbeatEvent.writeLong(SERVER_ID, 4);// write serverId
        heartbeatEvent.skip(4);// we don't know the size now
        heartbeatEvent.writeLong(pos, 4);
        heartbeatEvent.writeLong(0, 2);//
        // write rotate event body
        byte[] bytes = fileName.getBytes();
        heartbeatEvent.write(bytes);
        heartbeatEvent.skip(4);// crc32 checksum holder
        // rewrite size, log pos
        int length = heartbeatEvent.getPos();
        heartbeatEvent.reset();
        heartbeatEvent.skip(EVENT_LEN_OFFSET);
        heartbeatEvent.writeLong(length, 4);
        if (updateCheckSum) {
            EventGenerator.updateChecksum(data, 0, length);
        }
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeFakeRotate(long timestamp, String fileName, long position,
                                                       boolean updateCheckSum) {
        if (log.isDebugEnabled()) {
            log.debug("makeRotate {} {}", fileName, position);
        }
        byte[] data = new byte[128];
        ByteArray rotateEvent = new ByteArray(data);

        // write rotate event header
        rotateEvent.writeLong(timestamp, 4);
        rotateEvent.write((byte) LogEvent.ROTATE_EVENT);
        rotateEvent.writeLong(SERVER_ID, 4);// write serverId
        rotateEvent.skip(4);// we don't know the size now
        rotateEvent.skip(4);// we don't know the log pos now
        rotateEvent.writeLong(0x0020, 2);// 0x0020 LOG_EVENT_ARTIFICIAL_F

        // write rotate event body
        rotateEvent.writeLong(position, 8);// The position of the first event in the next log file
        rotateEvent.writeString(fileName);
        rotateEvent.writeLong(0, 4);// crc32 checksum holder

        // rewrite size, log pos
        int length = rotateEvent.getPos();
        rotateEvent.reset();
        rotateEvent.skip(EVENT_LEN_OFFSET);
        rotateEvent.writeLong(length, 4);
        if (updateCheckSum) {
            EventGenerator.updateChecksum(data, 0, length);
        }
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeRowsQuery(long timestamp, long serverId, String rowsQuery, long nextPos,
                                                      byte[] data, int offset) {
        return makeMarkEvent(timestamp, serverId, rowsQuery, nextPos, data, offset);
    }

    public static Pair<byte[], Integer> makeRowsQuery(long timestamp, long serverId, String rowsQuery, long nextPos) {
        return makeMarkEvent(timestamp, serverId, rowsQuery, nextPos);
    }

    public static void updatePos(byte[] data, long newPos) {
        if (log.isDebugEnabled()) {
            log.debug("updatePos {}", newPos);
        }

        // 不管是从源端传过来的event，还是dumper自己生成的event，统一在此处修改一下next position
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(13);
        byteArray.writeLong(newPos, 4);
    }

    public static void updateTimeStamp(byte[] data, long timeStamp) {
        if (log.isDebugEnabled()) {
            log.debug("updateTimeStamp {}", timeStamp);
        }

        ByteArray byteArray = new ByteArray(data);
        byteArray.writeLong(timeStamp, 4);
    }

    public static void updateTableId(byte[] data, long tableId) {
        if (log.isDebugEnabled()) {
            log.debug("updateTableId {}", tableId);
        }

        int length = getTableIdLength();
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(19);
        byteArray.writeLong(tableId, length);
    }

    public static void updateServerId(byte[] data) {
        if (log.isDebugEnabled()) {
            log.debug("updateServerId {}", SERVER_ID);
        }

        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(5);
        byteArray.writeLong(SERVER_ID, 4);
    }

    public static void updateChecksum(byte[] data, int offset, int length) {
        if (log.isDebugEnabled()) {
            log.debug("updateChecksum {}", SERVER_ID);
        }

        CRC32 crc32 = new CRC32();
        crc32.update(data, offset, length - LogEvent.BINLOG_CHECKSUM_LEN);
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(offset + length - LogEvent.BINLOG_CHECKSUM_LEN);
        byteArray.writeLong(crc32.getValue(), LogEvent.BINLOG_CHECKSUM_LEN);
    }
}
