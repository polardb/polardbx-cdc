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
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.dumper.metrics.DumpClientMetric;
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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
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
    protected final EnumBinlogChecksumAlg slaveChecksumAlg;
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
    boolean leftFiltered = false;
    long timestamp;
    private byte packetSequence = 1;
    private boolean rotateNext = true;
    private final boolean smallerByteBuffer;
    @Getter
    protected BinlogDumpDownloader dumpDownloader = null;
    protected EnumBinlogChecksumAlg eventChecksumAlg;
    private final NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
    private final DumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
    protected final boolean supportQuickDownload;
    private final boolean limitBufferEnabled =
        DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_DUMP_LIMIT_BUFFER_ENABLED);
    @Setter
    private BinlogDumpFilter binlogDumpFilter;
    private final boolean labEnvEnabled;
    /**
     * 文件身份标识（inode），用于检测文件是否被 rename/delete/recreate。
     * 参考 BinlogFileReader 中的 fileKey 方案，通过 BasicFileAttributes.fileKey() 获取。
     */
    private Object fileKey;
    /**
     * 文件状态检查开关，在链路初始化时读取配置，避免 read() 热路径中反复调用 getBoolean。
     */
    private final boolean fileStatusCheckEnabled;
    private final boolean useLegacySizeCheck;
    @Setter
    protected DumpClientMetric metric;
    protected final String clientTraceMark;

    public BinlogDumpReader(LogFileManager logFileManager, String fileName, long startPosition, int maxPacketSize,
                            int readBufferSize, EnumBinlogChecksumAlg slaveChecksumAlg, String clientTraceMark) {
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
        this.fileStatusCheckEnabled = DynamicApplicationConfig.getBoolean(
            ConfigKeys.BINLOG_DUMP_FILE_STATUS_CHECK_ENABLED);
        this.useLegacySizeCheck = DynamicApplicationConfig.getBoolean(
            ConfigKeys.BINLOG_DUMP_FILE_STATUS_USE_LEGACY_SIZE_CHECK);
        this.clientTraceMark = clientTraceMark;
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

        initCdcFile();
        if (cdcFile == null) {
            throw new PolardbxException("invalid log file:" + fileName);
        } else {
            // 先获取 fileKey 再打开 channel，避免在两者之间文件被重建导致无法检测
            initFileKey();
            channel = cdcFile.getReadChannel();
        }
    }

    protected void initCdcFile() throws Exception {
        cdcFile = logFileManager.getBinlogFileByName(fileName);
    }

    /**
     * 初始化文件身份标识（fileKey/inode），用于检测文件是否被 rename/delete/recreate。
     * 参考 BinlogFileReader 中的实现。
     */
    private void initFileKey() {
        try {
            File localFile = cdcFile.newFile();
            if (localFile.exists()) {
                BasicFileAttributes attrs = Files.readAttributes(
                    localFile.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                this.fileKey = attrs.fileKey();
            }
        } catch (Exception e) {
            log.warn("failed to init fileKey for {}", fileName, e);
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
        // 40: 压缩事件
        if ((eventType < 0 || eventType > 0x23) && eventType != 40) {
            throw new PolardbxException("invalid event type:" + eventType);
        }
        if (eventSize != endPos - startPosition) {
            throw new PolardbxException(
                "[" + clientTraceMark + "] invalid event size! next_position:" + endPos + ", cur_position:"
                    + startPosition
                    + ", event_size:" + eventSize);
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
            log.info("[{}] request binlog={}:{}, local cursor={}", clientTraceMark, fileName, startPosition,
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
                        log.info("[{}] can not sync {}:{} during last {}s", clientTraceMark, fileName, startPosition,
                            maxRetryTimes);
                        return false;
                    }
                } else {
                    // 主节点的binlog pos比请求的小，异常
                    log.info("[{}] request binlog={}:{},master cursor={}", clientTraceMark, fileName, startPosition,
                        cursor);
                    return false;
                }
            } else {
                List<NodeInfo> nodeInfoList = nodeInfoMapper.select(s -> s);
                log.info("[{}] all node in binlog_node_info:{}", clientTraceMark, nodeInfoList);
                return false;
            }
        } else {
            List<DumperInfo> dumperInfoList = dumperInfoMapper.select(s -> s);
            log.info("[{}] all dumper in binlog_dumper_info:{}", clientTraceMark, dumperInfoList);
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

    protected boolean eventChecksumOn() {
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
        if (binlogDumpFilter.isBinlogDumpFilterEnabled() && binlogDumpFilter.isIgnoreBySetFlag()) {
            // set flag方式过滤不能让下游校验
            byteArray.writeByte(eventLen - LogEvent.BINLOG_CHECKSUM_LEN - LogEvent.BINLOG_CHECKSUM_ALG_DESC_LEN,
                (byte) (LogEvent.BINLOG_CHECKSUM_ALG_OFF & 0xff));
        }
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

    int nextDumpPackLength() {
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

            DumpClientMetric.addReadBytes(data.length, metric);
            if (binlogDumpFilter.isBinlogDumpFilterEnabled()) {
                // withStatus 表明这是一个事件的开头
                if (withStatus) {
                    // event 开始位置
                    int offset = ba.getPos();
                    // 是否过滤本事件
                    if (binlogDumpFilter.filter(ba)) {
                        // 选择事件被过滤的方式
                        if (binlogDumpFilter.isIgnoreBySetFlag()) {
                            // 通过set flag过滤
                            return binlogDumpFilter.getFilteredData(data, offset);
                        } else {
                            // 通过不发送事件过滤
                            leftFiltered = true;
                            packetSequence--;
                            return ByteString.EMPTY;
                        }
                    } else {
                        leftFiltered = false;
                    }
                } else if (leftFiltered && !binlogDumpFilter.isIgnoreBySetFlag()) {
                    // 上一次没读完的大事件，且开头被跳过了，这次也跳过
                    packetSequence--;
                    return ByteString.EMPTY;
                }
            }

            // ByteString bytes = ByteString.copyFrom(data);
            ByteString bytes = UnsafeByteOperations.unsafeWrap(data);

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
            if (Thread.interrupted()) {
                throw new InterruptedException("thread is interrupted in loop read dump packets");
            }

            ByteString pack = nextDumpPack();
            if (!pack.isEmpty()) {
                result = result.concat(pack);
            }
            if (result.size() > maxPacketSize) {
                break;
            }
            int nextDumpPackLength = nextDumpPackLength();
            if (nextDumpPackLength == 0 && !result.isEmpty()) {
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

            if (fileStatusCheckEnabled && cdcFile.isLocal()) {
                if (useLegacySizeCheck) {
                    // @deprecated 旧方案：通过比较 channel.size() 与 cdcFile.size() 检测文件变化。
                    // 已废弃，原因：当 binlog 重建后 cdcFile.size()（基于 DB 中的 logSize）与实际磁盘文件大小
                    // 不一致时会误抛异常。仅作为回退方案保留，可通过设置
                    // BINLOG_DUMP_FILE_STATUS_USE_LEGACY_SIZE_CHECK=true 启用。
                    long channelSize = channel.size();
                    long cdcFileSize = cdcFile.size();
                    if (channelSize < cdcFileSize && (read = channel.read(buffer)) <= 0) {
                        String info = getUnexpectedChannelInfo(channelSize, cdcFileSize);
                        if (labEnvEnabled) {
                            LabEventManager.logEvent(LabEventType.DUMPER_FILE_STATUS_CHECK, info);
                        }
                        throw new PolardbxException(info);
                    }
                } else {
                    // 新方案：通过 fileKey（inode）检测文件是否被重建，与 BinlogFileReader 保持一致。
                    // 参见 BinlogFileReader.java L102-L113
                    read = checkAndHandleFileRebuild(read);
                }
            }
            // fileStatusCheckEnabled = false 时，跳过文件身份校验，仅保留上面的 checkFileStatus() 文件存在性检查
        }

        buffer.flip();

        if (log.isDebugEnabled()) {
            log.debug("read from {}, read={},buffer={}", fileName, read, bufferMessage(buffer));
        }
    }

    private String getUnexpectedChannelInfo(long channelSize, long cdcFileSize) {
        String cursorFile = logFileManager.getLatestFileCursor().getFileName();
        long cursorPos = logFileManager.getLatestFileCursor().getFilePosition();
        String info = String.format(
            "unexpected channel stat!! fp = %s , fileName = %s, channel size = %s, cdcFile size = %s, buffer = %s， cursor = %s:%s.",
            lastPosition, fileName, channelSize, cdcFileSize, buffer,
            cursorFile, cursorPos);
        return info;
    }

    /**
     * 通过 fileKey（inode）检测文件是否被 rename/delete/recreate。
     * 如果 fileKey 发生变化，说明文件被重建了（如 LogFileGenerator.prepare），需要重新打开 channel 并 seek。
     * 如果 fileKey 未变化，说明是正常的等待数据写入场景。
     * 参见 BinlogFileReader.java L102-L113。
     *
     * @param read 当前 channel.read() 的返回值
     * @return 重新读取后的 read 值
     */
    private int checkAndHandleFileRebuild(int read) throws IOException {
        try {
            File localFile = cdcFile.newFile();
            BasicFileAttributes attrs = Files.readAttributes(
                localFile.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Object currentFileKey = attrs.fileKey();
            if (fileKey != null && !fileKey.equals(currentFileKey)) {
                // 文件被重建了，重新打开 channel 并 seek 到之前的 position
                log.info("file {} has been rebuilt, fileKey changed from {} to {}, reopening channel at position {}",
                    fileName, fileKey, currentFileKey, channel.position());
                this.fileKey = currentFileKey;
                long position = channel.position();
                this.channel.close();
                this.channel = cdcFile.getReadChannel();
                this.channel.position(position);
            }
            // 1. fileKey 变化：文件经历重建，重新打开文件后读取后续数据
            // 2. fileKey 未变化：hasNext()为true说明文件可能有新写入，再读一次，没读到说明文件写完了，等待rotate
            read = channel.read(buffer);
        } catch (PolardbxException e) {
            throw e;
        } catch (Exception e) {
            log.warn("failed to check fileKey for {}", fileName, e);
        }
        return read;
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

    public boolean hasNextFile() {
        return hasNext();
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
            getFile();
        } else {
            // 在normal模式下不会切换用download，直接直连
            cdcFile = logFileManager.getBinlogFileByName(fileName);
        }

        // 先获取 fileKey 再打开 channel，避免在两者之间文件被重建导致无法检测
        initFileKey();
        this.channel = cdcFile.getReadChannel();
        log.info("rotate to next file {}", this.fileName);
        this.read();
    }

    protected void getFile() throws Exception {
        cdcFile = logFileManager.getLocalBinlogFileByName(fileName);
        if (cdcFile == null) {
            if (dumpDownloader == null || dumpDownloader.isFinished()) {
                initDumpDownloader(clientTraceMark);
            }
            log.info("[{}] {} does not exist in local, try get from downloader", clientTraceMark, fileName);
            cdcFile = dumpDownloader.getFile(fileName);
            log.info("[{}] {} is got from downloader", clientTraceMark, fileName);
        } else {
            log.info("[{}] {} is got from local", clientTraceMark, fileName);
        }
    }

    private boolean checkLocalFileExist() {
        CdcFile localFile = logFileManager.getLocalBinlogFileByName(fileName);
        return localFile != null;
    }

    /**
     * 检测到本地文件不存在，初始化下载器，开始下载
     */
    protected void initDumpDownloader(String trace) {
        log.info("init dump downloader...");
        dumpDownloader = BinlogDumpDownloader.buildBinlogDumpDownloader(dumpDownloader, fileName, trace);
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
            log.warn("[{}] {} close fail ", clientTraceMark, fileName, e);
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
    public void limitBuffer() throws IOException {
        BinlogCursor cursor = logFileManager.getLatestFileCursor();
        if (cursor.getFileSequence() == fileSequence) {
            if (cursor.getFilePosition() < 4) {
                // 刚刚rotate过来触发的read
                buffer.limit(buffer.position());
            } else {
                long maxBytesCanRead = cursor.getFilePosition() - channel.position();
                long maxRemaining = buffer.capacity() - buffer.position();
                if (maxRemaining > maxBytesCanRead) {
                    buffer.limit(buffer.position() + (int) maxBytesCanRead);
                }
            }
        }
    }

}
