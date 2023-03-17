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
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.cache.Cache;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheMode;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MultiPartInputStream {

    public static final int DEFAULT_THREAD_SIZE = 3;
    public static final String HEADER_RANGES_SUPPORT = "Accept-Ranges";
    public static final String SUPPORT_RANGE_FLAG = "bytes";
    public static final String HEADER_CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_RANGE = "Content-Range";
    private static LinkedList<byte[]> bufferQueue = new LinkedList();
    public long DEFAULT_BUFFER_SIZE;
    private String url;
    private long fileSize;
    private int partCount;
    private List<PartStream> partStreamList = new ArrayList<>();
    private int pos = 0;
    private ExecutorService executorService;
    private InputStream fin;

    public MultiPartInputStream(String url) throws IOException {
        this(url, -1L);
    }

    public MultiPartInputStream(String url, long fileSize) throws IOException {
        this.url = url;
        this.fileSize = fileSize;
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_SEARCHTSO_QUICKMODE) && BinlogDumpContext.isSearch()) {
            this.DEFAULT_BUFFER_SIZE = fileSize;
        } else {
            CacheMode mode = CacheManager.getInstance().getMode();
            if (mode == CacheMode.DISK) {
                this.DEFAULT_BUFFER_SIZE = fileSize / DEFAULT_THREAD_SIZE + DEFAULT_THREAD_SIZE;
            } else {
                this.DEFAULT_BUFFER_SIZE = DynamicApplicationConfig.getInt(ConfigKeys.TASK_OSS_CACHE_SIZE);
            }
        }

        if (this.DEFAULT_BUFFER_SIZE > Integer.MAX_VALUE) {
            this.DEFAULT_BUFFER_SIZE = Integer.MAX_VALUE;
        }

        log.info(
            "init multi part with buffer size : " + DEFAULT_BUFFER_SIZE + ", stage : " + BinlogDumpContext.getStage());

        this.open();
    }

    public static void main(String[] args) {
        System.out.println(3 * 1024 * 1024 * 1024);
        System.out.println(2147483647L);
        System.out.println(Integer.MAX_VALUE);
    }

    private void initMultiPart() throws IOException {
        this.partCount = getPartCount();
        if (partCount == 1) {
            return;
        }
        int seq = 0;
        for (; seq < partCount - 1; seq++) {
            partStreamList.add(new PartStream(seq * DEFAULT_BUFFER_SIZE, (seq + 1) * DEFAULT_BUFFER_SIZE - 1, seq));
        }
        partStreamList.add(new PartStream(seq * DEFAULT_BUFFER_SIZE, -1, seq));
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
                // 不支持分段下载
                return 1;
            }
            this.fileSize = Long.parseLong(connection.getHeaderField(HEADER_CONTENT_LENGTH));
            return (int) ((fileSize / DEFAULT_BUFFER_SIZE) + (fileSize % DEFAULT_BUFFER_SIZE > 0 ? 1 : 0));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }

    private void open() throws IOException {
        initMultiPart();
        if (partCount == 1) {
            HttpURLConnection connection = connect();
            fin = connection.getInputStream();
        } else {
            executorService = Executors.newFixedThreadPool(DEFAULT_THREAD_SIZE, r -> {
                Thread t = new Thread(r, "multi-input-stream");
                t.setDaemon(true);
                return t;
            });
            for (PartStream p : partStreamList) {
                executorService.execute(p);
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
        int readLen = 0;
        while ((readLen = ps.read(b, off, len)) == 0) {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
            int skipPartNum = (int) (n / DEFAULT_BUFFER_SIZE);
            for (int i = pos; i < skipPartNum; i++) {
                this.partStreamList.get(i).close();
            }
            this.pos += skipPartNum;
            PartStream ps = this.partStreamList.get(pos);
            ps.skip(n - ps.begin);
        }
    }

    public void close() throws IOException {
        if (!CollectionUtils.isEmpty(this.partStreamList)) {
            for (PartStream p : this.partStreamList) {
                p.close();
            }
        }
        if (this.executorService != null) {
            this.executorService.shutdown();
        }
        if (fin != null) {
            try {
                fin.close();
            } catch (IOException e) {
            }
        }
    }

    private class PartStream implements Runnable {
        private static final int STATE_INIT = 0;
        private static final int STATE_FETCH = 1;
        private static final int STATE_FINISH = 2;
        private final long begin;
        private final long end;
        private final int seq;
        private volatile Cache cache;
        private Throwable t;
        private boolean running = true;
        private volatile byte state = STATE_INIT;
        private int readCount = 0;

        public PartStream(long begin, long end, int seq) {
            this.begin = begin;
            this.end = end;
            this.seq = seq;
        }

        private void check() {
            if (this.t != null) {
                throw new PolardbxException(t);
            }
        }

        public int read(byte[] data, int offset, int length) throws IOException {
            check();
            if (cache == null) {
                return 0;
            }
            int len = cache.read(data, offset, length);
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
            while (running && cache == null) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                }
            }
            if (!running) {
                return;
            }
            readCount += bytes;
            cache.skip((int) bytes);
        }

        public void close() throws IOException {
            running = false;
            tryRelease();
        }

        private void tryRelease() throws IOException {
            if (state == STATE_FINISH && !running) {
                if (cache != null) {
                    cache.close();
                }
            }
        }

        private void checkRange(String rangeString) {
            if (rangeString != null && rangeString.startsWith("bytes")) {
                String ranges[] = rangeString.substring(6).split("/")[0].split("-");
                long tBegin = Long.parseLong(ranges[0]);
                long tEnd = Long.parseLong(ranges[1]);
                if (tBegin == begin && tEnd == end) {
                    log.info("begin and end match");
                } else {
                    log.info("begin and end  not match");
                }
            } else {
                System.out.println(rangeString);
            }
        }

        @Override
        public void run() {
            InputStream in = null;
            HttpURLConnection connection = null;
            if (!running) {
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
                if (cacheSize > DEFAULT_BUFFER_SIZE) {
                    log.warn("part cache size =" + cacheSize + ", end = " + this.end + " , fileSize = " + fileSize);
                }
                cache = CacheManager.getInstance().allocate(in, cacheSize);
                cache.fetchData();
            } catch (Exception e) {
                log.error("read data failed!", e);
                this.t = e;
            } finally {
                this.state = STATE_FINISH;
                try {
                    tryRelease();
                } catch (IOException e) {

                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
