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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.EVENT_LEN_OFFSET;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_UNDEF;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.EVENT_LEN_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.FLAGS_OFFSET;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.FLAG_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.LOG_POS_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.LOG_POS_OFFSET;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.SERVER_ID;

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
    long startPosition;

    long lastPosition = 0;
    CdcFile cdcFile;
    BinlogFileReadChannel channel;
    ByteBuffer buffer;
    LogFileManager logFileManager;
    int left = 0;

    long timestamp;

    private byte packetSequence = 1;

    private boolean rotateNext = true;
    protected List<BinlogDumpRotateObserver> rotateObservers;
    private DumpMode mode = DumpMode.NORMAL;
    private BinlogDumpDownloader dumpDownloader = null;
    private EnumBinlogChecksumAlg eventChecksumAlg;
    private final EnumBinlogChecksumAlg slaveChecksumAlg;

    public static final int NET_HEADER_SIZE = 4;
    public static final int NET_HEADER_PACKET_LENGTH_SIZE = 3;
    public static final int RPL_PROTOCOL_STATUS_SIZE = 1;
    public static final byte RPL_PROTOCOL_STATUS_OK = (byte) 0;
    public static final byte RPL_PROTOCOL_STATUS_ERR = (byte) 0xff;
    public static final byte RPL_PROTOCOL_STATUS_INVALID = (byte) -1;

    public BinlogDumpReader(LogFileManager logFileManager, String fileName, long startPosition, int maxPacketSize,
                            int readBufferSize, EnumBinlogChecksumAlg slaveChecksumAlg) {
        if (startPosition <= 0) {
            startPosition = 4;
        }
        this.logFileManager = logFileManager;
        this.fileName = fileName;
        this.startPosition = startPosition;
        this.maxPacketSize = maxPacketSize;
        this.readBufferSize = readBufferSize;
        this.slaveChecksumAlg = slaveChecksumAlg;
        // m_event_checksum_alg should be set to the checksum algorithm in Format_description_log_event.
        // But it is used by fake_rotate_event() which will be called before reading any Format_description_log_event.
        // In that case, m_slave_checksum_alg is set as the value of m_event_checksum_alg.
        this.eventChecksumAlg = this.slaveChecksumAlg;
        this.buffer = ByteBuffer.allocate(readBufferSize);
        this.rotateObservers = new ArrayList<>();
    }

    public void init() throws Exception {
        // MySQL标准行为，这个值应该从binlog的Format_description_log_event里读
        // 但是使用远程下载模式时，在等待文件下载的过程中可能需要给下游发送心跳，等不及从Format_description_log_event里读了
        // 所以这里直接从配置文件里读这个配置
        this.eventChecksumAlg = EnumBinlogChecksumAlg.fromName(
            DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DUMP_M_EVENT_CHECKSUM_ALG));

        Boolean checkCheckSumAlg =
            DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_CHECK_CHECKSUM_ALG_SWITCH);
        if (checkCheckSumAlg) {
            if (this.slaveChecksumAlg == BINLOG_CHECKSUM_ALG_UNDEF && eventChecksumOn()) {
                log.error(
                    "Master is configured to log replication events with checksum, "
                        + "but will not send such events to slaves that cannot process them");
                throw new Exception(
                    "Slave can not handle replication events with the checksum that master is configured to log");
            }
        }

        if (mode == DumpMode.QUICK) {
            cdcFile = dumpDownloader.getFile(fileName);
        } else {
            cdcFile = logFileManager.getBinlogFileByName(fileName);
        }

        if (cdcFile == null) {
            throw new PolardbxException("invalid log file:" + fileName);
        } else {
            channel = cdcFile.getReadChannel();
        }
    }

    /**
     * 检查event中各个字段的正确性，这里仅是检查，没有读取event的数据
     * 所以在后续读取的时候需要重新把channel的position重置到pos上
     */
    public void valid() throws IOException {
        int ret = logFileManager.getLatestFileCursor().getFileName().compareTo(fileName);
        // dump请求的是最新的binlog文件
        if (ret == 0) {
            // 请求的位点大于binlog文件的最大位点
            if (startPosition > logFileManager.getLatestFileCursor().getFilePosition()) {
                log.info("valid fileName={}, pos={}, cursor={}", fileName, startPosition,
                    logFileManager.getLatestFileCursor());
                throw new PolardbxException("invalid log position");
            }
            if (startPosition == logFileManager.getLatestFileCursor().getFilePosition()) {
                return;
            }
        } else if (ret < 0) {
            // dump请求的是还没有生成的binlog文件
            throw new PolardbxException("invalid log file");
        }

        byte[] data = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.read(buffer, startPosition);
        buffer.flip();
        /*
          Event Structure:
          timestamp 0:4
          type_code 4:1
          server_id 5:4
          event_length 9:4
          next_position 13:4
          flags 17:2
          extra_headers 19:x-19
         */
        ByteArray ba = new ByteArray(data);
        long timestamp = ba.readLong(4);
        int eventType = ba.read();
        ba.skip(4);
        long eventSize = ba.readLong(4);
        long endPos = ba.readLong(4);

        if (timestamp < 0) {
            throw new PolardbxException("invalid event timestamp:" + timestamp);
        }
        if (eventType < 0 || eventType > 0x23) {
            throw new PolardbxException("invalid event type:" + eventType);
        }
        if (eventSize != endPos - startPosition) {
            throw new PolardbxException(
                "invalid event size! next_position:" + endPos + ", cur_position:" + startPosition + ", event_size:"
                    + eventSize);
        }
    }

    public ByteString fakeRotateEventPacket() {
        byte[] fakeRotateEvent = EventGenerator.makeFakeRotate(fileName, startPosition, eventChecksumOn(),
            ServerConfigUtil.getGlobalNumberVar(SERVER_ID));
        byte[] packetHeader =
            makePacketHeader(fakeRotateEvent.length, this.packetSequence++, true, RPL_PROTOCOL_STATUS_OK);
        return ByteString.copyFrom(ArrayUtils.addAll(packetHeader, fakeRotateEvent));
    }

    private boolean eventChecksumOn() {
        return (this.eventChecksumAlg == EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_CRC32);
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
        lastPosition += 4 + length;
        //相比show binlog events, payload 前面增加了以下5个byte
        ByteArray ba = new ByteArray(data);
        ba.writeLong(length + 1, 3);
        ba.write(packetSequence++);
        // Network streams are requested with COM_BINLOG_DUMP and prepend each Binlog Event with 00 OK-byte.
        ba.write((byte) 0);

        ba.skip(13);
        if (startPosition > 4) {
            //fake format with next position 0
            // make real format or fake format, different of next position
            ba.writeLong(0, 4);
            channel.position(startPosition);
        } else {
            ba.skip(4);
            channel.position(4 + length);
        }
        ba.writeLong(0, 2);
        EventGenerator.updateChecksum(data, 5, length);
        return ByteString.copyFrom(data);
    }

    public ByteString formatDescriptionPacket() throws IOException {
        byte[] formatDescriptionEvent = readFormatDescriptionEvent();
        byte[] packetHeader =
            makePacketHeader(formatDescriptionEvent.length, this.packetSequence++, true, RPL_PROTOCOL_STATUS_OK);
        return ByteString.copyFrom(ArrayUtils.addAll(packetHeader, formatDescriptionEvent));
    }

    private byte[] readFormatDescriptionEvent() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        channel.read(buffer, 4);
        ByteArray byteArray = new ByteArray(buffer.array());
        // TODO: error on slave does not support checksum
        int eventLen = byteArray.readInteger(EVENT_LEN_OFFSET, EVENT_LEN_LEN);
        this.lastPosition = 4 + eventLen;

        if (startPosition > 4) {
            // set log pos to 0 means this is a fake format desc event
            byteArray.writeLong(LOG_POS_OFFSET, 0, LOG_POS_LEN);
            channel.position(startPosition);
        } else {
            channel.position(lastPosition);
        }

        byteArray.writeLong(FLAGS_OFFSET, 0, FLAG_LEN);
        byte[] eventData = new byte[eventLen];
        buffer.position(0);
        buffer.get(eventData, 0, eventLen);
        // calculate checksum
        EventGenerator.updateChecksum(eventData, 0, eventLen);
        return eventData;
    }

    public ByteString heartbeatEventPacket() {
        byte[] heartbeatEvent = EventGenerator.makeHeartBeat(this.fileName, this.lastPosition, eventChecksumOn(),
            ServerConfigUtil.getGlobalNumberVar(SERVER_ID));
        byte[] packetHeader =
            makePacketHeader(heartbeatEvent.length, this.packetSequence++, true, RPL_PROTOCOL_STATUS_OK);
        return ByteString.copyFrom(ArrayUtils.addAll(packetHeader, heartbeatEvent));
    }

    /**
     * Binlog Network streams are requested with COM_BINLOG_DUMP and each Binlog Event is prepended with a status byte.
     * The data sent over network is then network protocol (4 bytes) + 1 byte status flag + <n bytes> event data.
     * 注：每个event都需要作为一个packet来发送，其前面需要加一个packet header。但是为了发送效率，可以将多个packet组合在一起一次发送出去
     * 每次发送的大packet最后需要增加一个status
     * <p>
     * Packet Header Format:
     * - packet length (3 bytes)
     * - packet sequence (1 byte)
     * Replication protocol status byte:
     * - uint<1> OK (0) or ERR (ff) or End of File, EOF, (fe)
     */
    public static byte[] makePacketHeader(int eventLen, byte packetSequence, boolean hasStatus, byte status) {
        int packetHeaderLen = NET_HEADER_SIZE + (hasStatus ? RPL_PROTOCOL_STATUS_SIZE : 0);
        int packetLen = eventLen + (hasStatus ? 1 : 0);
        ByteArray packetHeader = new ByteArray(new byte[packetHeaderLen]);
        packetHeader.writeLong(packetLen, NET_HEADER_PACKET_LENGTH_SIZE);
        packetHeader.write(packetSequence);
        if (hasStatus) {
            packetHeader.write(status);
        }
        return packetHeader.getData();
    }

    public ByteString eofEvent() {
        ByteArray ba = new ByteArray(new byte[9]);
        ba.writeLong(5, 3);
        ba.write(packetSequence++);
        ba.write((byte) 0xfe);
        ba.writeLong(0, 2);
        ba.writeLong(0x0002, 2);
        return ByteString.copyFrom(ba.getData());
    }

    public void start() throws Exception {
        final long position = channel.position();
        if (startPosition > position) {
            channel.position(startPosition);
            lastPosition = startPosition;
        } else {
            lastPosition = position;
        }

        read();
    }

    private int nextDumpPackLength() {
        if (buffer.remaining() < 13) {
            return 0;
        }
        int cur = buffer.position();
        //go to length
        buffer.position(cur + 9);
        int length = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
            | ((buffer.get()) << 24);
        buffer.position(cur);
        return length;
    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    protected ByteString nextDumpPack() throws Exception {
        int eventLength = 0;
        try {
            if (buffer.remaining() == 0) {
                buffer.compact();
                this.read();
            }
            if (buffer.remaining() == 0 && hasNext() && lastPosition == channel.size()) {
                log.info("rotate, buffer={}, {}, {}<->{}", buffer, hasNext(), channel.position(), channel.size());
                rotate();
                return fakeRotateEventPacket();
            }
            int cur = buffer.position();
            boolean withStatus = true;
            if (left > 0) {
                withStatus = false;
                eventLength = left;
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
                eventLength = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
                    | ((buffer.get()) << 24);
            }
            if (eventLength >= 0xFFFFFF) {
                left = withStatus ? (eventLength - 0xFFFFFF + 1) : eventLength - 0xFFFFFF;
                eventLength = withStatus ? 0xFFFFFF - 1 : 0xFFFFFF;
            } else {
                left = 0;
            }
            if (buffer.remaining() < eventLength - 13) {
                if (log.isDebugEnabled()) {
                    log.debug("buffer.remaining() < length - 13  cause read, length={},buffer={}", eventLength, buffer);
                }
                buffer.position(cur);//go to length
                buffer.compact();
                cur = 0;
                this.read();
                while (buffer.remaining() < eventLength) {
                    this.read();
                }
            }
            // TODO > 16M 包处理 https://dev.mysql.com/doc/internals/en/sending-more-than-16mbyte.html
            //packet #n:   3 bytes length + sequence + status + [event_header + (event data - 1)]
            //packet #n+1: 3 bytes length + sequence + last byte of the event data.
            int nrp_len = withStatus ? 5 : 4;
            byte[] data = new byte[nrp_len + eventLength];
            //相比show binlog events, payload 前面增加了以下5个byte https://mariadb.com/kb/en/3-binlog-network-stream/
            //Network Replication Protocol, 5 Bytes
            //packet size [3] = 23 00 00 => 00 00 23 => 35 (ok byte + event size)
            //pkt sequence [1] = 04
            //OK indicator [1] = 0 (OK)
            ByteArray ba = new ByteArray(data);
            ba.writeLong(withStatus ? eventLength + 1 : eventLength, 3);
            ba.write(packetSequence++);
            // Network streams are requested with COM_BINLOG_DUMP and prepend each Binlog Event with 00 OK-byte.
            if (withStatus) {
                ba.write((byte) 0x00);
            }
            buffer.position(cur);
            buffer.get(data, nrp_len, eventLength);
            lastPosition += eventLength;
            //ByteString bytes = ByteString.copyFrom(data);
            ByteString bytes = UnsafeByteOperations.unsafeWrap(data);
            if (log.isDebugEnabled()) {
                log.debug("dumpPack {}@{}#{}", fileName, lastPosition - eventLength, lastPosition);
            }
            // try parse event header timestamp
            try {
                if (withStatus && data.length > 8) {
                    timestamp = ((long) (0xff & data[5])) | ((long) (0xff & data[6]) << 8)
                        | ((long) (0xff & data[7]) << 16) | ((long) (0xff & data[8]) << 24);
                }
            } catch (Exception e) {
                log.error("dump reader parser timestamp failed", e);
            }

            return bytes;
        } catch (InterruptedException e) {
            log.info("binlog dump has been interrupted.");
            throw new InterruptedException("remote closed");
        } catch (Exception e) {
            log.warn("buffer parse fail {}@{} {} {}", fileName, lastPosition, eventLength, buffer, e);
            throw new Exception(e);
        }
    }

    /**
     * @return next dump pack
     * @see <a href="mysqlbinlog.cc">https://github.com/mysql/mysql-server/blob/8.0/client/mysqlbinlog.cc</a>
     */
    public ByteString nextDumpPacks(ServerCallStreamObserver<DumpStream> serverCallStreamObserver) throws Exception {
        ByteString result = ByteString.EMPTY;
        while (hasNext() & !serverCallStreamObserver.isCancelled()) {
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

    protected void read() throws IOException {
        // binlog文件开头的4个字节是魔法值，可以直接跳过
        if (channel.position() == 0) {
            lastPosition = 4;
            channel.position(4);
        }

        if (log.isDebugEnabled()) {
            log.debug("will read from {}#{}, fp={}, buffer={}", fileName, channel.position(), lastPosition,
                bufferMessage(buffer));
        }

        int read = channel.read(buffer);

        // the file related to current channel may be deleted/renamed/recreated
        if (read <= 0 && hasNext()) {
            if (!checkFileStatus() || (channel.size() < cdcFile.size() && (read = channel.read(buffer)) <= 0)) {
                throw new PolardbxException(String.format(
                    "unexpected channel stat!! fp = %s , fileName = %s, channel size = %s , buffer = %s.",
                    lastPosition, fileName, channel.size(), buffer));
            }
        }

        buffer.flip();

        if (log.isDebugEnabled()) {
            log.debug("read from {}, read={},buffer={}", fileName, read, bufferMessage(buffer));
        }
    }

    public boolean checkFileStatus() {
        return cdcFile.exist();
    }

    public boolean hasNext() {
        BinlogCursor cursor = logFileManager.getLatestFileCursor();
        int ret = cursor.getFileName().compareTo(fileName);
        if (ret == 0) {
            long latestCursor = cursor.getFilePosition();
            return lastPosition < latestCursor;
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

    protected void rotate() throws Exception {
        rotateObservers.forEach(BinlogDumpRotateObserver::onRotate);
        this.close();
        this.fileName = BinlogFileUtil.getNextBinlogFileName(fileName);
        this.startPosition = 4;

        if (mode == DumpMode.QUICK) {
            if (!dumpDownloader.isFinished()) {
                cdcFile = dumpDownloader.getFile(fileName);
            } else {
                switchDumpModeToNormal();
                cdcFile = logFileManager.getBinlogFileByName(fileName);
            }
        } else {
            cdcFile = logFileManager.getBinlogFileByName(fileName);
        }

        this.channel = cdcFile.getReadChannel();
        log.info("rotate to next file {}", this.fileName);
        this.read();
    }

    private String bufferMessage(ByteBuffer buffer) {
        return "[" + buffer.position() + "," + buffer.limit() + "," + buffer.capacity() + "]";
    }

    public void close() {
        try {
            buffer.clear();
            if (channel != null) {
                channel.close();
            }
        } catch (Exception e) {
            log.warn("{} close fail ", fileName, e);
        }
    }

    public void setRotateNext(boolean rotateNext) {
        this.rotateNext = rotateNext;
    }

    public void registerRotateObserver(BinlogDumpRotateObserver observer) {
        if (observer == null) {
            return;
        }
        rotateObservers.add(observer);
    }

    public void setDumpMode(DumpMode mode) {
        this.mode = mode;
    }

    public void setBinlogDumpDownloader(BinlogDumpDownloader downloader) {
        this.dumpDownloader = downloader;
    }

    public enum DumpMode {
        NORMAL,
        QUICK
    }

    private void switchDumpModeToNormal() {
        log.info("switching to normal mode...");
        dumpDownloader.close();
        this.mode = DumpMode.NORMAL;
    }

}
