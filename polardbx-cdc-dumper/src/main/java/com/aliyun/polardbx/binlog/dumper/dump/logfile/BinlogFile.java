/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV1;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandlerV2;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.BinlogFileSeekHandler;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.SeekResult;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel.SingleEventToken;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.enums.CompressionType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.BufferUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_SERVER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.IS_LAB_ENV;
import static com.aliyun.polardbx.binlog.canal.binlog.LogBuffer.ISO_8859_1;
import static com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil.getTableIdLength;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.getTargetServerIds;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class BinlogFile {

    public static final byte[] BINLOG_FILE_HEADER = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};

    private File file;
    private File fileBak;
    @Getter
    private int fileSequence;
    private RandomAccessFile raf;
    private RandomAccessFile rafBak;
    private FileChannel fileChannel;
    private FileChannel fileChannelBak;
    private int seekBufferSize;
    private StreamMetrics metrics;
    private boolean checkServerId;
    private Set<Long> targetServerIds4Check;
    @Getter
    private long lastXid;

    private ByteBuffer writeBuffer;
    private long lastFlushTime;
    /**
     * FileChannel的position()方法频繁调用的话有严重的性能问题，所以在内存中维护一个指针
     */
    private long filePointer;

    private Long logBegin;
    private BinlogEndInfo binlogEndInfo;
    private BinlogFileSeekHandler binlogFileSeekHandler;

    public BinlogFile(File file, String mode, int writeBufferSize, int seekBufferSize, boolean useDirectByteBuffer,
                      StreamMetrics metrics, boolean fileBakEnabled) throws FileNotFoundException {
        initBinlogFile(file, mode, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics, fileBakEnabled);
    }

    public BinlogFile(File file, String mode, int writeBufferSize, int seekBufferSize, boolean useDirectByteBuffer,
                      StreamMetrics metrics) throws FileNotFoundException {
        initBinlogFile(file, mode, writeBufferSize, seekBufferSize, useDirectByteBuffer, metrics, true);
    }

    private void initBinlogFile(File file, String mode, int writeBufferSize, int seekBufferSize,
                                boolean useDirectByteBuffer, StreamMetrics metrics, boolean fileBakEnabled)
        throws FileNotFoundException {
        this.checkMode(mode);
        this.file = file;
        this.fileSequence = BinlogFileUtil.getBinlogSequence(file.getName());
        this.raf = new RandomAccessFile(file, mode);
        this.fileChannel = raf.getChannel();
        this.seekBufferSize = seekBufferSize * 1024 * 1024;
        // this.seekBufferSize = seekBufferSize;
        this.metrics = metrics;
        this.checkServerId = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_CHECK_SERVER_ID);
        this.targetServerIds4Check = getTargetServerIds();
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MEMORY_OPTIMIZE_ENABLED)) {
            binlogFileSeekHandler = new BinlogFileSeekHandlerV2();
        } else {
            binlogFileSeekHandler = new BinlogFileSeekHandlerV1();
        }
        if ("rw".equals(mode)) {
            this.writeBuffer = useDirectByteBuffer ? ByteBuffer.allocateDirect(writeBufferSize)
                : ByteBuffer.allocate(writeBufferSize);
        }

        if (DynamicApplicationConfig.getBoolean(IS_LAB_ENV) && fileBakEnabled) {
            initFileBak(mode);
        }
    }

    private void initFileBak(String mode) throws FileNotFoundException {
        String path = DynamicApplicationConfig.getString(ConfigKeys.BINLOG_DIR_PATH) + "/../lab/";

        // 创建 _bak 文件夹
        File bakDir = new File(path);
        if (!bakDir.exists()) {
            if (!bakDir.mkdirs()) {
                throw new RuntimeException("Failed to create backup directory: " + bakDir.getAbsolutePath());
            }
        }

        // 构造 file_bak 路径
        this.fileBak = new File(path + this.file.getName());

        // 检查 file_bak 是否存在，如果存在则重命名
        if (this.fileBak.exists()) {
            this.fileBak = renameFileWithSuffix(this.fileBak);
        }
        rafBak = new RandomAccessFile(fileBak, mode);
        fileChannelBak = rafBak.getChannel();
    }

    private File renameFileWithSuffix(File file) {
        String name = file.getName();
        String parentPath = file.getParent();

        int suffix = 1;
        File renamedFile;
        do {
            String newName = name + "_b" + suffix;
            renamedFile = new File(parentPath, newName);
            suffix++;
        } while (renamedFile.exists());

        return renamedFile;
    }

    public void updateXid(long xid) {
        if (xid > this.lastXid) {
            this.lastXid = xid;
        }
    }

    public void flush() throws IOException {
        if (writeBuffer == null) {
            return;
        }
        if (writeBuffer.position() > 0) {
            writeBuffer.flip();
            long size = writeBuffer.limit() - writeBuffer.position();
            while (writeBuffer.hasRemaining()) {
                fileChannel.write(writeBuffer);
            }
            if (fileChannelBak != null) {
                writeBuffer.flip();
                while (writeBuffer.hasRemaining()) {
                    fileChannelBak.write(writeBuffer);
                }
            }
            filePointer += size;
        }
        writeBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        if (metrics != null) {
            metrics.incrementTotalFlushWriteCount();
        }
    }

    public boolean hasBufferedData() {
        return writeBuffer.position() > 0;
    }

    /**
     * 上次执行flush操作的时间
     */
    public long lastFlushTime() {
        return lastFlushTime;
    }

    /**
     * 当前正在读取或者写入的文件位置
     */
    public long filePointer() {
        return filePointer;
    }

    /**
     * 文件的实际长度，一定大于等于position()
     */
    public long fileSize() throws IOException {
        return raf.length();
    }

    /**
     * 尝试对文件进行截断处理
     */
    public void tryTruncate() throws IOException {
        long filePointer = filePointer();
        long fileSize = fileSize();
        if (filePointer < fileSize) {
            truncate(filePointer);
            log.warn("truncate binlog file, file pointer {}, file size {}", filePointer, fileSize);
        }
    }

    /**
     * 对文件进行截断处理
     */
    public void truncate(long size) throws IOException {
        fileChannel.truncate(size);
    }

    /**
     * 已经写入的字节数，包含已经写入write buffer缓冲区但还未进行flush的数据
     */
    public long writePointer() {
        return filePointer + writeBuffer.position();
    }

    /**
     * 定位到文件最后的位置
     */
    public void seekLast() throws IOException {
        fileChannel.position(raf.length());
        filePointer = raf.length();
    }

    /**
     * 获取文件名，Simple Name
     */
    public String getFileName() {
        return file.getName();
    }

    /*
     * 文件头，四个字节
     */
    public void writeHeader() throws IOException {
        fileChannel.write(ByteBuffer.wrap(BINLOG_FILE_HEADER));
        if (fileChannelBak != null) {
            fileChannelBak.write(ByteBuffer.wrap(BINLOG_FILE_HEADER));
        }
        filePointer += BINLOG_FILE_HEADER.length;
    }

    /**
     * 无需更新事件信息，直接写入binlog文件
     */
    public void writeEventForSync(byte[] data, int offset, int length) throws IOException {
        assert data.length >= length;

        ByteArray array = new ByteArray(data, offset, length);
        array.skip(13);
        long thatPosition = array.readLong(4);
        long thisPosition = filePointer + writeBuffer.position() + length;
        checkPosition(thatPosition, thisPosition);

        writeInternal(data, offset, length);

        if (metrics != null) {
            metrics.incrementTotalWriteEventCount();
        }
    }

    public void writeData(byte[] data, int offset, int length) throws IOException {
        writeInternal(data, offset, length);
    }

    public void checkPosition(long thatPosition, long thisPosition) {
        if (thisPosition != thatPosition) {
            // 4个字节能表示的最大的无符号数为4294967295，当thisPosition超过这个最大值之后，会出现this和that不相等的情况
            // 因此，需要对this进行一次编码和解码，然后再进行对比
            ByteArray ba = new ByteArray(new byte[10]);
            ba.writeLong(thisPosition, 4);
            ba.reset();
            thisPosition = ba.readLong(4);

            //经过转换之后如果还不相等，则可以抛异常
            if (thisPosition != thatPosition) {
                throw new PolardbxException(
                    String.format("find mismatched position, this position is [%s], that position is [%s].",
                        thisPosition,
                        thatPosition));
            }
        }
    }

    public long writeEvent(byte[] data, int offset, SingleEventToken eventToken) throws IOException {
        updateXid(eventToken.getXid());
        long realPosition = writePointer() + eventToken.getLength();

        if (eventToken.getType() == SingleEventToken.Type.TRANSACTION_PAYLOAD) {
            CompressionStatistics.setLastCompressionFile(file.getName());
            CompressionStatistics.setLastCompressionPos(writePointer());
        }

        if (log.isDebugEnabled()) {
            log.debug("write Event{} to {}, before pos {}, tso: {}, after pos {}, lastXid: {}",
                eventToken.getType(),
                file.getName(),
                eventToken.getNextPosition(),
                eventToken.getTso(),
                realPosition, lastXid);
        }

        boolean needUpdateCheckSum = eventToken.getNextPosition() != realPosition;
        // rotate 的判断依据
        eventToken.setNextPosition(realPosition);
        writeEvent(data, offset, eventToken.getLength(), needUpdateCheckSum, eventToken.getCheckServerId());
        return realPosition;
    }

    /**
     * 更新binlog event的position信息，并写入文件
     */
    public void writeEvent(byte[] data, int offset, int length, boolean updateChecksum, boolean needCheckServerId)
        throws IOException {
        tryCheckServerId(data, offset, needCheckServerId);
        // 更新checksum, nextPos
        // modified by zm: 因为压缩后事务变小，所以必须更新nextPos
        long position = writePointer() + length;
        EventGenerator.updatePos(data, offset, position);
        if (updateChecksum) {
            EventGenerator.updateChecksum(data, offset, length);
        }

        writeInternal(data, offset, length);

        if (metrics != null) {
            metrics.incrementTotalWriteEventCount();
        }
    }

    private void tryCheckServerId(byte[] data, int offset, boolean needCheckServerId) {
        if (checkServerId && needCheckServerId && !targetServerIds4Check.isEmpty()) {
            ByteArray byteArray = new ByteArray(data, offset);
            byteArray.skip(4);
            int eventType = byteArray.readInteger(1);
            long serverId = byteArray.readLong(4);

            if (!targetServerIds4Check.contains(serverId)) {
                throw new PolardbxException(String.format("server_id %s is not in target server_id list %s, with event"
                    + " type %s .", serverId, targetServerIds4Check, eventType));
            }
        }
    }

    public FileChannel getFileChanel() {
        return fileChannel;
    }

    public void setFilePointer(long pos) {
        filePointer = pos;
    }

    public void close() throws IOException {
        flush();
        if (fileChannel != null) {
            fileChannel.close();
        }
        if (raf != null) {
            raf.close();
        }
        if (rafBak != null) {
            rafBak.close();
        }
        if (writeBuffer != null && writeBuffer.isDirect()) {
            BufferUtil.clean((MappedByteBuffer) writeBuffer);
        }
        log.info("binlog file {} successfully closed.", file.getName());
    }

    public SeekResult seekFirst() {
        long startTime = System.currentTimeMillis();
        try {
            final long fileLength = fileSize();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            Byte lastEventType = null;
            Long lastEventTimestamp = null;

            if (fileLength > 4) {
                long nextEventAbsolutePos = 4;
                int seekBufferSize = 4 * 1024;//seek first 不需要很大内存
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;
                do {
                    RandomAccessFile tempRaf = null;
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(bufSize);
                        tempRaf = new RandomAccessFile(file, "r");
                        tempRaf.getChannel().read(buffer, nextEventAbsolutePos);
                        buffer.flip();

                        if (buffer.hasRemaining() && buffer.remaining() >= 19) {
                            lastEventTimestamp = readInt32(buffer);//read timestamp
                            lastEventType = buffer.get();//read event_type
                            buffer.position(buffer.position() + 4);//skip server_id
                            long eventSize = readInt32(buffer);//read eventSizeer_id
                            nextEventAbsolutePos += eventSize;
                            if (lastEventType == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                                continue;
                            }
                        } else {
                            continue;
                        }
                    } finally {
                        try {
                            if (tempRaf != null) {
                                tempRaf.close();
                            }
                        } catch (IOException ex) {
                            log.error("close temp raf failed.", ex);
                        }
                    }
                    break;
                } while (true);

            }

            long pos = StringUtils.isBlank(lastTso) ? 0 : seekPosition;
            fileChannel.position(pos);
            filePointer = pos;
            log.info(
                "seek start tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                    + seekEventCount);

            return new SeekResult(lastTso, lastEventType, lastEventTimestamp);
        } catch (IOException e) {
            throw new PolardbxException("seek tso failed.", e);
        }

    }

    public SeekResult seekLastTso() {
        int mode = DynamicApplicationConfig.getInt(BINLOG_FILE_SEEK_LAST_TSO_MODE);
        return seekLastTso(mode);
    }

    /**
     * rows_query_event: https://dev.mysql.com/doc/internals/en/rows-query-event.html
     *
     * @return {@link SeekResult }
     */
    public SeekResult seekLastTso(int mode) {
        return binlogFileSeekHandler.seekLastTso(this, mode, seekBufferSize, 4);
    }

    public static boolean isValidTso4Recovery(String cts, int mode) {
        return mode == 0 || CommonUtils.isTsoPolicyTrans(cts);
    }

    private void writeInternal(byte[] data, int offset, int length) throws IOException {
        while (writeBuffer.remaining() < length) {
            int n = writeBuffer.remaining();
            writeBuffer.put(data, offset, n);
            offset += n;
            length -= n;
            flush();
        }
        writeBuffer.put(data, offset, length);
        if (writeBuffer.remaining() == 0) {
            flush();
        }

        if (metrics != null) {
            metrics.incrementTotalWriteBytes(length);
        }
    }

    public static long readInt32(ByteBuffer buffer) {
        return ((long) (0xff & buffer.get())) | ((long) (0xff & buffer.get()) << 8) |
            ((long) (0xff & buffer.get()) << 16) | ((long) (0xff & buffer.get()) << 24);
    }

    public static String readString(long length, ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[(int) length];
        buffer.get(bytes);
        return new String(bytes);
    }

    public static long readTableId(ByteBuffer buffer) {
        int length = getTableIdLength();
        return readLongByLength(buffer, length);
    }

    private void checkMode(String mode) {
        if (!"rw".equals(mode) && !"r".equals(mode)) {
            throw new PolardbxException("invalid mode " + mode);
        }
    }

    /**
     * Return next dynamic length string from buffer.
     */
    public static final String getString(ByteBuffer buffer) {
        return getString(buffer, ISO_8859_1);
    }

    /**
     * Return next dynamic length string from buffer.
     */
    public static final String getString(ByteBuffer byteBuffer, String charsetName) {
        final int len = (0xff & byteBuffer.get());
        try {
            byte[] bytes = new byte[len];
            byteBuffer.get(bytes);
            return new String(bytes, charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Unsupported encoding: " + charsetName, e);
        }
    }

    /**
     * Read int<lenenc> written in little-endian format.
     * Format (first-byte-based):
     * <0xfb - The first byte is the number (in the range 0-250). No additional bytes are used.
     * 0xfc - Two more bytes are used. The number is in the range 251-0xffff.
     * 0xfd - Three more bytes are used. The number is in the range 0xffff-0xffffff.
     * 0xfe - Eight more bytes are used. The number is in the range 0xffffff-0xffffffffffffffff.
     */
    public static long readLenenc(ByteBuffer buffer) {
        int b = buffer.get() & 0xff;
        if (b < 0xfb) {
            return b;
        } else if (b == 0xfb) {
            return 0;
        } else if (b == 0xfc) {
            return readLongByLength(buffer, 2);
        } else if (b == 0xfd) {
            return readLongByLength(buffer, 3);
        } else if (b == 0xfe) {
            return readLongByLength(buffer, 8);
        } else {
            assert false : b;
        }
        return b;
    }

    public static long readLongByLength(ByteBuffer buffer, int length) {
        long result = 0;
        for (int i = 0; i < length; ++i) {
            result |= (((long) (0xff & buffer.get())) << (i << 3));
        }
        return result;
    }

    public File getFile() {
        return file;
    }

    public long getLastFlushTime() {
        return lastFlushTime;
    }

    public long getLogBegin() {
        if (logBegin == null) {
            SeekResult result = seekFirst();
            logBegin = result.getLastEventTimestamp() * 1000;
        }
        return logBegin;
    }

    public BinlogEndInfo getLogEndInfo() {
        if (binlogEndInfo == null) {
            SeekResult result = seekLastTso(0);
            binlogEndInfo =
                new BinlogEndInfo(result.getLastEventTimestamp() * 1000, result.getLastTso(), result.getLastXid());
        }
        return binlogEndInfo;
    }

    /**
     * 将buffer中的transactionPayload压缩数据解压到decompressedBuffer中
     *
     * @return boolean true if the decompressed buffer is big enough to put decompressed data.
     */
    public static boolean handleTransactionPayload(ByteBuffer buffer, ByteBuffer decompressedBuffer)
        throws RuntimeException {
        int compressionStartPos = buffer.position() - 19;
        // compression type
        // skip compression filed info, see BinlogTransactionCompressorTest.java
        buffer.position(buffer.position() + 2);
        int typeValue = buffer.get();
        CompressionType type = CompressionType.fromValue(typeValue);
        // uncompressed size
        buffer.position(buffer.position() + 2);
        long uncompressedSize = readLenenc(buffer);
        // compression size
        buffer.position(buffer.position() + 2);
        long compressionSize = readLenenc(buffer);
        // skip end mask
        buffer.position(buffer.position() + 1);

        byte[] compressedData = new byte[(int) compressionSize];
        byte[] decompressedData = new byte[(int) uncompressedSize];
        buffer.get(compressedData);

        if (type == CompressionType.ZSTD) {
            Zstd.decompress(decompressedData, compressedData);
        } else if (type == CompressionType.NONE) {
            decompressedData = compressedData;
        } else {
            throw new RuntimeException("Unknown Compression Type" + type);
        }

        if (decompressedBuffer.remaining() >= uncompressedSize) {
            decompressedBuffer.put(decompressedData);
            decompressedBuffer.flip();
            // 跳过checksum
            buffer.position(buffer.position() + 4);
            return true;
        } else {
            buffer.position(compressionStartPos);
            return false;
        }
    }
}
