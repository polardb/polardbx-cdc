/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDumpContext;
import com.aliyun.polardbx.binlog.canal.binlog.cache.Cache;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheProgressListener;
import com.aliyun.polardbx.binlog.canal.binlog.cache.MemoryCache;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class MultiPartInputStream extends InputStream {

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
    private final String uuid;

    private final ExecutorService executorService;

    private final List<Future> futureList = new ArrayList<>();
    /**
     * 单字节缓存
     * 用于单字节读取
     * 目前外部不会使用这个
     */
    private final byte[] singleByteCache = new byte[1];

    public MultiPartInputStream(String url, long fileSize, String storageInstanceId, String fileName,
                                ExecutorService executorService) throws IOException {
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
            log.warn("not support multi part to download");
            return;
        }
        if (partSize > Integer.MAX_VALUE) {
            log.error("part size is {}", partSize);
            throw new IllegalArgumentException("part size should not be greater than Integer.MAX_VALUE");
        }
        int seq = 0;
        for (; seq < partCount - 1; seq++) {
            partStreamList.add(new PartStream(seq * partSize, (seq + 1) * partSize - 1, seq, sequencer));
        }
        partStreamList.add(new PartStream(seq * partSize, fileSize - 1, seq, sequencer));
    }

    private HttpURLConnection connect() throws IOException {
        return connectWithRetry(null);
    }

    private HttpURLConnection createConnection(String range) throws IOException {
        // noinspection StartSSRFNetHookCheckingInspection
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_TIMEOUT_MS));
        connection.setReadTimeout(
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_READ_TIMEOUT_MS));
        connection.setRequestProperty("User-Agent", "Mozilla/4.76");
        connection.setDoInput(true);
        connection.setDoOutput(false);
        if (range != null) {
            connection.setRequestProperty("Range", range);
        }
        return connection;
    }

    /**
     * 建立连接，对 connect timed out 等瞬时网络异常做有限次数退避重试，重试耗尽后抛出最后一次建连的异常
     */
    private HttpURLConnection connectWithRetry(String range) throws IOException {
        int maxRetryCount = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_COUNT);
        long backoffBaseMs =
            DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_BACKOFF_BASE_MS);
        long backoffMaxMs =
            DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_BACKOFF_MAX_MS);
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = createConnection(range);
                connection.connect();
                // connect() 仅完成 TCP 握手，发送请求与读取响应头实际发生在首次访问响应内容时；
                // 而 getHeaderField 会吞掉这一阶段的 IOException 并返回 null，使得连接被重置这类瞬时故障
                // 表现为“响应头缺失”，既不会在此处被重试，也会让上层拿到不带 cause 的
                // NumberFormatException 而无法识别为网络异常。因此用 getResponseCode() 在重试范围内
                // 强制完成响应头读取，让瞬时故障以 IOException 暴露出来
                int responseCode = connection.getResponseCode();
                // 5xx 属于服务端瞬时故障（如 OSS 503 SlowDown 限流），必须在此主动转成 IOException 才能走退避重试：
                // getResponseCode() 本身不会因错误状态码抛异常，放过它等于把 5xx 当成建连成功，状态码信息随即丢失。
                // 4xx 属于确定性错误（签名过期、对象不存在），不在此处转换，由后续 getInputStream 直接失败，避免无效重试
                if (responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    throw new IOException("server returned http response code : " + responseCode + " for file : "
                        + fileName + " uuid : " + uuid);
                }
                return connection;
            } catch (IOException e) {
                if (connection != null) {
                    connection.disconnect();
                }
                if (!isRetryableIOException(e) || attempt >= maxRetryCount) {
                    throw e;
                }
                // attempt 过大时左移会溢出，超过可左移位数直接取退避上限
                long backoffMs = attempt >= Long.SIZE - 2 ? backoffMaxMs
                    : Math.min(backoffBaseMs << attempt, backoffMaxMs);
                log.warn("connect failed for file : {} uuid : {} , attempt : {} , will retry in {} ms", fileName,
                    uuid, attempt + 1, backoffMs, e);
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /**
     * 建连期的 IOException 默认当成瞬时网络异常重试，仅排除重试不会改变结果的确定性错误：
     * ProtocolException 对应 getResponseCode 阶段的重定向过多，MalformedURLException 对应 url 本身不合法。
     * FileNotFoundException 对应 404，保留作为防御：当前路径下 getResponseCode 已拿到状态行时不会再抛它，
     * 404 实际在后续 getInputStream 阶段暴露，同样不会被重试。
     * 注意不能反转为正向白名单：列举不全时会退化回“漏重试”，而那正是引入本重试逻辑要修的问题
     */
    private static boolean isRetryableIOException(IOException e) {
        if (e instanceof FileNotFoundException || e instanceof ProtocolException
            || e instanceof MalformedURLException) {
            return false;
        }
        // SSL 握手失败必须区分成因，不能整类归为不可重试：证书不可信、主机名不匹配等属确定性错误，
        // 但 "Remote host terminated the handshake"、"Received fatal alert: internal_error" 这类
        // 握手期连接中断与服务端瞬时拒绝仍是可恢复故障，一并排除会把网络抖动升级成任务重启
        if (e instanceof SSLHandshakeException) {
            return !hasCertificateCause(e);
        }
        return true;
    }

    /**
     * 回溯 cause 链判断握手失败是否源于证书校验（典型链为 SSLHandshakeException -> ValidatorException）
     */
    private static boolean hasCertificateCause(Throwable e) {
        // 限制回溯深度，避免 cause 链自引用或成环时死循环；
        // 配置值小于 1 时兜底为 1，保证至少检查异常自身，避免证书判定被误配置旁路后退化成无意义重试
        int maxDepth =
            Math.max(1, DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CAUSE_TRACE_MAX_DEPTH));
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < maxDepth; depth++) {
            if (cause instanceof CertificateException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 读取并解析 Content-Length。
     * 响应头缺失时抛 EOFException（响应未完整到达，属于上层可识别的可重试网络异常），
     * 取值非法时抛 IOException（服务端协议异常，重试无意义）；
     * 两者都避开了 Long.parseLong(null) 抛出的、cause 链为空的 NumberFormatException
     */
    private long parseContentLength(HttpURLConnection connection) throws IOException {
        String value = connection.getHeaderField(HEADER_CONTENT_LENGTH);
        if (value == null) {
            throw new EOFException("missing response header " + HEADER_CONTENT_LENGTH + " for file : " + fileName
                + " uuid : " + uuid);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IOException("illegal response header " + HEADER_CONTENT_LENGTH + " : " + value + " for file : "
                + fileName + " uuid : " + uuid, e);
        }
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
            this.fileSize = parseContentLength(connection);
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

    @Override
    public int read() throws IOException {
        // 外部目前不会调用
        if (this.read(singleByteCache, 0, 1) == -1) {
            return -1;
        }
        return singleByteCache[0] & 0xFF;
    }

    public int read(byte b[], int off, int len) throws IOException {
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

    public long skip(long n) throws IOException {
        if (this.partCount == 1) {
            return fin.skip(n);
        } else {
            long skipCounter = 0;
            for (; n > 0 && pos < this.partStreamList.size(); pos++) {
                PartStream pis = this.partStreamList.get(pos);
                long partSkipSize = pis.skip(n);
                skipCounter += partSkipSize;
                n -= partSkipSize;
                if (!pis.hasRemain()) {
                    pis.close();
                } else {
                    log.info("skip end part stream seq : {} , uuid : {} , read count : {}", pis.seq, uuid,
                        pis.readCount);
                    break;
                }
            }
            if (this.pos >= this.partStreamList.size()) {
                return skipCounter;
            }
            int seq = sequencer.get();
            while (seq < pos && !sequencer.compareAndSet(seq, pos)) {
                seq = sequencer.get();
            }
            log.warn("reset {} {} sequence from {} to {} , current sequencer is {} id {}", storageInstance, fileName,
                seq, pos, sequencer.get(), uuid);
            return skipCounter;
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
        /**
         * 读取的字节数
         * readCount 变量在 read 和 skip 中会修改， hasRemain方法读取
         * hasRemain方法是私有方法，只会在skip中调用
         * skip 方法只有在URLFetcher的open()方法内部打开链接后调用
         * read 方法只会fetch方法时调用
         * binlog消费总是先URLFetcher.open->stream.skip->URLFetcher.fetch->stream.read，所以不会有并发问题
         */
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

        public long skip(long bytes) {
            if (!running) {
                return 0;
            }
            long remain = end - begin + 1 - readCount;
            long skip = 0;
            if (remain > bytes) {
                skip = bytes;
            } else {
                skip = remain;
            }
            readCount += (int) skip;
            cache.skip((int) skip);
            return skip;
        }

        private boolean hasRemain() {
            return end - begin + 1 - readCount > 0;
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
                StringBuilder rangeBuilder = new StringBuilder();
                rangeBuilder.append("bytes=").append(begin).append("-");
                long end = this.end;
                if (end > 0) {
                    rangeBuilder.append(end);
                } else {
                    end = fileSize - 1;
                }
                connection = connectWithRetry(rangeBuilder.toString());
                long contentLength = parseContentLength(connection);
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
