/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.filesys.CdcFile;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import io.grpc.stub.ServerCallStreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.EVENT_LEN_OFFSET;
import static com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_UNDEF;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.EVENT_LEN_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.FLAGS_OFFSET;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.FLAG_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.LOG_POS_LEN;
import static com.aliyun.polardbx.binlog.format.utils.EventGenerator.LOG_POS_OFFSET;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.SERVER_ID;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ShuGuang
 */
@Slf4j
public class BinlogDumpReader {
    public static final int NET_HEADER_SIZE = 4;
    public static final int NET_HEADER_PACKET_LENGTH_SIZE = 3;
    public static final int RPL_PROTOCOL_STATUS_SIZE = 1;
    public static final byte RPL_PROTOCOL_STATUS_OK = (byte) 0;
    public static final byte RPL_PROTOCOL_STATUS_ERR = (byte) 0xff;
    public static final byte RPL_PROTOCOL_STATUS_INVALID = (byte) -1;
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
    private final EnumBinlogChecksumAlg slaveChecksumAlg;
    protected List<BinlogDumpRotateObserver> rotateObservers;
    // https://dev.mysql.com/doc/refman/5.7/en/replication-options-binary-log.html
    String fileName;
    int fileSequence;
    long startPosition;
    long lastPosition = 0;
    CdcFile cdcFile;
    @Setter
    BinlogFileReadChannel channel;
    ByteBuffer buffer;
    LogFileManager logFileManager;
    int left = 0;
    long timestamp;
    private byte packetSequence = 1;
    private boolean rotateNext = true;
    private final boolean smallerByteBuffer;
    @Getter
    private BinlogDumpDownloader dumpDownloader = null;
    private EnumBinlogChecksumAlg eventChecksumAlg;
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    private final boolean supportQuickDownload;
    private final boolean limitBufferEnabled =
        DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_LIMIT_BUFFER_ENABLED);
    private final boolean labEnvEnabled;

    public BinlogDumpReader(LogFileManager logFileManager, String fileName, long startPosition, int maxPacketSize,
                            int readBufferSize, EnumBinlogChecksumAlg slaveChecksumAlg) {
        if (startPosition <= 0) {
            startPosition = 4;
        }
        this.logFileManager = logFileManager;
        this.fileName = fileName;
        this.fileSequence = logFileManager.parseFileNumber(fileName);
        this.startPosition = startPosition;
        this.maxPacketSize = maxPacketSize;
        this.readBufferSize = readBufferSize;
        this.slaveChecksumAlg = slaveChecksumAlg;
        // m_event_checksum_alg should be set to the checksum algorithm in Format_description_log_event.
        // But it is used by fake_rotate_event() which will be called before reading any Format_description_log_event.
        // In that case, m_slave_checksum_alg is set as the value of m_event_checksum_alg.
        this.eventChecksumAlg = this.slaveChecksumAlg;
        this.smallerByteBuffer =
            DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_SUPPORT_SMALLER_BUFFER_SIZE);
        this.buffer = ByteBuffer.allocate(readBufferSize);
        this.rotateObservers = new ArrayList<>();
        supportQuickDownload = getBoolean(ConfigKeys.BINLOG_DUMP_DOWNLOAD_FIRST_MODE);
        this.labEnvEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV);
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

    public void init() throws Exception {
        // MySQL标准行为，这个值应该从binlog的Format_description_log_event里读
        // 但是使用远程下载模式时，在等待文件下载的过程中可能需要给下游发送心跳，等不及从Format_description_log_event里读了
        // 所以这里直接从配置文件里读这个配置
        this.eventChecksumAlg = EnumBinlogChecksumAlg.fromName(
            getString(ConfigKeys.BINLOG_DUMP_M_EVENT_CHECKSUM_ALG));

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

        cdcFile = logFileManager.getBinlogFileByName(fileName);
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
        int ret = validRequestBinlogPosition();
        if (ret == 0) {
            // 恰好是最新的binlog pos，直接返回
            return;
        } else if (ret < 0) {
            throw new PolardbxException("invalid request pos.");
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

    public int compareBinlogPos() {
        int ret = BinlogFileUtil.compareBinlogFileName(logFileManager.getLatestFileCursor().getFileName(), fileName);
        if (ret == 0) {
            ret = Long.compare(logFileManager.getLatestFileCursor().getFilePosition(), startPosition);
        }
        return ret;
    }

    public int validRequestBinlogPosition() {
        // 比较本地写入的最新位点和dump请求的位点
        int ret = compareBinlogPos();
        if (ret < 0) {
            // dump 请求了一个大于最新位点的位点
            if (!RuntimeLeaderElector.isDumperMasterOrX(logFileManager.getExecutionConfig().getRuntimeVersion(),
                logFileManager.getTaskType(), logFileManager.getTaskName())) {
                // 从节点等待主节点同步最新位点
                if (tryWaitSyncFromDumperMaster()) {
                    return 1;
                }
            }
            // 主节点位点比请求的小或者从节点没有同步到请求位点
            log.info("request binlog={}:{}, local cursor={}", fileName, startPosition,
                logFileManager.getLatestFileCursor());
            return -1;
        } else if (ret == 0) {
            // 恰巧等于最新位点
            return 0;
        } else {
            return 1;
        }
    }

    public boolean tryWaitSyncFromDumperMaster() {
        // 尝试获取dumper master的最新位点
        Optional<DumperInfo> dumperMasterInfo =
            dumperInfoMapper.selectOne(s -> s.where(DumperInfoDynamicSqlSupport.role, isEqualTo("M"))
                .and(DumperInfoDynamicSqlSupport.status, isEqualTo(0))
                .and(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(getString(ConfigKeys.CLUSTER_ID))));
        if (dumperMasterInfo.isPresent()) {
            DumperInfo dumperInfo = dumperMasterInfo.get();
            Optional<NodeInfo> nodeInfo = nodeInfoMapper.selectOne(
                s -> s.where(NodeInfoDynamicSqlSupport.ip, isEqualTo(dumperInfo.getIp())));
            if (nodeInfo.isPresent()) {
                BinlogCursor cursor =
                    JSON.parseObject(nodeInfo.get().getLatestCursor(), BinlogCursor.class);
                if (BinlogFileUtil.compareBinlogFileName(cursor.getFileName(), fileName) >= 0
                    && cursor.getFilePosition() >= startPosition) {
                    // 主节点的binlog pos比请求的大，等待
                    int maxRetryTimes = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_DUMP_WAIT_SYNC_RETRY_TIMES);
                    Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                        .retryIfResult(result -> result)
                        .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                        .withStopStrategy(StopStrategies.stopAfterAttempt(maxRetryTimes))
                        .build();
                    try {
                        retryer.call(() -> compareBinlogPos() <= 0);
                    } catch (RetryException | ExecutionException e) {
                        log.info("can not sync {}:{} during last {}s", fileName, startPosition, maxRetryTimes);
                        return false;
                    }
                } else {
                    // 主节点的binlog pos比请求的小，异常
                    log.info("request binlog={}:{},master cursor={}", fileName, startPosition, cursor);
                    return false;
                }
            } else {
                List<NodeInfo> nodeInfoList = nodeInfoMapper.select(s -> s);
                log.info("all node in binlog_node_info:{}", nodeInfoList);
                return false;
            }
        } else {
            List<DumperInfo> dumperInfoList = dumperInfoMapper.select(s -> s);
            log.info("all dumper in binlog_dumper_info:{}", dumperInfoList);
            return false;
        }
        return true;
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
        log.info("read format description event, eventLen={}", eventLen);
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
        return heartbeatEventPacket(this.fileName, this.lastPosition);
    }

    public ByteString heartbeatEventPacket(String fileName, long position) {
        byte[] heartbeatEvent = EventGenerator.makeHeartBeat(fileName, position, eventChecksumOn(),
            ServerConfigUtil.getGlobalNumberVar(SERVER_ID));
        byte[] packetHeader =
            makePacketHeader(heartbeatEvent.length, this.packetSequence++, true, RPL_PROTOCOL_STATUS_OK);
        return ByteString.copyFrom(ArrayUtils.addAll(packetHeader, heartbeatEvent));
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
                // 将有效数据挪动到开头，并将pos设为有效数据长度
                buffer.compact();
                // 读取文件到buffer，并将pos设为0
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
                // go to length
                buffer.position(cur + 9);
                eventLength = (0xff & buffer.get()) | ((0xff & buffer.get()) << 8) | ((0xff & buffer.get()) << 16)
                    | ((buffer.get()) << 24);
            }
            if (eventLength >= 0xFFFFFF) {
                log.warn("receive a big event, length: {}", eventLength);
                left = withStatus ? (eventLength - 0xFFFFFF + 1) : eventLength - 0xFFFFFF;
                eventLength = withStatus ? 0xFFFFFF - 1 : 0xFFFFFF;
            } else {
                left = 0;
            }
            if (buffer.remaining() < eventLength - 13) {
                if (log.isDebugEnabled()) {
                    log.debug("buffer.remaining() < length - 13  cause read, length={},buffer={}", eventLength, buffer);
                }
                buffer.position(cur);
                buffer.compact();
                cur = 0;
                this.read();
                if (!smallerByteBuffer) {
                    while (buffer.remaining() < eventLength) {
                        buffer.compact();
                        this.read();
                    }
                }
            }
            // > 16M 包处理 https://dev.mysql.com/doc/internals/en/sending-more-than-16mbyte.html
            // packet #n:   3 bytes length + sequence + status + [event_header + (event data - 1)]
            // packet #n+1: 3 bytes length + sequence + last byte of the event data.
            int nrp_len = withStatus ? 5 : 4;
            byte[] data = new byte[nrp_len + eventLength];
            // 相比show binlog events, payload 前面增加了以下5个byte https://mariadb.com/kb/en/3-binlog-network-stream/
            // Network Replication Protocol, 5 Bytes
            // packet size [3] = 23 00 00 => 00 00 23 => 35 (ok byte + event size)
            // pkt sequence [1] = 04
            // OK indicator [1] = 0 (OK)
            ByteArray ba = new ByteArray(data);
            ba.writeLong(withStatus ? eventLength + 1 : eventLength, 3);
            ba.write(packetSequence++);
            // Network streams are requested with COM_BINLOG_DUMP and prepend each Binlog Event with 00 OK-byte.
            if (withStatus) {
                ba.write((byte) 0x00);
            }
            buffer.position(cur);

            // 如果buffer装不下eventLength，则先把buffer里面的数据存到data中，
            // 然后继续读binlog文件到buffer，直至完整的event被塞入data
            if (smallerByteBuffer) {
                int sendSize = Math.min(buffer.remaining(), eventLength);
                buffer.get(data, nrp_len, sendSize);
                while (sendSize < eventLength) {
                    buffer.compact();
                    if (log.isDebugEnabled()) {
                        log.debug("sendSize < eventLength  cause read, length={},buffer={}", eventLength, buffer);
                    }
                    this.read();
                    int eventRemainingSize = eventLength - sendSize;
                    int writtenSize = sendSize + nrp_len;
                    int readSizeTmp = Math.min(buffer.remaining(), eventRemainingSize);
                    buffer.get(data, writtenSize, readSizeTmp);
                    sendSize += readSizeTmp;
                }
            } else {
                buffer.get(data, nrp_len, eventLength);
            }

            lastPosition += eventLength;
            // ByteString bytes = ByteString.copyFrom(data);
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

    public void read() throws IOException {
        // binlog文件开头的4个字节是魔法值，可以直接跳过
        if (channel.position() == 0) {
            lastPosition = 4;
            channel.position(4);
        }

        if (log.isDebugEnabled()) {
            log.debug("will read from {}#{}, fp={}, buffer={}", fileName, channel.position(), lastPosition,
                bufferMessage(buffer));
        }

        if (buffer.position() == buffer.capacity()) {
            // 这个buffer已满，写入不进去数据了
            buffer.flip();
            return;
        }

        // 限制读取的长度不要超过lastCursor，以免读到刚刚写入的不完整的事件
        if (limitBufferEnabled) {
            limitBuffer();
        }

        int read = channel.read(buffer);

        // the file related to current channel may be deleted/renamed/recreated
        if (read <= 0 && hasNext()) {
            // 此时cursor更新了，需要重新limit
            if (limitBufferEnabled) {
                limitBuffer();
            }
            if (!checkFileStatus()) {
                // 文件不存在
                throw new PolardbxException(
                    String.format("the dumped file %s not exists!, fp: %s", fileName, lastPosition));
            }
            if (channel.size() < cdcFile.size() && (read = channel.read(buffer)) <= 0) {
                // cursor更新了，却读不出数据
                String info = String.format(
                    "unexpected channel stat!! fp = %s , fileName = %s, channel size = %s, cdcFile size = %s, buffer = %s.",
                    lastPosition, fileName, channel.size(), cdcFile.size(), buffer);
                if (labEnvEnabled) {
                    LabEventManager.logEvent(LabEventType.DUMPER_FILE_STATUS_CHECK, info);
                }
                throw new PolardbxException(info);
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
        int ret = cursor.getFileSequence() - fileSequence;
        if (ret == 0) {
            long latestCursor = cursor.getFilePosition();
            return lastPosition < latestCursor;
        } else if (ret > 0) {
            if (cursor.getFileSequence() - fileSequence == 1) {
                return cursor.getFilePosition() > 4;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    protected void rotate() throws Exception {
        this.close();
        String preFileName = fileName;
        this.fileName = BinlogFileUtil.getNextBinlogFileName(fileName);
        logFileManager.getLogFileLockManager().readLock(fileName);
        rotateObservers.forEach(o -> o.onRotate(preFileName));
        this.fileSequence = logFileManager.parseFileNumber(fileName);
        this.startPosition = 4;

        if (supportQuickDownload) {
            log.info("try get {} from local in quick mode", fileName);
            cdcFile = logFileManager.getLocalBinlogFileByName(fileName);
            if (cdcFile == null) {
                if (dumpDownloader == null || dumpDownloader.isFinished()) {
                    initDumpDownloader();
                }
                log.info("{} does not exist in local, try get from downloader", fileName);
                cdcFile = dumpDownloader.getFile(fileName);
                log.info("{} is got from downloader", fileName);
            }
        } else {
            // 在normal模式下不会切换用download，直接直连
            cdcFile = logFileManager.getBinlogFileByName(fileName);
        }

        this.channel = cdcFile.getReadChannel();
        log.info("rotate to next file {}", this.fileName);
        this.read();
    }

    private boolean checkLocalFileExist() {
        CdcFile localFile = logFileManager.getLocalBinlogFileByName(fileName);
        return localFile != null;
    }

    /**
     * 检测到本地文件不存在，初始化下载器，开始下载
     */
    private void initDumpDownloader() {
        log.info("init dump downloader...");
        dumpDownloader = BinlogDumpDownloader.buildBinlogDumpDownloader(dumpDownloader, fileName);
        dumpDownloader.init();
        this.registerRotateObserver(dumpDownloader);
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
        } finally {
            logFileManager.getLogFileLockManager().unLockRead(fileName);
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

    public void setBinlogDumpDownloader(BinlogDumpDownloader downloader) {
        this.dumpDownloader = downloader;
    }


    public enum DumpMode {
        NORMAL,
        QUICK
    }

    /**
     * 限制读取的长度不要超过lastCursor，以免读到刚刚写入的不完整的事件
     */
    private void limitBuffer() throws IOException {
        BinlogCursor cursor = logFileManager.getLatestFileCursor();
        if (cursor.getFileSequence() == fileSequence) {
            if (cursor.getFilePosition() < 4) {
                // 刚刚rotate过来触发的read
                buffer.limit(buffer.position());
            } else {
                long maxBytesCanRead = cursor.getFilePosition() - channel.position();
                if (buffer.remaining() > maxBytesCanRead) {
                    buffer.limit(buffer.position() + (int) maxBytesCanRead);
                }
            }
        }
    }

}
