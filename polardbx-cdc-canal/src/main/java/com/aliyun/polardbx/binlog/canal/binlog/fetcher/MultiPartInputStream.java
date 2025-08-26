/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.SearchMode;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDumpContext;
import com.aliyun.polardbx.binlog.canal.binlog.cache.Cache;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheProgressListener;
import com.aliyun.polardbx.binlog.canal.binlog.cache.MemoryCache;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MultiPartInputStream {

    public static final String HEADER_RANGES_SUPPORT = "Accept-Ranges";
    public static final String SUPPORT_RANGE_FLAG = "bytes";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_RANGE = "Content-Range";
    public long partSize;
    private final String url;
    private long fileSize;
    private int partCount;
    private final List<PartStream> partStreamList = new ArrayList<>();
    private int pos = 0;
    private InputStream fin;
    private final String storageInstance;
    private final String fileName;
    private final AtomicInteger sequencer = new AtomicInteger(0);
    ;
    private final String uuid;

    private final ExecutorService executorService;

    private final List<Future> futureList = new ArrayList<>();

    public MultiPartInputStream(String url, long fileSize, String storageInstanceId, String fileName,
                                ExecutorService executorService)
        throws IOException {
        this.url = url;
        this.fileSize = fileSize;
        this.storageInstance = storageInstanceId;
        this.fileName = fileName;
        this.executorService = executorService;
        this.partSize = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE);

        uuid = UUID.randomUUID().toString();
        log.info("init multi part {} with buffer size : {}, stage : {} id : {}", fileName, partSize,
            BinlogDumpContext.getStage(), uuid);

        this.open();
    }

    private void initMultiPart() throws IOException {
        this.partCount = getPartCount();
        if (partCount == 1) {
            return;
        }
        int seq = 0;
        for (; seq < partCount - 1; seq++) {
            partStreamList.add(new PartStream(seq * partSize, (seq + 1) * partSize - 1, seq, sequencer));
        }
        partStreamList.add(new PartStream(seq * partSize, fileSize - 1, seq, sequencer));
    }

    private HttpURLConnection connect() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(20));
        connection.setReadTimeout((int) TimeUnit.MINUTES.toMillis(5));
        connection.setRequestProperty("User-Agent", "Mozilla/4.76");
        connection.setDoInput(true);
        connection.setDoOutput(false);
        connection.connect();
        return connection;
    }

    private int getPartCount() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = connect();
            String messageString = connection.getHeaderField(HEADER_RANGES_SUPPORT);
            if (!SUPPORT_RANGE_FLAG.equals(messageString)) {
                //   不支持分段下载
                return 1;
            }
            this.fileSize = Long.parseLong(connection.getHeaderField(HEADER_CONTENT_LENGTH));
            return (int) ((fileSize / partSize) + (fileSize % partSize > 0 ? 1 : 0));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }

    protected void open() throws IOException {
        initMultiPart();
        if (partCount == 1) {
            HttpURLConnection connection = connect();
            fin = connection.getInputStream();
        } else {
            AtomicInteger finishCounter = new AtomicInteger();
            MultiPartStreamMetrics metrics = new MultiPartStreamMetrics(fileName, url, storageInstance, finishCounter);
            partStreamList.forEach(p -> {
                PartStreamMetrics partStreamMetrics = new PartStreamMetrics(p.seq, p.end - p.begin);
                p.setMetrics(partStreamMetrics);
                partStreamMetrics.setFinishCounter(finishCounter);
                metrics.addPartMetrics(partStreamMetrics);
                p.setUuid(uuid);
            });
            metrics.startMetrics();
            for (PartStream p : partStreamList) {
                futureList.add(executorService.submit(p));
            }
        }
    }

    public int read(byte b[], int off, int len)
        throws IOException {
        if (this.partCount == 1) {
            return fin.read(b, off, len);
        }
        if (pos >= this.partStreamList.size()) {
            return -1;
        }
        PartStream ps = this.partStreamList.get(pos);
        int readLen = ps.read(b, off, len);
        if (readLen == -1) {
            ps.close();
            pos++;
            if (pos >= this.partStreamList.size()) {
                return -1;
            }
            ps = this.partStreamList.get(pos);
            return ps.read(b, off, len);
        }
        return readLen;
    }

    public void skip(long n) throws IOException {
        if (this.partCount == 1) {
            fin.skip(n);
        } else {
            int skipPartNum = (int) (n / partSize);
            for (int i = pos; i < skipPartNum; i++) {
                this.partStreamList.get(i).close();
            }
            this.pos += skipPartNum;
            int seq = sequencer.get();
            while (seq < pos && !sequencer.compareAndSet(seq, pos)) {
                seq = sequencer.get();
            }
            log.warn("reset {} {} sequence from {} to {} , current sequencer is {} id {}", storageInstance, fileName,
                seq, pos, sequencer.get(), uuid);
            PartStream ps = this.partStreamList.get(pos);
            ps.skip(n - ps.begin);
        }
    }

    public boolean isClosed() {
        if (!CollectionUtils.isEmpty(this.partStreamList)) {
            for (PartStream p : this.partStreamList) {
                if (!p.isClosed()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void closePartStream() throws IOException {
        if (!CollectionUtils.isEmpty(this.partStreamList)) {
            for (PartStream p : this.partStreamList) {
                p.close();
            }
        }
    }

    public void close() throws IOException {

        closePartStream();

        while (!isClosed()) {
            log.warn("close multi part stream failed for {} seq : {} , will retry", fileName, uuid);
            closePartStream();
        }

        if (fin != null) {
            try {
                fin.close();
            } catch (IOException e) {
            }
        }
        for (Future f : futureList) {
            if (!f.isDone() && !f.isCancelled()) {
                f.cancel(true);
            }
        }
    }

    private class PartStream implements Runnable {
        private static final int STATE_INIT = 0;
        private static final int STATE_FETCH = 1;
        private static final int STATE_FINISH = 2;
        private static final int STATE_CLOSE = 3;
        private final long begin;
        private final long end;
        private final int seq;
        private final Cache cache;
        private Throwable t;
        private volatile boolean running = true;
        private volatile byte state = STATE_INIT;
        private int readCount = 0;
        private InputStream in;

        @Setter
        private PartStreamMetrics metrics;

        public PartStream(long begin, long end, int seq, AtomicInteger sequencer) {
            this.begin = begin;
            this.end = end;
            this.seq = seq;
            long cacheSize = end - begin + 1;
            this.cache = new MemoryCache(storageInstance, url, seq, cacheSize, sequencer);
            ;
        }

        public void setUuid(String uuid) {
            ((MemoryCache) this.cache).setUuid(uuid);
        }

        private void check() {
            if (this.t != null) {
                throw new PolardbxException(t);
            }
        }

        public int read(byte[] data, int offset, int length) throws IOException {
            check();
            int len = cache.read(data, offset, length);
            check();
            if (len != -1) {
                readCount += len;
            } else {
                long end = this.end;
                if (end == -1) {
                    end = fileSize - 1;
                }
                boolean match = readCount == end - begin + 1;
                if (!match) {
                    throw new PolardbxException(
                        "detected consume part not match readCount : " + readCount + ", fileSize : " + fileSize
                            + ", begin : " + begin + ", end : " + this.end + " range : " + (end - begin + 1)
                            + ", cache detail : " + cache);
                }
            }
            return len;
        }

        public void skip(long bytes) {
            if (!running) {
                return;
            }
            readCount += bytes;
            cache.skip((int) bytes);
        }

        public void close() throws IOException {
            if (log.isDebugEnabled()) {
                log.debug("dn {} close part stream seq : {} from {} uuid : {}", storageInstance, seq, fileName, uuid);
            }
            running = false;
            try {
                if (this.in != null) {
                    this.in.close();
                }
            } catch (Exception ignored) {
            }

            this.cache.interrupt();
            tryRelease();
        }

        private void tryRelease() throws IOException {
            if ((state == STATE_FINISH || state == STATE_CLOSE) && !running) {
                cache.close();
                state = STATE_CLOSE;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("{} close {} part : {} , uuid {} failed for state {} or running {} , has buffer {} ",
                        storageInstance, fileName, seq, uuid, state, running, metrics.isAllocateBuffer());
                }
            }
        }

        public boolean isClosed() {
            return state == STATE_CLOSE;
        }

        @Override
        public void run() {
            HttpURLConnection connection = null;
            metrics.setStartTimestamp(System.currentTimeMillis());
            if (!running) {
                state = STATE_CLOSE;
                return;
            }
            try {
                this.state = STATE_FETCH;
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(20));
                connection.setReadTimeout((int) TimeUnit.MINUTES.toMillis(5));
                connection.setRequestProperty("User-Agent", "Mozilla/4.76");
                connection.setDoInput(true);
                connection.setDoOutput(false);
                StringBuilder rangeBuilder = new StringBuilder();
                rangeBuilder.append("bytes=").append(begin).append("-");
                long end = this.end;
                if (end > 0) {
                    rangeBuilder.append(end);
                } else {
                    end = fileSize - 1;
                }
                connection.setRequestProperty("Range", rangeBuilder.toString());
                connection.connect();
                Long contentLength = Long.parseLong(connection.getHeaderField("Content-Length"));
                if (contentLength != end - begin + 1) {
                    String errorMsg =
                        "content length " + contentLength + "  is not equal request range " + (end - begin + 1) + "["
                            + begin + ", " + end + "]";
                    log.warn(errorMsg);
                    throw new PolardbxException(errorMsg);
                }
                in = connection.getInputStream();
                long cacheSize = end - begin + 1;
                if (cacheSize > partSize) {
                    log.warn("part cache size ={}, end = {} , fileSize = {}", cacheSize, this.end, fileSize);
                }
                if (!running) {
                    try {
                        in.close();
                    } catch (Exception ignore) {
                    }
                    state = STATE_CLOSE;
                    return;
                }
                cache.setProgressListener(new CacheProgressListener() {

                    private long lastReceiveTimestamp;
                    private long lastReceiveBytes;

                    @Override
                    public void onStart() {
                        lastReceiveTimestamp = System.currentTimeMillis();
                    }

                    @Override
                    public void onAllocateBuffer() {
                        metrics.setAllocateBuffer(true);
                    }

                    @Override
                    public void onProgress(long bytesRead) {
                        metrics.setBytesRead(bytesRead);
                        long now = System.currentTimeMillis();
                        long timeUsed = now - lastReceiveTimestamp;
                        if (timeUsed == 0) {
                            timeUsed = 1;
                        }
                        metrics.setBps((bytesRead - lastReceiveBytes) * 1000 / timeUsed);
                        lastReceiveTimestamp = System.currentTimeMillis();
                        lastReceiveBytes = bytesRead;
                    }

                    @Override
                    public void onFinish() {
                        metrics.setFinishTimestamp(System.currentTimeMillis());
                        metrics.getFinishCounter().incrementAndGet();
                    }
                });
                cache.fetchData(in);
            } catch (Exception e) {
                if (!running) {
                    try {
                        in.close();
                    } catch (Exception ignore) {
                    }
                    state = STATE_CLOSE;
                    if (log.isDebugEnabled()) {
                        log.debug("close stream ignore exception ! dn {} fileName : {} seq : {} uuid : {}",
                            storageInstance, fileName, seq, uuid, e);
                    }
                    return;
                }
                log.error("read data failed from file : {} , seq {} : uuid : {}", fileName, seq, uuid, e);
                this.t = e;
                this.running = false;
            } finally {
                if (this.state == STATE_FETCH || this.state == STATE_INIT) {
                    this.state = STATE_FINISH;
                }
                metrics.setFinishTimestamp(System.currentTimeMillis());
                try {
                    tryRelease();
                } catch (IOException ignored) {

                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
