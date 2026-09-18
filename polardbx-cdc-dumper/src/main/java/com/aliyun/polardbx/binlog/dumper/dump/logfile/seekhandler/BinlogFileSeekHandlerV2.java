/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.ROWS_QUERY_LOG_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TRANSACTION_PAYLOAD_EVENT;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.handleTransactionPayload;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.readLongByLength;
import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.containsTableId;

/**
 * 默认的seekTso逻辑，buffer size是固定的较小值（1M）
 *
 * @author zm
 */
@Slf4j
public class BinlogFileSeekHandlerV2 implements BinlogFileSeekHandler {
    private long maxNextAbsolutePos = 0;
    private final long maxLoopTimes = 20;
    private long loopTimes = 0;
    private final boolean isLabEnv = DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV);
    private int maxUncompressedSize;

    /**
     * 获取最后一个tso
     */
    @SneakyThrows
    @Override
    public SeekResult seekLastTso(BinlogFile binlogFile, int mode, int seekBufferSize, long startPos) {
        String fileName = binlogFile.getFileName();
        log.info("prepare to seek last tso from binlog file {}:{}", fileName, binlogFile.fileSize());
        long startTime = System.currentTimeMillis();
        double expandFactor = DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_EXPAND_FACTOR);
        maxUncompressedSize =
            DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_TRANSACTION_COMPRESSION_MAX_UNCOMPRESSED_SIZE);
        try {
            final long fileLength = binlogFile.fileSize();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            byte lastEventType = -1;
            Long lastEventTimestamp = null;
            Long maxTableId = null;
            MarkInfo markInfo = null;
            boolean shouldBreak = false;
            Long maxXid = null;
            long lastXidPosition = 0;
            ByteBuffer rawbuffer = null;
            ByteBuffer decompressedBuffer = null;
            boolean reAllocateDecompressed = true;
            boolean remainingDecompressed = false;

            if (fileLength > 4) {
                long nextEventAbsolutePos = startPos;
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;
                // 配置大小
                rawbuffer = ByteBuffer.allocate(bufSize);
                RandomAccessFile tempRaf = null;
                boolean needClearBuffer = true;
                while (!shouldBreak && (nextEventAbsolutePos < fileLength || remainingDecompressed)) {
                    checkInterrupted();

                    try {
                        checkDeadLoop(nextEventAbsolutePos, binlogFile.getFile().getName());
                        if (remainingDecompressed) {
                            if (!decompressedBuffer.hasRemaining()) {
                                // 处理完压缩数据
                                decompressedBuffer.clear();
                                remainingDecompressed = false;
                            }
                        }

                        if (reAllocateDecompressed) {
                            if (bufSize >= 1024 * 1024 * 1024) {
                                log.warn("reallocate decompressed buffer size to {}", bufSize);
                            }
                            decompressedBuffer = ByteBuffer.allocate(bufSize);
                            reAllocateDecompressed = false;
                        }

                        // max xid length(8) + header length(19)
                        if (rawbuffer.remaining() < 27 || needClearBuffer) {
                            rawbuffer.clear();
                            tempRaf = new RandomAccessFile(binlogFile.getFile(), "r");
                            tempRaf.getChannel().read(rawbuffer, nextEventAbsolutePos);
                            needClearBuffer = false;
                            rawbuffer.flip();
                        }

                        // 如果刚刚读取的buffer的remaining小于event header的长度，
                        // 且没有未处理的压缩数据,说明对文件已经读取完，直接break
                        if (nextEventAbsolutePos + rawbuffer.remaining() >= fileLength
                            && rawbuffer.hasRemaining() && rawbuffer.remaining() < 27 && !remainingDecompressed) {
                            log.info("file read done");
                            break;
                        }

                        ByteBuffer buffer = rawbuffer;
                        if (remainingDecompressed) {
                            buffer = decompressedBuffer;
                        }

                        long nextEventRelativePos = buffer.position();
                        while (buffer.hasRemaining() && buffer.remaining() >= 27) {
                            checkInterrupted();

                            // read timestamp
                            lastEventTimestamp = BinlogFile.readInt32(buffer);
                            // read event_type
                            lastEventType = buffer.get();
                            if (!LogEventUtil.validEventType(lastEventType)) {
                                shouldBreak = true;
                                break;
                            }
                            // skip server_id
                            buffer.position(buffer.position() + 4);
                            // read eventSize
                            long eventSize = BinlogFile.readInt32(buffer);
                            if (eventSize < 19) {
                                shouldBreak = true;
                                break;
                            }
                            if (eventSize > 512 * 1024 * 1024) {
                                log.warn("Big Event Size:{} at {}:{}", eventSize, binlogFile.getFile(),
                                    nextEventAbsolutePos);
                            }
                            // next position需要通过计算获取，不能直接用header中的log_pos字段的值
                            // 因为对于超大事件(>2G)，log_pos的四个字节已经无法准确表达下个事件的位置
                            nextEventRelativePos += eventSize;
                            if (!remainingDecompressed) {
                                // 处理解压数据，跟文件的绝对位置无关
                                nextEventAbsolutePos += eventSize;
                            }
                            markInfo = null;
                            if (nextEventRelativePos > buffer.limit() && (lastEventType == ROWS_QUERY_LOG_EVENT
                                || lastEventType == TRANSACTION_PAYLOAD_EVENT)) {
                                // rows query log 以及 transaction payload event(最大512MB)必须要全部存进buffer
                                // ATTENTION: 如果出现死循环，一定是因为rows query超出了buffer size，但目前不可能
                                if (nextEventAbsolutePos > fileLength) {
                                    // 该文件是最后一个不完整的ROWS_QUERY_LOG_EVENT，即使重新读取, buffer.limit()受文件长度影响也不会大于nextEventRelativePos
                                    // 因此，直接break
                                    shouldBreak = true;
                                    break;
                                }
                                if (lastEventType == TRANSACTION_PAYLOAD_EVENT && eventSize > buffer.capacity()) {
                                    // 压缩事件超过了buffer.capacity()，扩容
                                    if (eventSize <= maxUncompressedSize) {
                                        rawbuffer = ByteBuffer.allocate((int) eventSize);
                                        log.warn("large cmp! resize to {} at {}", eventSize, nextEventAbsolutePos);
                                    } else {
                                        throw new PolardbxException("cmp err!" + fileName + ":" + nextEventAbsolutePos);
                                    }
                                }
                                nextEventAbsolutePos -= eventSize;
                                needClearBuffer = true;
                                break;
                            } else {
                                if (lastEventType == TRANSACTION_PAYLOAD_EVENT) {
                                    // 是压缩事务类型，将内容解压后放到另一buffer再正常处理
                                    buffer.position(buffer.position() + 6);
                                    if (handleTransactionPayload(buffer, decompressedBuffer)) {
                                        remainingDecompressed = true;
                                    } else {
                                        reAllocateDecompressed = true;
                                        nextEventAbsolutePos -= eventSize;
                                        bufSize = (int) (bufSize * expandFactor);
                                        buffer.position((int) (nextEventRelativePos - eventSize));
                                        log.warn("Big Event Size:{} at {}:{} in decompressing", eventSize,
                                            binlogFile.getFile(),
                                            nextEventAbsolutePos);
                                    }
                                    break;
                                } else if (lastEventType == ROWS_QUERY_LOG_EVENT) {
                                    // 跳过剩余的header
                                    buffer.position(buffer.position() + 6);
                                    // 在之前的版本中，ROWS_QUERY_LOG_EVENT只用来记录tso，这个字段的值并不是1，而是tso的长度
                                    byte tsoSize = buffer.get();

                                    String content;
                                    if (remainingDecompressed) {
                                        // compressed data 没有checksum的 4 字节
                                        content = BinlogFile.readString(eventSize - 19 - 1, buffer);
                                    } else {
                                        // eventSize减去header长度、checksum的长度和payload的第一个字节，便是query_log的字符串的长度
                                        content = BinlogFile.readString(eventSize - 19 - 1 - 4, buffer);
                                    }
                                    // 只有在历史版本中，tsoSize的值才会大于1，验证一下长度是否合法
                                    if (tsoSize > 1 && tsoSize != 54) {
                                        throw new PolardbxException("invalid tso size " + tsoSize);
                                    }
                                    // 如果tsoSize等于54(历史版本，ROWS_QUERY_LOG_EVENT只用来记录tso)
                                    // 或者content的前缀是CTS(ROWS_QUERY_LOG_EVENT用来记录更多元信息)
                                    // 则说明该Event记录的是一个commit tso
                                    if (tsoSize == 54) {
                                        if (BinlogFile.isValidTso4Recovery(content, mode)) {
                                            lastTso = content;
                                            seekPosition = nextEventAbsolutePos;
                                        }
                                    } else if (content.startsWith(MarkType.CTS.name())) {
                                        markInfo = new MarkInfo(content);
                                        if (BinlogFile.isValidTso4Recovery(markInfo.getTso(), mode)) {
                                            lastTso = markInfo.getTso();
                                            seekPosition = nextEventAbsolutePos;
                                        }
                                    }
                                } else if (containsTableId(lastEventType)) {
                                    buffer.position(buffer.position() + 6);
                                    long tableId = BinlogFile.readTableId(buffer);
                                    maxTableId = maxTableId == null ? tableId : Math.max(maxTableId, tableId);
                                } else if (lastEventType == LogEvent.XID_EVENT) {
                                    buffer.position(buffer.position() + 6);
                                    long xid = readLongByLength(buffer, 8);
                                    if (maxXid == null || xid > maxXid) {
                                        maxXid = xid;
                                        lastXidPosition = nextEventAbsolutePos;
                                    }
                                }
                                seekEventCount++;
                                if (nextEventRelativePos > buffer.limit()) {
                                    // 下一事件已不在buffer内，需要clear之后重新读取event
                                    needClearBuffer = true;
                                    break;
                                }
                                buffer.position((int) nextEventRelativePos);
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
            binlogFile.getFileChanel().position(pos);
            binlogFile.setFilePointer(pos);
            if (lastXidPosition > pos) {
                // 正常来说xid之后一定会跟一个tso，如果最终找到的最后一个xid的位置比最后一个tso的位置要大
                // 说明这个xid所在的事务的tso没有写进来，之后会被truncate，因此回退1
                maxXid -= 1;
                log.warn("last xid position {} is greater than last tso position {}, xid:{}", lastXidPosition, pos,
                    maxXid);
            }
            log.info(
                "seek last tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                    + seekEventCount);

            SeekResult result =
                new SeekResult(binlogFile.getFileName(), String.valueOf(seekPosition), lastTso, lastEventType,
                    lastEventTimestamp,
                    maxTableId, maxXid, markInfo);
            if (maxXid != null) {
                binlogFile.updateXid(maxXid);
            }
            return result;
        } catch (IOException e) {
            throw new PolardbxException("seek tso failed.", e);
        }
    }

    private void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("seek tso interrupted ...");
        }
    }

    public void checkDeadLoop(long nextAbsolutePos, String fileName) {
        if (isLabEnv) {
            if (nextAbsolutePos > maxNextAbsolutePos) {
                maxNextAbsolutePos = nextAbsolutePos;
                loopTimes = 0;
            } else {
                loopTimes++;
            }
            if (loopTimes >= maxLoopTimes) {
                LabEventManager.logEvent(LabEventType.SEEK_LAST_TSO_CHECK,
                    fileName + ":" + nextAbsolutePos + ":" + maxNextAbsolutePos);
            }
        }
    }
}
