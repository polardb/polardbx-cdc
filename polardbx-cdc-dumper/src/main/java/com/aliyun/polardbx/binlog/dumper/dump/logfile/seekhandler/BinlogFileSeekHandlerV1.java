/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.MarkType;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.domain.MarkInfo;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.github.luben.zstd.ZstdException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.ROWS_QUERY_LOG_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TRANSACTION_PAYLOAD_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.XID_EVENT;
import static com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet.loadCharactorSetFromCN;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.handleTransactionPayload;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.isValidTso4Recovery;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.readInt32;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.readLongByLength;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.readString;
import static com.aliyun.polardbx.binlog.dumper.dump.logfile.BinlogFile.readTableId;
import static com.aliyun.polardbx.binlog.dumper.dump.util.TableIdManager.containsTableId;

/**
 * 老版本的seekTso逻辑，缺陷是buffer size会随着event大小变化，容易OOM
 * 仅用于兜底
 *
 * @author zm
 */
@Slf4j
public class BinlogFileSeekHandlerV1 implements BinlogFileSeekHandler {
    /**
     * 获取最后一个tso
     */
    @SneakyThrows
    @Override
    public SeekResult seekLastTso(BinlogFile binlogFile, int mode, int seekBufferSize, long startPos) {
        log.info("prepare to seek last tso from binlog file {}:{}", binlogFile.getFileName(), binlogFile.fileSize());
        long startTime = System.currentTimeMillis();
        try {
            final long fileLength = binlogFile.fileSize();
            long seekEventCount = 0;
            long seekPosition = 0;

            String lastTso = "";
            byte lastEventType = -1;
            Long lastEventTimestamp = null;
            Long maxTableId = null;
            Long maxXid = null;
            long lastXidPos = 0;
            MarkInfo markInfo = null;
            boolean shouldBreak = false;
            double expandFactor =
                DynamicApplicationConfig.getDouble(ConfigKeys.BINLOG_FILE_SEEK_LAST_TSO_EXPAND_FACTOR);

            if (fileLength > 4) {
                long nextEventAbsolutePos = startPos;
                // 该大小会随着event size的增大而增大
                int bufSize = seekBufferSize > fileLength ? (int) fileLength : seekBufferSize;
                // 配置大小
                final int rawBufSize = bufSize;
                ByteBuffer rawbuffer = null;
                ByteBuffer decompressedBuffer = null;
                RandomAccessFile tempRaf = null;
                boolean reAllocateRaw = true;
                boolean reAllocateDecompressed = true;
                boolean remainingDecompressed = false;
                while (!shouldBreak && (nextEventAbsolutePos < fileLength || remainingDecompressed)) {
                    checkInterrupted();

                    try {
                        tempRaf = new RandomAccessFile(binlogFile.getFile(), "r");

                        if (remainingDecompressed) {
                            if (!decompressedBuffer.hasRemaining()) {
                                // 处理完压缩数据
                                decompressedBuffer.clear();
                                remainingDecompressed = false;
                            }
                        } else if (!reAllocateRaw && rawbuffer.remaining() < 19) {
                            // 正常处理完一遍buffer内event，如果bufSize相比于配置的bufSize大，则缩小bufSize。
                            if (bufSize > rawBufSize) {
                                bufSize = (int) (bufSize / expandFactor);
                            }
                            if (bufSize < rawBufSize) {
                                bufSize = rawBufSize;
                            }
                            log.info("[-] raw data consume done, try make buffer smaller to {}.", bufSize);
                            reAllocateRaw = true;
                        }

                        if (reAllocateRaw) {
                            log.info("reallocate raw buffer size to {}", bufSize);
                            rawbuffer = ByteBuffer.allocate(bufSize);
                            tempRaf.getChannel().read(rawbuffer, nextEventAbsolutePos);
                            rawbuffer.flip();
                            reAllocateRaw = false;
                        }
                        if (reAllocateDecompressed) {
                            log.info("reallocate decompressed buffer size to {}", bufSize);
                            decompressedBuffer = ByteBuffer.allocate(bufSize);
                            reAllocateDecompressed = false;
                        }

                        // 如果刚刚读取的buffer的remaining小于event header的长度，
                        // 且没有未处理的压缩数据,说明对文件已经读取完，直接break
                        if (nextEventAbsolutePos + rawbuffer.remaining() >= fileLength
                            && rawbuffer.hasRemaining() && rawbuffer.remaining() < 19 && !remainingDecompressed) {
                            log.info("file read done");
                            break;
                        }

                        ByteBuffer buffer = rawbuffer;
                        if (remainingDecompressed) {
                            buffer = decompressedBuffer;
                        }

                        long nextEventRelativePos = buffer.position();
                        while (buffer.hasRemaining() && buffer.remaining() >= 19) {
                            checkInterrupted();

                            // read timestamp
                            lastEventTimestamp = readInt32(buffer);
                            // read event_type
                            lastEventType = buffer.get();
                            if (!LogEventUtil.validEventType(lastEventType)) {
                                shouldBreak = true;
                                break;
                            }
                            // skip server_id
                            buffer.position(buffer.position() + 4);
                            // read eventSize
                            long eventSize = readInt32(buffer);
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
                            if (!remainingDecompressed) {
                                // 处理解压数据，跟文件的绝对位置无关
                                nextEventAbsolutePos += eventSize;
                            }
                            nextEventRelativePos += eventSize;
                            markInfo = null;
                            if (nextEventRelativePos > buffer.limit()) {
                                // 如果当前这个Event是ROWS_QUERY_LOG_EVENT，则不能直接跳过，需要将nextEventAbsolutePos进行回调后再break，
                                // 但保证nextEventAbsolutePos < fileLength，否则会有死循环问题
                                if ((lastEventType == ROWS_QUERY_LOG_EVENT || lastEventType == XID_EVENT
                                    || containsTableId(lastEventType))
                                    && nextEventAbsolutePos < fileLength) {
                                    nextEventAbsolutePos -= eventSize;
                                    if (eventSize > buffer.limit()) {
                                        log.warn(
                                            "need expand buf size! relativePos:{}, absolutePos:{}, limit:{}, eventSize:{}",
                                            nextEventRelativePos, nextEventAbsolutePos,
                                            buffer.limit(), eventSize);
                                        bufSize = (int) (bufSize * expandFactor);
                                    }
                                }
                                reAllocateRaw = true;
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
                                } else if (lastEventType == XID_EVENT) {
                                    buffer.position(buffer.position() + 6);
                                    long xid = readLongByLength(buffer, 8);
                                    if (maxXid == null || xid > maxXid) {
                                        maxXid = xid;
                                        lastXidPos = nextEventAbsolutePos;
                                    }
                                }
                                buffer.position((int) nextEventRelativePos);
                                seekEventCount++;
                            }

                            if (nextEventAbsolutePos > fileLength) {
                                throw new PolardbxException("invalid next position {" + nextEventAbsolutePos
                                    + "}, its value can't be greater than file length {" + fileLength
                                    + "}");
                            }
                        }
                    } catch (ZstdException ze) {
                        // 读到了一个不完整的压缩事件
                        log.error("zstd decompressed failed.", ze);
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
            if (lastXidPos > pos) {
                maxXid -= 1;
            }
            binlogFile.getFileChanel().position(pos);
            binlogFile.setFilePointer(pos);
            log.info(
                "seek last tso cost time:" + (System.currentTimeMillis() - startTime) + "ms, skipped event count:"
                    + seekEventCount);
            if (maxXid != null) {
                binlogFile.updateXid(maxXid);
            }
            SeekResult result =
                new SeekResult(binlogFile.getFileName(), String.valueOf(seekPosition), lastTso, lastEventType,
                    lastEventTimestamp,
                    maxTableId, maxXid, markInfo);
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
}
