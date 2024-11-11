/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class StreamObserverLogFetcher extends LogFetcher implements StreamObserver<DumpStream> {

    /**
     * Packet header sizes
     */
    public static final int NET_HEADER_SIZE = 4;
    public static final int SQLSTATE_LENGTH = 5;
    /**
     * Packet offsets
     */
    public static final int PACKET_LEN_OFFSET = 0;
    public static final int PACKET_SEQ_OFFSET = 3;
    /**
     * Maximum packet length
     */
    public static final int MAX_PACKET_LENGTH = (256 * 256 * 256 - 1);
    /**
     * BINLOG_DUMP options
     */
    public static final int BINLOG_DUMP_NON_BLOCK = 1;
    public static final int BINLOG_SEND_ANNOTATE_ROWS_EVENT = 2;
    private static final Logger logger = LoggerFactory.getLogger(StreamObserverLogFetcher.class);
    private AtomicLong receiveBytesCount = new AtomicLong(0);
    private StreamPipe pipe;
    private long lastReceiveTimestamp = 0;

    private ErrorHandle errorHandle;

    private boolean activate = true;

    public StreamObserverLogFetcher() throws IOException {
        pipe = new StreamPipe();
    }

    private final boolean fetch0(final int off, final int len) throws IOException {
        ensureCapacity(off + len);

        for (int count, n = 0; n < len; n += count) {
            if (0 > (count = pipe.read(buffer, off + n, len - n))) {
                // Reached end of input stream
                return false;
            }
        }

        if (limit < off + len) {
            limit = off + len;
        }
        return true;
    }

    @Override
    public boolean fetch() throws IOException {
        try {
            // Fetching packet header from input.
            if (!fetch0(0, NET_HEADER_SIZE)) {
                logger.warn("Reached end of input stream while fetching header");
                return false;
            }

            // Fetching the first packet(may a multi-packet).
            int netlen = getUint24(PACKET_LEN_OFFSET);
            int netnum = getUint8(PACKET_SEQ_OFFSET);
            if (!fetch0(NET_HEADER_SIZE, netlen)) {
                logger.warn("Reached end of input stream: packet #" + netnum + ", len = " + netlen);
                return false;
            }

            // Detecting error code.
            final int mark = getUint8(NET_HEADER_SIZE);
            if (mark != 0) {
                if (mark == 255) // error from master
                {
                    // Indicates an error, for example trying to fetch from
                    // wrong
                    // binlog position.
                    position = NET_HEADER_SIZE + 1;
                    final int errno = getInt16();
                    String sqlstate = forward(1).getFixString(SQLSTATE_LENGTH);
                    String errmsg = getFixString(limit - position);
                    throw new IOException("Received error packet:" + " errno = " + errno + ", sqlstate = " + sqlstate
                        + " errmsg = " + errmsg);
                } else if (mark == 254) {
                    // Indicates end of stream. It's not clear when this would
                    // be sent.
                    logger.warn("Received EOF packet from server, apparent" + " master disconnected.");
                    return false;
                } else {
                    // Should not happen.
                    throw new IOException("Unexpected response " + mark + " while fetching binlog: packet #" + netnum
                        + ", len = " + netlen);
                }
            }

            // The first packet is a multi-packet, concatenate the packets.
            while (netlen == MAX_PACKET_LENGTH) {
                if (!fetch0(0, NET_HEADER_SIZE)) {
                    logger.warn("Reached end of input stream while fetching header");
                    return false;
                }

                netlen = getUint24(PACKET_LEN_OFFSET);
                netnum = getUint8(PACKET_SEQ_OFFSET);
                if (!fetch0(limit, netlen)) {
                    logger.warn("Reached end of input stream: packet #" + netnum + ", len = " + netlen);
                    return false;
                }
            }

            // Preparing buffer variables to decoding.
            origin = NET_HEADER_SIZE + 1;
            position = origin;
            limit -= origin;
            return true;
        } catch (InterruptedIOException e) {
            close(); /* Do cleanup */
            logger.warn("I/O interrupted while reading from client socket", e);
            throw e;
        } catch (IOException e) {
            close(); /* Do cleanup */
            logger.error("I/O error while reading from client socket", e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (pipe != null) {
            pipe.close();
        }
        pipe = new StreamPipe();
        logger.warn("close fetcher");
        this.activate = false;
    }

    @Override
    public void onNext(DumpStream dumpStream) {
        if (!this.activate) {
            return;
        }
        final ByteString payload = dumpStream.getPayload();
        byte[] packets = payload.toByteArray();
        long counter = receiveBytesCount.addAndGet(packets.length);
        try {
            pipe.write(packets);
        } catch (IOException e) {
            throw new PolardbxException(e);
        }
        long now = System.currentTimeMillis();
        long diff = TimeUnit.MILLISECONDS.toSeconds(now - lastReceiveTimestamp);
        if (diff > 30) {
            logger.info("receive from dumper bytes : " + (counter) / diff + " b/s");
            lastReceiveTimestamp = now;
            receiveBytesCount.set(0);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (!this.activate) {
            return;
        }
        logger.error("dumper error!", t);
        if (this.errorHandle != null) {
            this.errorHandle.onError(t);
        }
    }

    @Override
    public void onCompleted() {
        logger.error("dumper completed, should not reach this method!");
    }

    public void registerErrorHandle(ErrorHandle h) {
        this.errorHandle = h;
    }

    public interface ErrorHandle {
        void onError(Throwable t);
    }

}
