/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.util.BufferUtil;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.ByteArray;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RelayFile {
    //前4个byte代表长度，最后一个byte代表标记为(保留)
    public static final int HEADER_SIZE = 5;

    private final File file;
    private final RandomAccessFile raf;
    private final FileChannel fileChannel;
    private final ByteBuffer writeBuffer;
    private final ByteArray byteArray;
    private long lastFlushTime;
    private long filePointer;

    public RelayFile(File file, int writeBufferSize, boolean useDirectByteBuffer)
        throws FileNotFoundException {
        this.file = file;
        this.raf = new RandomAccessFile(file, "rw");
        this.fileChannel = raf.getChannel();
        this.writeBuffer = useDirectByteBuffer ? ByteBuffer.allocateDirect(writeBufferSize)
            : ByteBuffer.allocate(writeBufferSize);
        this.byteArray = new ByteArray(new byte[HEADER_SIZE]);
    }

    public void flush() {
        try {
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
        } catch (IOException e) {
            throw new PolardbxException("flush relay file failed , " + getFileName(), e);
        }
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
            log.warn("truncate relay file {}, file pointer {}, file size {}", getFileName(), filePointer, fileSize);
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

    public void seekTo(long pos) throws IOException {
        if (pos > raf.length()) {
            throw new PolardbxException("invalid seek pos " + pos);
        }
        fileChannel.position(pos);
        filePointer = pos;
    }

    /**
     * 获取文件名，Simple Name
     */
    public String getFileName() {
        return file.getName();
    }

    public void writeData(byte[] data) {
        try {
            byteArray.reset();
            byteArray.writeLong(data.length, 4);
            writeInternal(byteArray.getData(), 0, byteArray.getLimit());
            writeInternal(data, 0, data.length);
        } catch (Exception e) {
            throw new PolardbxException("write data error!", e);
        }
    }

    public void close() {
        try {
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
            log.info("relay file {} successfully closed.", file.getName());
        } catch (IOException e) {
            throw new PolardbxException("relay file close failed, " + file.getName(), e);
        }
    }

    private void writeInternal(byte[] data, int offset, int length) {
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
    }

    public File getFile() {
        return file;
    }

    public long getLastFlushTime() {
        return lastFlushTime;
    }
}
