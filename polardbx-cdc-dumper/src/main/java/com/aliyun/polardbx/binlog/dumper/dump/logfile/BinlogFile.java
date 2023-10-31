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
import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import com.aliyun.polardbx.binlog.format.utils.EventGenerator;
import com.aliyun.polardbx.binlog.util.BufferUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_SERVER_ID;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.ROWS_QUERY_LOG_EVENT;
import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.containsTableId;
import static com.aliyun.polardbx.binlog.format.utils.generator.BinlogGenerateUtil.getTableIdLength;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.getTargetServerIds;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class BinlogFile {

    public static final byte[] BINLOG_FILE_HEADER = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};

    private final File file;
    private final RandomAccessFile raf;
    private final FileChannel fileChannel;
    private final int seekBufferSize;
    private final StreamMetrics metrics;
    private final boolean checkServerId;
    private final Set<Long> targetServerIds4Check;

    private ByteBuffer writeBuffer;
    private long lastFlushTime;
    private long filePointer;//FileChannel的position()方法频繁调用的话有严重的性能问题，所以在内存中维护一个指针

    private Long logBegin;
    private BinlogEndInfo binlogEndInfo;

    public BinlogFile(File file, String mode, int writeBufferSize, int seekBufferSize, boolean useDirectByteBuffer,
                      StreamMetrics metrics)
        throws FileNotFoundException {
        this.checkMode(mode);
        this.file = file;
        this.raf = new RandomAccessFile(file, mode);
        this.fileChannel = raf.getChannel();
        this.seekBufferSize = seekBufferSize * 1024 * 1024;
        this.metrics = metrics;
        this.checkServerId = DynamicApplicationConfig.getBoolean(BINLOG_WRITE_CHECK_SERVER_ID);
        this.targetServerIds4Check = getTargetServerIds();

        if ("rw".equals(mode)) {
            this.writeBuffer = useDirectByteBuffer ? ByteBuffer.allocateDirect(writeBufferSize)
                : ByteBuffer.allocate(writeBufferSize);
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

    /*
     * 更新binlog event的position信息，并写入文件
     */
    public void writeEvent(byte[] data, int offset, int length, boolean updateChecksum, boolean needCheckServerId)
        throws IOException {
        tryCheckServerId(data, offset, needCheckServerId);
        // 更新checksum
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

    public void close() throws IOException {
        flush();
        if (fileChannel != null) {
            fileChannel.close();
        }
        if (raf != null) {
            raf.close();
        }
        if (writeBuffer != null && writeBuffer.isDirect()) {
            BufferUtil.clean((MappedByteBuffer) writeBuffer);
        }
        log.info("binlog file successfully closed.");
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

    // rows_query_envent: https://dev.mysql.com/doc/internals/en/rows-query-event.html
    public SeekResult seekLastTso(int mode) {
        log.info("prepare to seek last tso from binlog file " + getFileName());
        long startTime = System.currentTimeMillis();
        try {
            final long fileLength = fileSize();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            byte lastEventType = -1;
            Long lastEventTimestamp = null;
            Long maxTableId = null;
            MarkInfo markInfo = null;
            boolean shouldBreak = false;

            if (fileLength > 4) {
                long nextEventAbsolutePos = 4;
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;

                while (!shouldBreak && nextEventAbsolutePos < fileLength) {
                    RandomAccessFile tempRaf = null;
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(bufSize);
                        tempRaf = new RandomAccessFile(file, "r");
                        tempRaf.getChannel().read(buffer, nextEventAbsolutePos);
                        buffer.flip();

                        //如果刚刚读取的buffer的remaining小于event header的长度，说明对文件已经读取完，直接break
                        if (buffer.hasRemaining() && buffer.remaining() < 19 &&
                            nextEventAbsolutePos + buffer.remaining() >= fileLength) {
                            break;
                        }

                        int nextEventRelativePos = buffer.position();
                        while (buffer.hasRemaining() && buffer.remaining() >= 19) {
                            lastEventTimestamp = readInt32(buffer);//read timestamp
                            lastEventType = buffer.get();//read event_type
                            if (!LogEventUtil.validEventType(lastEventType)) {
                                shouldBreak = true;
                                break;
                            }
                            buffer.position(buffer.position() + 4);//skip server_id
                            long eventSize = readInt32(buffer);//read eventSize
                            if (eventSize < 19) {
                                shouldBreak = true;
                                break;
                            }
                            // next position需要通过计算获取，不能直接用header中的log_pos字段的值
                            // 因为对于超大事务(>2G)，log_pos的四个字节已经无法准确表达下个事件的位置
                            nextEventAbsolutePos += eventSize;
                            nextEventRelativePos += eventSize;
                            markInfo = null;
                            if (nextEventRelativePos > buffer.limit()) {
                                // 如果当前这个Event是ROWS_QUERY_LOG_EVENT，则不能直接跳过，需要将nextEventAbsolutePos进行回调后再break，
                                // 但保证nextEventAbsolutePos<fileLength，否则会有死循环问题
                                if ((lastEventType == ROWS_QUERY_LOG_EVENT || containsTableId(lastEventType))
                                    && nextEventAbsolutePos < fileLength) {
                                    nextEventAbsolutePos -= eventSize;
                                }
                                break;
                            } else {
                                if (lastEventType == ROWS_QUERY_LOG_EVENT) {
                                    //跳过剩余的header
                                    buffer.position(buffer.position() + 6);
                                    //在之前的版本中，ROWS_QUERY_LOG_EVENT只用来记录tso，这个字段的值并不是1，而是tso的长度
                                    byte tsoSize = buffer.get();
                                    //eventSize减去header长度、checksum的长度和payload的第一个字节，便是query_log的字符串的长度
                                    String content = readString(eventSize - 19 - 1 - 4, buffer);
                                    //只有在历史版本中，tsoSize的值才会大于1，验证一下长度是否合法
                                    if (tsoSize > 1 && tsoSize != 54) {
                                        throw new PolardbxException("invalid tso size " + tsoSize);
                                    }
                                    //如果tsoSize等于54(历史版本，ROWS_QUERY_LOG_EVENT只用来记录tso)
                                    //或者content的前缀是CTS(ROWS_QUERY_LOG_EVENT用来记录更多元信息)
                                    //则说明该Event记录的是一个commit tso
                                    if (tsoSize == 54) {
                                        if (isValidTso4Recovery(content, mode)) {
                                            lastTso = content;
                                            seekPosition = nextEventAbsolutePos;
                                        }
                                    } else if (content.startsWith(MarkType.CTS.name())) {
                                        markInfo = new MarkInfo(content);
                                        if (isValidTso4Recovery(markInfo.getTso(), mode)) {
                                            lastTso = markInfo.getTso();
                                            seekPosition = nextEventAbsolutePos;
                                        }
                                    }
                                } else if (containsTableId(lastEventType)) {
                                    buffer.position(buffer.position() + 6);
                                    long tableId = readTableId(buffer);
                                    maxTableId = maxTableId == null ? tableId : Math.max(maxTableId, tableId);
                                }
                                buffer.position(nextEventRelativePos);
                                seekEventCount++;
                            }

                            if (nextEventAbsolutePos > fileLength) {
                                throw new PolardbxException("invalid next position {" + nextEventAbsolutePos
                                    + "}, its value can't be greater than file length {" + fileLength
                                    + "}");
                            }
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
                }
            }

            long pos = StringUtils.isBlank(lastTso) ? 0 : seekPosition;
            fileChannel.position(pos);
            filePointer = pos;
            log.info(
                "seek last tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                    + seekEventCount);

            SeekResult result = new SeekResult(lastTso, lastEventType, lastEventTimestamp, maxTableId, markInfo);
            result.setBinlogFile(getFileName());
            result.setPosition(String.valueOf(seekPosition));
            return result;
        } catch (IOException e) {
            throw new PolardbxException("seek tso failed.", e);
        }
    }

    private boolean isValidTso4Recovery(String cts, int mode) {
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

    private long readInt32(ByteBuffer buffer) {
        return ((long) (0xff & buffer.get())) | ((long) (0xff & buffer.get()) << 8) |
            ((long) (0xff & buffer.get()) << 16) | ((long) (0xff & buffer.get()) << 24);
    }

    private String readString(long length, ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[(int) length];
        buffer.get(bytes);
        return new String(bytes);
    }

    private long readTableId(ByteBuffer buffer) {
        int length = getTableIdLength();
        return readLongByLength(buffer, length);
    }

    private void checkMode(String mode) {
        if (!"rw".equals(mode) && !"r".equals(mode)) {
            throw new PolardbxException("invalid mode " + mode);
        }
    }

    public long readLongByLength(ByteBuffer buffer, int length) {
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
            binlogEndInfo = new BinlogEndInfo(result.getLastEventTimestamp() * 1000, result.getLastTso());
        }
        return binlogEndInfo;
    }

    public static class SeekResult {
        private String binlogFile;
        private String position;
        private String lastTso;
        private Byte lastEventType;
        private Long lastEventTimestamp;
        private Long maxTableId;

        private MarkInfo markInfo;

        public SeekResult(String lastTso, Byte lastEventType, Long lastEventTimestamp) {
            this.lastTso = lastTso;
            this.lastEventType = lastEventType;
            this.lastEventTimestamp = lastEventTimestamp;
        }

        public SeekResult(String lastTso, Byte lastEventType, Long lastEventTimestamp, Long maxTableId,
                          MarkInfo markInfo) {
            this.lastTso = lastTso;
            this.lastEventType = lastEventType;
            this.lastEventTimestamp = lastEventTimestamp;
            this.maxTableId = maxTableId;
            this.markInfo = markInfo;
        }

        public String getBinlogFile() {
            return binlogFile;
        }

        public void setBinlogFile(String binlogFile) {
            this.binlogFile = binlogFile;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getLastTso() {
            return lastTso;
        }

        public void setLastTso(String lastTso) {
            this.lastTso = lastTso;
        }

        public Byte getLastEventType() {
            return lastEventType;
        }

        public void setLastEventType(Byte lastEventType) {
            this.lastEventType = lastEventType;
        }

        public Long getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        public MarkInfo getMarkInfo() {
            return markInfo;
        }

        public void setLastEventTimestamp(Long lastEventTimestamp) {
            this.lastEventTimestamp = lastEventTimestamp;
        }

        public Long getMaxTableId() {
            return maxTableId;
        }

        public void setMaxTableId(Long maxTableId) {
            this.maxTableId = maxTableId;
        }

        @Override
        public String toString() {
            return "SeekResult{" +
                "lastTso='" + lastTso + '\'' +
                ", lastEventType=" + lastEventType +
                ", lastEventTimestamp=" + lastEventTimestamp +
                ", maxTableId=" + maxTableId +
                '}';
        }
    }
}
