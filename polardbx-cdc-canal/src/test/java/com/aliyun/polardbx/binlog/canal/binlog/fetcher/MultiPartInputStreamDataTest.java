/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiPartInputStreamDataTest extends BaseTest {

    private int port;
    private Server server;

    private static String referenceDownloadFileName;
    private static boolean isRangeDownload;

    @Before
    public void beforeInit() throws Exception {
        // 启动临时HTTP服务器
        cleanUrlMocker();
        startHttpServer();
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "1004");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT, "1048576");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RDS_BINLOG_AUTO_DECOMPRESS, "true");

        // 使用registerSpringObject方法来注册CacheManager，而不是直接通过反射修改objectMap
        registerSpringObject(CacheManager.class, new CacheManager());
    }

    @After
    public void afterCleanup() throws Exception {
        System.out.println("end of http server on port " + port);
        // 停止HTTP服务器
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void mergeAllTest() throws IOException, NoSuchFieldException, IllegalAccessException {
        testHttpRangeDownload();
        testHttpSkipRange();
        testHttpSkipEqualPartSizeRange();
        testHttpZstdDownload();
        testHttpZstdSkipRange();
    }

    /**
     * 测试HTTP分段下载功能
     */
    public void testHttpRangeDownload()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        dumpFile("split_binlog.001", 4, 199);
        Assert.assertTrue(isRangeDownload);
        Assert.assertEquals("split_binlog.001", referenceDownloadFileName);
    }

    /**
     * 测试HTTP分段下载skip功能
     */
    public void testHttpSkipRange()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        dumpFile("split_binlog.001", 10001, 16);
        Assert.assertTrue(isRangeDownload);
        Assert.assertEquals("split_binlog.001", referenceDownloadFileName);
    }

    /**
     * 测试HTTP分段下载skip == part size功能
     */
    public void testHttpSkipEqualPartSizeRange()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        dumpFile("split_binlog.001", 1004, 181);
        Assert.assertTrue(isRangeDownload);
        Assert.assertEquals("split_binlog.001", referenceDownloadFileName);
    }

    /**
     * 测试HTTP分段下载压缩功能
     */
    public void testHttpZstdDownload()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        dumpFile("split_binlog.001.zst", 4, 199);
        Assert.assertTrue(isRangeDownload);
        Assert.assertEquals("split_binlog.001.zst", referenceDownloadFileName);
    }

    /**
     * 测试HTTP分段下载skip功能
     */
    public void testHttpZstdSkipRange()
        throws IOException, NoSuchFieldException, IllegalAccessException {
        dumpFile("split_binlog.001.zst", 10001, 16);
        Assert.assertTrue(isRangeDownload);
        Assert.assertEquals("split_binlog.001.zst", referenceDownloadFileName);
    }

    public void dumpFile(String fileName, long skipPos, int expectedCount)
        throws IOException, NoSuchFieldException, IllegalAccessException {
        System.out.println("Testing HTTP range download on port " + port);

        URLLogFetcher fetcher = new URLLogFetcher("1", "split_binlog.001");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        System.out.println(
            "open fetcher " + fileName + " skip " + skipPos + " port " + port + "executor : " + executor);
        fetcher.open("http://localhost:" + port + "/" + fileName, skipPos, 100, executor);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext lc = new LogContext();
        lc.setServerCharactorSet(new ServerCharactorSet());
        lc.setLogPosition(new LogPosition("split_binlog.001", 4));
        int eventCount = 0;
        while (fetcher.fetch()) {
            LogEvent le = decoder.decode(fetcher, lc);
            if (le == null) {
                continue;
            }
            eventCount++;
        }
        fetcher.close();
        System.out.println("end event count: " + eventCount);

        executor.shutdown();
        Assert.assertEquals(expectedCount, eventCount);
    }

    /**
     * 启动HTTP服务器，支持分段下载
     *
     * @throws IOException IO异常
     */
    private void startHttpServer() throws IOException {
        // 获取测试资源目录
        URL resourceUrl = MultiPartInputStreamDataTest.class.getClassLoader().getResource(".");
        if (resourceUrl == null) {
            throw new IllegalStateException("Cannot find test resources directory");
        }

        final File docRoot = new File(resourceUrl.getPath());

        // 使用0让系统自动分配端口
        server = new Server(0);
        server.setHandler(new HttpFileHandler(docRoot));

        try {
            server.start();
            // 获取实际分配的端口
            port = server.getURI().getPort();
            System.out.println("HTTP server started on port " + port);
        } catch (Exception e) {
            throw new IOException("Failed to start HTTP server", e);
        }
    }

    /**
     * HTTP文件处理器，支持Range请求（分段下载）
     */
    static class HttpFileHandler extends AbstractHandler {

        private final File docRoot;

        public HttpFileHandler(final File docRoot) {
            this.docRoot = docRoot;
        }

        @Override
        public void handle(String target, org.eclipse.jetty.server.Request baseRequest,
                           HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

            String method = request.getMethod().toUpperCase();
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")) {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                baseRequest.setHandled(true);
                return;
            }

            // 移除开头的斜杠获取文件名
            String fileName = target.startsWith("/") ? target.substring(1) : target;

            // 处理特殊路径
            if (fileName.isEmpty()) {
                fileName = "index.html";
            }

            final File file = new File(this.docRoot, fileName);
            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                baseRequest.setHandled(true);
                return;
            }
            if (!file.canRead() || file.isDirectory()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                baseRequest.setHandled(true);
                return;
            }
            referenceDownloadFileName = fileName;
            // 解析Range头部
            String rangeHeader = request.getHeader("Range");

            if ("OPTIONS".equals(method)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader("Allow", "GET, HEAD, OPTIONS");
                response.setHeader("Accept-Ranges", "bytes");
                baseRequest.setHandled(true);
                return;
            }

            String contentType = "application/octet-stream";
            if (fileName.endsWith(".txt")) {
                contentType = "text/plain";
            } else if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                contentType = "text/html";
            }

            // 支持分段下载
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequest(file, rangeHeader, response, contentType);
                isRangeDownload = true;
            } else {
                // 普通请求
                handleFullRequest(file, request, response, contentType);
            }

            baseRequest.setHandled(true);
        }

        /**
         * 处理分段请求
         */
        private void handleRangeRequest(File file, String rangeHeader, HttpServletResponse response, String contentType)
            throws IOException {
            long fileLength = file.length();
            String rangeValue = rangeHeader.substring(6); // 去掉"bytes="前缀

            // 解析范围，格式如 "0-100" 或 "0-" 或 "-100"
            long start = 0;
            long end = fileLength - 1;

            int dashIndex = rangeValue.indexOf('-');
            if (dashIndex > 0) {
                start = Long.parseLong(rangeValue.substring(0, dashIndex));
                if (dashIndex < rangeValue.length() - 1) {
                    end = Long.parseLong(rangeValue.substring(dashIndex + 1));
                }
            } else if (dashIndex == 0) {
                // 格式如 "-100"，表示最后100字节
                long suffixLength = Long.parseLong(rangeValue.substring(1));
                start = fileLength - suffixLength;
                if (start < 0) {
                    start = 0;
                }
            } else {
                // 格式如 "100-"，表示从100字节到最后
                start = Long.parseLong(rangeValue);
            }

            // 边界检查
            if (start >= fileLength) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                return;
            }

            if (end >= fileLength) {
                end = fileLength - 1;
            }

            long contentLength = end - start + 1;

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Type", contentType);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(contentLength));
            System.out.println("Content-Range: bytes " + start + "-" + end + "/" + fileLength);

            // 直接写入响应流
            writeToResponse(file, response, start, contentLength);
        }

        /**
         * 处理完整文件请求
         */
        private void handleFullRequest(File file, HttpServletRequest request, HttpServletResponse response,
                                       String contentType)
            throws IOException {
            String method = request.getMethod().toUpperCase();
            if (method.equals("GET")) {
                response.setHeader("Content-Type", contentType);
                response.setHeader("Content-Length", String.valueOf(file.length()));
                response.setHeader("Accept-Ranges", "bytes");
                response.setStatus(HttpServletResponse.SC_OK);

                // 直接写入响应流
                writeToResponse(file, response, 0, file.length());
            } else if (method.equals("HEAD")) {
                response.setHeader("Content-Type", contentType);
                response.setHeader("Content-Length", String.valueOf(file.length()));
                response.setHeader("Accept-Ranges", "bytes");
                response.setStatus(HttpServletResponse.SC_OK);
            }
        }

        /**
         * 将文件内容写入响应流
         */
        private void writeToResponse(File file, HttpServletResponse response, long start, long length)
            throws IOException {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                raf.seek(start);
                OutputStream out = response.getOutputStream();
                long remaining = length;
                byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    int readLen = (int) Math.min(buffer.length, remaining);
                    int bytesRead = raf.read(buffer, 0, readLen);
                    if (bytesRead == -1) {
                        break;
                    }
                    out.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
                out.flush();
            } finally {
                raf.close();
            }
        }
    }
}