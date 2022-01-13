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

import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.dumper.dump.util.ByteArray;
import com.aliyun.polardbx.binlog.dumper.dump.util.EventGenerator;
import com.aliyun.polardbx.binlog.dumper.metrics.Metrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Created by ziyang.lb
 */
@Data
@Slf4j
public class BinlogFile {

    public static final byte[] BINLOG_FILE_HEADER = new byte[] {(byte) 0xfe, 0x62, 0x69, 0x6e};
    public static final int MAX_PACKET_LENGTH = (256 * 256 * 256 - 1);

    private File file;
    private RandomAccessFile raf;
    private CRC32 crc32;
    private ByteBuffer writeBuffer;
    private long lastFlushTime;
    private int seekBufferSize;

    private Long logBegin;
    private Long logEnd;

    public BinlogFile(File file, String mode, int writeBufferSize, int seekBufferSize) throws FileNotFoundException {
        this.file = file;
        this.raf = new RandomAccessFile(file, mode);
        this.crc32 = new CRC32();
        this.writeBuffer = ByteBuffer.allocate(writeBufferSize);
        this.seekBufferSize = seekBufferSize * 1024 * 1024;//外部传过来的参数为单位为M，需要转化为字节
    }

    public void flush() throws IOException {
        if (writeBuffer.position() > 0) {
            log.debug("binlog write payload {}[{}->{}]", file.getName(), raf.getFilePointer(),
                raf.getFilePointer() + writeBuffer.position());
            raf.write(writeBuffer.array(), 0, writeBuffer.position());
        }
        writeBuffer.clear();
        lastFlushTime = System.currentTimeMillis();
        Metrics.get().incrementTotalFlushWriteCount();
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
     * 当前正在读取或者写入的文件位置(不包含write buffer)
     */
    public long position() throws IOException {
        return raf.getFilePointer();
    }

    /**
     * 文件的实际长度(不包含write buffer)
     */
    public long length() throws IOException {
        return raf.length();
    }

    /**
     * 已经写入文件的字节数，包含已经写入缓冲区但还未写入文件的字节
     */
    public long writeSize() throws IOException {
        return raf.getFilePointer() + writeBuffer.position();
    }

    /**
     * 定位到文件的某个位置
     */
    public void seek(long pos) throws IOException {
        raf.seek(pos);
    }

    /**
     * 定位到文件最后的位置
     */
    public void seekLast() throws IOException {
        raf.seek(length());
    }

    /**
     * 获取文件名，Simple Name
     */
    public String getFileName() {
        return file.getName();
    }

    /**
     * 跳过n个字节
     */
    public void skipBytes(int n) throws IOException {
        raf.skipBytes(n);
    }

    /**
     * 跳过文件头
     */
    public void skipHeader() throws IOException {
        assert raf.getFilePointer() == 0;
        raf.seek(4);
    }

    /*
     * 文件头，四个字节
     */
    public void writeHeader() throws IOException {
        raf.write(BINLOG_FILE_HEADER);
    }

    /**
     * 无需更新事件信息，直接写入binlog文件
     */
    public void writeEventWithoutUpdate(byte[] data, int offset, int length) throws IOException {
        assert data.length >= length;
        long startTime = System.currentTimeMillis();

        ByteArray array = new ByteArray(data, offset, length);
        array.skip(13);
        long thatPosition = array.readLong(4);
        long thisPosition = raf.getFilePointer() + writeBuffer.position() + length;

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

        writeInternal(data, offset, length);

        if (log.isDebugEnabled()) {
            log.debug("write {} {} {}", data, offset, length);
        }

        long endTime = System.currentTimeMillis();
        Metrics.get().incrementTotalWriteTime(endTime - startTime);
        Metrics.get().incrementTotalWriteEventCount();
        Metrics.get().incrementTotalWriteBytes(length);
    }

    /*
     * 更新binlog event的position信息，并写入文件
     */
    public void writeEvent(byte[] data, int offset, int length, boolean updateChecksum) throws IOException {
        assert data.length >= length;
        long startTime = System.currentTimeMillis();

        // 更新checksum
        long position = raf.getFilePointer() + writeBuffer.position() + length;
        EventGenerator.updatePos(data, position);
        if (updateChecksum) {
            EventGenerator.updateChecksum(data, offset, length);
        }

        writeInternal(data, offset, length);

        if (log.isDebugEnabled()) {
            log.debug("write {} {} {}", data, offset, length);
        }

        long endTime = System.currentTimeMillis();
        Metrics.get().incrementTotalWriteTime(endTime - startTime);
        Metrics.get().incrementTotalWriteEventCount();
        Metrics.get().incrementTotalWriteBytes(length);

    }

    public void readBytes(byte[] bytes) throws IOException {
        raf.read(bytes);
    }

    public void readBytes(byte[] bytes, int off, int length) throws IOException {
        raf.read(bytes, off, length);
    }

    /**
     * 定位最后一个记录tso的事件，并返回tso信息 </br>
     * 逐条seek可能会有性能问题，遇到性能问题再优化，可能需要引入额外的checkpoint文件
     */
    public SeekResult seekLastTso() {
        return seekLastTsoV2();
    }

    public void close() throws IOException {
        flush();
        raf.close();
        log.info("binlog file successfully closed.");
    }

    private SeekResult seekFirst() {
        long startTime = System.currentTimeMillis();
        try {
            final long fileLength = length();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            Byte lastEventType = null;
            Long lastEventTimestamp = null;
            boolean isFirst = true;

            if (fileLength > 4) {
                long nextEventAbsolutePos = 4;
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;
                do {

                    RandomAccessFile tempRaf = null;
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(bufSize);
                        tempRaf = new RandomAccessFile(file, "r");
                        tempRaf.getChannel().read(buffer, nextEventAbsolutePos);
                        buffer.flip();

                        if (buffer.hasRemaining() && buffer.remaining() >= 19) {
                            lastEventTimestamp = readLong(buffer);//read timestamp
                            lastEventType = buffer.get();//read event_type
                            buffer.position(buffer.position() + 4);//skip server_id
                            long eventSize = readLong(buffer);//read eventSizeer_id
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

            raf.seek(StringUtils.isBlank(lastTso) ? 0 : seekPosition);
            log.info("seek start tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                + seekEventCount);

            return new SeekResult(lastTso, lastEventType, lastEventTimestamp);
        } catch (IOException e) {
            throw new PolardbxException("seek tso failed.", e);
        }

    }

    // rows_query_envent: https://dev.mysql.com/doc/internals/en/rows-query-event.html
    private SeekResult seekLastTsoV2() {
        long startTime = System.currentTimeMillis();
        try {
            final long fileLength = length();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            byte lastEventType = -1;
            Long lastEventTimestamp = null;

            if (fileLength > 4) {
                long nextEventAbsolutePos = 4;
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;

                while (nextEventAbsolutePos < fileLength) {
                    RandomAccessFile tempRaf = null;
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(bufSize);
                        tempRaf = new RandomAccessFile(file, "r");
                        tempRaf.getChannel().read(buffer, nextEventAbsolutePos);
                        buffer.flip();

                        int nextEventRelativePos = buffer.position();
                        while (buffer.hasRemaining() && buffer.remaining() >= 19) {
                            lastEventTimestamp = readLong(buffer);//read timestamp
                            lastEventType = buffer.get();//read event_type
                            buffer.position(buffer.position() + 4);//skip server_id
                            long eventSize = readLong(buffer);//read eventSize

                            // next position需要通过计算获取，不能直接用header中的log_pos字段的值
                            // 因为对于超大事务(>2G)，log_pos的四个字节已经无法准确表达下个事件的位置
                            nextEventAbsolutePos += eventSize;
                            nextEventRelativePos += eventSize;

                            if (nextEventRelativePos > buffer.limit()) {
                                // 如果当前这个Event是ROWS_QUERY_LOG_EVENT，则不能直接跳过，需要将nextEventAbsolutePos进行回调后再break
                                if (lastEventType == LogEvent.ROWS_QUERY_LOG_EVENT) {
                                    nextEventAbsolutePos -= eventSize;
                                }
                                break;
                            } else {
                                if (lastEventType == LogEvent.ROWS_QUERY_LOG_EVENT) {
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
                                        lastTso = content;
                                        seekPosition = nextEventAbsolutePos;
                                    } else if (content.startsWith(MarkType.CTS.name())) {
                                        MarkInfo markInfo = new MarkInfo(content);
                                        lastTso = markInfo.getTso();
                                        seekPosition = nextEventAbsolutePos;
                                    }
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

            raf.seek(StringUtils.isBlank(lastTso) ? 0 : seekPosition);
            log.info("seek last tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                + seekEventCount);

            return new SeekResult(lastTso, lastEventType, lastEventTimestamp);
        } catch (IOException e) {
            throw new PolardbxException("seek tso failed.", e);
        }
    }

    private void writeInternal(byte[] data, int offset, int length) throws IOException {
        if (writeBuffer.remaining() < length) {
            if (log.isDebugEnabled()) {
                log.warn(
                    "force flushing write buffer because remaining space is not enough, remaining space is {}, write data length is {}.",
                    writeBuffer.remaining(),
                    length);
            }
            flush();
            Metrics.get().incrementTotalForceFlushWriteCount();

            assert (writeBuffer.remaining() == writeBuffer.capacity());
            if (writeBuffer.remaining() < length) {
                log.info("write data length [{}] is greater than write buffer capacity [{}].",
                    length,
                    writeBuffer.remaining());
                raf.write(data, offset, length);
            } else {
                writeBuffer.put(data, offset, length);
            }
        } else {
            writeBuffer.put(data, offset, length);
        }
    }

    private long readLong(ByteBuffer buffer) {
        return ((long) (0xff & buffer.get())) | ((long) (0xff & buffer.get()) << 8) |
            ((long) (0xff & buffer.get()) << 16) | ((long) (0xff & buffer.get()) << 24);
    }

    private String readString(byte length) throws IOException {
        byte[] bytes = new byte[length];
        raf.read(bytes);
        return new String(bytes);
    }

    private String readString(long length, ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[(int) length];
        buffer.get(bytes);
        return new String(bytes);
    }

    public long getLogBegin() {
        if (logBegin == null) {
            SeekResult result = seekFirst();
            logBegin = result.getLastEventTimestamp() * 1000;
        }
        return logBegin;
    }

    public long getLogEnd() {
        if (logEnd == null) {
            SeekResult result = seekLastTso();
            logEnd = result.getLastEventTimestamp() * 1000;
        }
        return logEnd;
    }

    public static class SeekResult {

        private String lastTso;
        private Byte lastEventType;
        private Long lastEventTimestamp;

        public SeekResult(String lastTso, Byte lastEventType, Long lastEventTimestamp) {
            this.lastTso = lastTso;
            this.lastEventType = lastEventType;
            this.lastEventTimestamp = lastEventTimestamp;
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

        public void setLastEventTimestamp(Long lastEventTimestamp) {
            this.lastEventTimestamp = lastEventTimestamp;
        }
    }
}
