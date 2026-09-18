/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.enums.CompressionType;
import com.aliyun.polardbx.binlog.enums.TransactionPayloadFiled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.zip.CRC32;

import static com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil.getTableIdLength;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class EventGenerator {
    public static final int BEGIN_EVENT_LENGTH = 42;
    public static final int COMMIT_EVENT_LENGTH = 31;
    public static final int ROWS_QUERY_FIXED_LENGTH = 24;

    /*
     * Event header offsets;
     * these point to places inside the fixed header.
     */
    public static final int TIMESTAMP_OFFSET = 0;
    public static final int EVENT_TYPE_OFFSET = 4;
    public static final int SERVER_ID_OFFSET = 5;
    public static final int EVENT_LEN_OFFSET = 9;
    public static final int LOG_POS_OFFSET = 13;
    public static final int FLAGS_OFFSET = 17;

    public static final int TIMESTAMP_LEN = 4;
    public static final int EVENT_TYPE_LEN = 1;
    public static final int SERVER_ID_LEN = 4;
    public static final int EVENT_LEN_LEN = 4;
    public static final int LOG_POS_LEN = 4;
    public static final int FLAG_LEN = 2;

    public static final int LOG_EVENT_HEADER_LEN = 19;
    public static final int BINLOG_CHECKSUM_LEN = 4;
    public static final int ROTATE_HEADER_LEN = 8;
    /**
     * common header length + post header length 注意这个长度是不包括checksum且不准确的需要进一步计算
     */
    public static final int TRANSACTION_BINLOG_HEADER_FIXED_LEN = 26;

    private static final ThreadLocal<byte[]> BYTES = ThreadLocal.withInitial(() -> new byte[1024]);
    private static final byte[] BEGIN_BYTES = "BEGIN".getBytes();

    public static Pair<byte[], Integer> makeBegin(long timestamp, long serverId, long nextPos) {
        return makeBegin(timestamp, serverId, nextPos, BYTES.get(), 0, true);
    }

    /**
     * @return byteArray写完post header之后的位置
     */
    public static int makeTransactionPayloadPostHeader(ByteArray transactionPayload, int startPos,
                                                       CompressionType type,
                                                       long compressionSize,
                                                       long uncompressedSize) {
        /// Following comments are from mysql:
        /// There are four fields: "compression type", "payload size",
        /// "uncompressed size", and "end mark".  Each of the three first
        /// fields is stored as a triple, where:
        /// - the first element is a type code,
        /// - the second element is a number containing the length of the
        ///   third element, and
        /// - the third element is the value.
        /// The last field, "end mark", is stored as only a type code.  All
        /// elements are stored in the "net_store_length" format.
        /// net_store_length stores 64 bits numbers in a variable length
        /// format, using 1 to 9 bytes depending on the magnitude of the
        /// value; 1 for values up to 250, longer for bigger values.
        ///
        /// So:
        /// - The first element in each triple is always length 1 since type
        ///   codes are small;
        /// - the second element in each triple is always length 1 since the
        ///   third field is at most 9 bytes;
        /// - the third field in each triple is:
        ///   - at most 1 for the "compression type" since type codes are small;
        ///   - at most 9 for the "payload size" and "uncompressed size".
        /// - the end mark is always 1 byte since it is a constant value
        ///   less than 250
        // transaction payload compression type <type, length, value>
        transactionPayload.setPos(startPos);
        int length = ByteArray.netLengthSize(type.getValue());
        transactionPayload.writeLong(TransactionPayloadFiled.OTW_PAYLOAD_COMPRESSION_TYPE_FIELD.getValue(), 1);
        transactionPayload.writeLong(length, 1);
        transactionPayload.writeLongNetStore(type.getValue(), length);

        // transaction payload uncompressed size <type, length, value>
        transactionPayload.writeLong(TransactionPayloadFiled.OTW_PAYLOAD_UNCOMPRESSED_SIZE_FIELD.getValue(), 1);
        length = ByteArray.netLengthSize(uncompressedSize);
        transactionPayload.writeLong(length, 1);
        transactionPayload.writeLongNetStore(uncompressedSize, length);

        // transaction payload uncompressed size <type, length, value>
        transactionPayload.writeLong(TransactionPayloadFiled.OTW_PAYLOAD_SIZE_FIELD.getValue(), 1);
        length = ByteArray.netLengthSize(compressionSize);
        transactionPayload.writeLong(length, 1);
        transactionPayload.writeLongNetStore(compressionSize, length);

        // transaction payload end mask <type, length, value>
        transactionPayload.writeLong(TransactionPayloadFiled.OTW_PAYLOAD_HEADER_END_MARK.getValue(), 1);

        return transactionPayload.getPos();
    }

    public static Pair<byte[], Integer> makeTransactionPayload(long timestamp, long serverId, long nextPos, byte[] data,
                                                               byte[] payload, CompressionType type,
                                                               long compressionSize,
                                                               long uncompressedSize) {
        ByteArray transactionPayload = new ByteArray(data, 0);
        // write common header
        // 0:4 timestamp
        transactionPayload.writeLong(timestamp, 4);
        // 4:1 type_code
        transactionPayload.write((byte) LogEvent.TRANSACTION_PAYLOAD_EVENT);
        // 5:4 server_id
        transactionPayload.writeLong(serverId, 4);
        // 9:4 event size (header+data)
        transactionPayload.skip(4);
        // 13:4 next event pos
        transactionPayload.writeLong(nextPos, 4);
        // 17:2 flags
        transactionPayload.writeLong(0, 2);

        makeTransactionPayloadPostHeader(transactionPayload, transactionPayload.getPos(), type, compressionSize,
            uncompressedSize);

        // payload
        transactionPayload.write(payload);
        // crc
        transactionPayload.writeLong(0, 4);

        // rewrite size
        int eventSize = transactionPayload.getPos();
        transactionPayload.reset();
        transactionPayload.skip(EVENT_LEN_OFFSET);
        transactionPayload.writeLong(eventSize, 4);

        return Pair.of(data, eventSize);
    }

    /**
     * 高频使用，为了性能，复用byte数组
     * 后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
     */
    public static Pair<byte[], Integer> makeBegin(long timestamp, long serverId, long nextPos, byte[] data,
                                                  int offset, boolean useCRC) {
        ByteArray byteArray = new ByteArray(data, offset);
        return makeBegin(timestamp, serverId, nextPos, byteArray, offset, useCRC);
    }

    public static Pair<byte[], Integer> makeBegin(long timestamp, long serverId, long nextPos, ByteArray byteArray,
                                                  int offset, boolean useCRC) {
        byteArray.setPos(offset);
        // write query event header
        // write timestamp
        byteArray.writeLong(timestamp, 4);
        // write event type
        byteArray.write((byte) LogEvent.QUERY_EVENT);
        // write serverId
        byteArray.writeLong(serverId, 4);
        // we don't know the size now
        byteArray.skip(4);
        // 这里的nextPos字段并不准确，因为有可能有事务压缩从而将改值变小
        byteArray.writeLong(nextPos, 4);
        // LOG_EVENT_SUPPRESS_USE_F event doesn't need default database to be updated (CREATE DATABASE, ...)
        byteArray.writeLong(8, 2);

        // write query event body
        // slave_proxy_id is not needed
        byteArray.writeLong(0, 4);
        // execution time is not needed
        byteArray.writeLong(0, 4);
        // schema length
        byteArray.write((byte) 0);
        // error-code is not needed
        byteArray.writeLong(0, 2);
        // status-vars is not needed
        byteArray.writeLong(0, 2);
        byteArray.writeString("");
        byteArray.write((byte) 0);
        byteArray.writeString(BEGIN_BYTES);
        if (useCRC) {
            // crc32  checksum
            byteArray.writeLong(0, 4);
        }

        // rewrite size, log pos
        int length = byteArray.getPos() - offset;
        byteArray.setPos(offset + EVENT_LEN_OFFSET);
        // event size
        byteArray.writeLong(length, 4);
        return Pair.of(byteArray.getData(), length);
    }

    public static Pair<byte[], Integer> makeCommit(long timestamp, long serverId, long xid, long nextPos,
                                                   boolean useChecksum) {
        return makeCommit(timestamp, serverId, xid, nextPos, BYTES.get(), 0, useChecksum);
    }

    //高频使用，为了性能，复用byte数组
    //后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
    public static Pair<byte[], Integer> makeCommit(long timestamp, long serverId, long xid, long nextPos, byte[] data,
                                                   int offset, boolean useChecksum) {
        ByteArray commit = new ByteArray(data, offset);
        return makeCommit(timestamp, serverId, xid, nextPos, commit, offset, useChecksum);
    }

    public static Pair<byte[], Integer> makeCommit(long timestamp, long serverId, long xid, long nextPos,
                                                   ByteArray byteArray,
                                                   int offset, boolean useChecksum) {
        byteArray.setPos(offset);
        //write xid event header
        byteArray.writeLong(timestamp, 4);
        byteArray.write((byte) LogEvent.XID_EVENT);
        // write serverId
        byteArray.writeLong(serverId, 4);
        // we don't know the size now
        byteArray.skip(4);
        byteArray.writeLong(nextPos, 4);
        byteArray.writeLong(0, 2);

        //write xid event body
        byteArray.writeLong(xid, 8);
        if (useChecksum) {
            // crc32 checksum
            byteArray.writeLong(0, 4);
        }

        //rewrite size, log pos
        int length = byteArray.getPos() - offset;
        byteArray.setPos(offset + EVENT_LEN_OFFSET);
        byteArray.writeLong(length, 4);
        return Pair.of(byteArray.getData(), length);
    }

    public static Pair<byte[], Integer> makeRotate(long timestamp, String fileName, long nextPos, long serverId) {
        byte[] data = new byte[128];
        ByteArray rotateEvent = new ByteArray(data);

        // write rotate event header
        rotateEvent.writeLong(timestamp, 4);
        rotateEvent.write((byte) LogEvent.ROTATE_EVENT);
        rotateEvent.writeLong(serverId, 4);// write serverId
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

    /**
     * This event does not appear in the binary log.
     * It's only sent over the network by a master to a slave server to let it know that the master is still alive,
     * and is only sent when the master has no binlog events to send to slave servers.
     * <p>
     * Header:
     * - Timestamp [4]
     * - Event Type [1]
     * - Server_id [4]
     * - Event Size [4]
     * - Next_pos [4]
     * - Flags [2]
     * Content, string<EOF>
     */
    public static byte[] makeHeartBeat(String logFileName, long logPos, boolean eventChecksumOn, long serverId) {
        // TODO: dir name length 处理
        // const char* filename= m_linfo.log_file_name;
        // const char* p= filename + dirname_length(filename);
        // size_t ident_len= strlen(p);

        int eventLen = logFileName.length() + LOG_EVENT_HEADER_LEN + (eventChecksumOn ? BINLOG_CHECKSUM_LEN : 0);
        ByteArray heartbeatEvent = new ByteArray(new byte[eventLen]);
        /* Timestamp field */
        heartbeatEvent.writeLong(TIMESTAMP_OFFSET, 0, TIMESTAMP_LEN);
        heartbeatEvent.writeByte(EVENT_TYPE_OFFSET, (byte) LogEvent.HEARTBEAT_LOG_EVENT);
        heartbeatEvent.writeLong(SERVER_ID_OFFSET, serverId, SERVER_ID_LEN);
        heartbeatEvent.writeLong(EVENT_LEN_OFFSET, eventLen, EVENT_LEN_LEN);
        heartbeatEvent.writeLong(LOG_POS_OFFSET, logPos, LOG_POS_LEN);
        heartbeatEvent.writeLong(FLAGS_OFFSET, 0, FLAG_LEN);
        heartbeatEvent.writeString(LOG_EVENT_HEADER_LEN, logFileName);

        if (eventChecksumOn) {
            CRC32 crc32 = new CRC32();
            crc32.update(heartbeatEvent.getData(), 0, eventLen - LogEvent.BINLOG_CHECKSUM_LEN);
            heartbeatEvent.writeLong(eventLen - LogEvent.BINLOG_CHECKSUM_LEN, crc32.getValue(), BINLOG_CHECKSUM_LEN);
        }

        return heartbeatEvent.getData();
    }

    /**
     * Faked rotate event is only required in a few cases.
     * But even so, a faked rotate event is always sent before sending event log file,
     * even if a rotate log event exists in last binlog and was already sent.
     * The slave then gets an extra rotation and records two Rotate_log_events.
     * The main issue here are some dependencies on mysqlbinlog, that should be solved in the future.
     * <p>
     * Header:
     * - Timestamp[4] set to 0
     * - Event Type[1] set to ROTATE_EVENT
     * - Next_Pos[4] set to 0
     * - Flags[2] set to LOG_ARTIFICIAL_F (0x20)
     * Content:
     * - pos[8]: the requested pos from slave, usually 4
     * - filename: the master binlog filename
     * If it is the first fake rotate event and global server variable @@binlog_checksum was set to CRC32:
     * - crc32_checksum (4 Bytes)
     */
    public static byte[] makeFakeRotate(String nextLogFile, long logPos, boolean eventChecksumOn, long serverId) {
        // TODO: dir name length 处理
        // const char* filename= m_linfo.log_file_name;
        // const char* p= filename + dirname_length(filename);
        // size_t ident_len= strlen(p);

        int eventLen = nextLogFile.length() + LOG_EVENT_HEADER_LEN + ROTATE_HEADER_LEN +
            (eventChecksumOn ? BINLOG_CHECKSUM_LEN : 0);
        ByteArray fakeRotateEvent = new ByteArray(new byte[eventLen]);
        // 'when' (the timestamp) is set to 0 so that slave could distinguish between
        // real and fake Rotate events (if necessary)
        fakeRotateEvent.writeLong(TIMESTAMP_OFFSET, 0, TIMESTAMP_LEN);
        fakeRotateEvent.writeByte(EVENT_TYPE_OFFSET, (byte) LogEvent.ROTATE_EVENT);
        fakeRotateEvent.writeLong(SERVER_ID_OFFSET, serverId, SERVER_ID_LEN);
        fakeRotateEvent.writeLong(EVENT_LEN_OFFSET, eventLen, EVENT_LEN_LEN);
        fakeRotateEvent.writeLong(LOG_POS_OFFSET, 0, LOG_POS_LEN);
        fakeRotateEvent.writeLong(FLAGS_OFFSET, 0x0020, FLAG_LEN); // LOG_EVENT_ARTIFICIAL_F
        fakeRotateEvent.writeLong(LOG_EVENT_HEADER_LEN, logPos, ROTATE_HEADER_LEN);
        fakeRotateEvent.writeString(LOG_EVENT_HEADER_LEN + ROTATE_HEADER_LEN, nextLogFile);

        if (eventChecksumOn) {
            EventGenerator.updateChecksum(fakeRotateEvent.getData(), 0, eventLen);
        }

        return fakeRotateEvent.getData();
    }

    public static Pair<byte[], Integer> makeFakeRotate(long timestamp, String fileName, long position,
                                                       boolean withCheckSum, long serverId) {
        if (log.isDebugEnabled()) {
            log.debug("makeRotate {} {}", fileName, position);
        }
        byte[] data = new byte[128];
        ByteArray rotateEvent = new ByteArray(data);

        // write rotate event header
        rotateEvent.writeLong(timestamp, 4);
        rotateEvent.write((byte) LogEvent.ROTATE_EVENT);
        rotateEvent.writeLong(serverId, 4);// write serverId
        rotateEvent.skip(4);// we don't know the size now
        rotateEvent.skip(4);// we don't know the log pos now
        rotateEvent.writeLong(0x0020, 2);// 0x0020 LOG_EVENT_ARTIFICIAL_F

        // write rotate event body
        rotateEvent.writeLong(position, 8);// The position of the first event in the next log file
        rotateEvent.writeString(fileName);
        if (withCheckSum) {
            rotateEvent.writeLong(0, 4);// crc32 checksum holder
        }
        // rewrite size, log pos
        int length = rotateEvent.getPos();
        rotateEvent.reset();
        rotateEvent.skip(EVENT_LEN_OFFSET);
        rotateEvent.writeLong(length, 4);
        if (withCheckSum) {
            EventGenerator.updateChecksum(data, 0, length);
        }
        return Pair.of(data, length);
    }

    public static Pair<byte[], Integer> makeRowsQuery(long timestamp, long serverId, String rowsQuery, long nextPos,
                                                      byte[] data, int offset, boolean useChecksum) {
        return makeMarkEvent(timestamp, serverId, rowsQuery, nextPos, data, offset, useChecksum);
    }

    public static Pair<byte[], Integer> makeRowsQuery(long timestamp, long serverId, String rowsQuery, long nextPos,
                                                      boolean useChecksum) {
        return makeMarkEvent(timestamp, serverId, rowsQuery, nextPos, useChecksum);
    }

    public static Pair<byte[], Integer> makeMarkEvent(long timestamp, long serverId, String markContent, long nextPos,
                                                      boolean useChecksum) {
        return makeMarkEvent(timestamp, serverId, markContent, nextPos, BYTES.get(), 0, useChecksum);
    }

    /**
     * 高频使用，为了性能，复用byte数组
     * 后期维护时：一要注意线程安全；二要注意每次调用时，中间位置不要遗留上次的脏数据
     */
    public static Pair<byte[], Integer> makeMarkEvent(long timestamp, long serverId, String markContent, long nextPos,
                                                      byte[] data, int offset, boolean useChecksum) {
        ByteArray byteArray = new ByteArray(data, offset);
        return makeMarkEvent(timestamp, serverId, markContent, nextPos, byteArray, offset, useChecksum);
    }

    public static Pair<byte[], Integer> makeMarkEvent(long timestamp, long serverId, String markContent, long nextPos,
                                                      ByteArray byteArray, int offset, boolean useChecksum) {
        byteArray.setPos(offset);
        // write tso event header
        // write timestamp
        byteArray.writeLong(timestamp, 4);
        // write event type
        byteArray.write((byte) LogEvent.ROWS_QUERY_LOG_EVENT);
        // write serverId
        byteArray.writeLong(serverId, 4);
        // we don't know the size now
        byteArray.skip(4);
        byteArray.writeLong(nextPos, 4);
        byteArray.writeLong(0, 2);

        //write tso event body
        byteArray.write((byte) 1);
        // content
        byteArray.writeString(markContent);
        if (useChecksum) {
            //crc32  checksum
            byteArray.writeLong(0, 4);
        }

        // rewrite size, log pos
        int length = byteArray.getPos() - offset;
        byteArray.setPos(offset + EVENT_LEN_OFFSET);
        // write event size
        byteArray.writeLong(length, 4);
        return Pair.of(byteArray.getData(), length);
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

    public static void updatePos(byte[] data, int offset, long newPos) {
        if (log.isDebugEnabled()) {
            log.debug("updatePos {}", newPos);
        }

        // 不管是从源端传过来的event，还是dumper自己生成的event，统一在此处修改一下next position
        ByteArray byteArray = new ByteArray(data, offset);
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

    public static long readTableId(byte[] data) {
        int length = getTableIdLength();
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(19);
        return byteArray.readLong(length);
    }


    public static void updateServerId(byte[] data, long serverId) {
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(5);
        byteArray.writeLong(serverId, 4);
    }

    public static void updateEventSize(byte[] data, int eventSize) {
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(9);
        byteArray.writeLong(eventSize, 4);
    }

    public static void updateChecksum(byte[] data, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, offset, length - LogEvent.BINLOG_CHECKSUM_LEN);
        ByteArray byteArray = new ByteArray(data);
        byteArray.skip(offset + length - LogEvent.BINLOG_CHECKSUM_LEN);
        byteArray.writeLong(crc32.getValue(), LogEvent.BINLOG_CHECKSUM_LEN);
    }
}
