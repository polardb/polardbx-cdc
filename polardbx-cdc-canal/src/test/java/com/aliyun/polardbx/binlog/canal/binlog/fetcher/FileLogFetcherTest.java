/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * FileLogFetcher 单元测试，重点验证对 zstd 压缩 binlog 文件的透明解压能力。
 * 背景：下载的 binlog 文件可能是 zstd 压缩格式（magic number 0x28B52FFD），
 * 直接按原始 binlog 读取会抛出 "Error binlog file header" 异常，需自动识别并解压。
 */
public class FileLogFetcherTest extends BaseTest {

    /**
     * 构造一段以 binlog magic number 开头的伪 binlog 内容：4 字节 magic + 16 字节递增 body。
     */
    private byte[] buildFakeBinlog() {
        byte[] full = new byte[FileLogFetcher.BINLOG_MAGIC.length + 16];
        System.arraycopy(FileLogFetcher.BINLOG_MAGIC, 0, full, 0, FileLogFetcher.BINLOG_MAGIC.length);
        for (int i = 0; i < 16; i++) {
            full[FileLogFetcher.BINLOG_MAGIC.length + i] = (byte) i;
        }
        return full;
    }

    /**
     * 验证：普通（未压缩）binlog 文件可正常打开并读取 magic 之后的内容。
     */
    @Test
    public void testOpenPlainBinlogFile() throws IOException {
        byte[] full = buildFakeBinlog();
        File dir = Files.createTempDirectory("filelogfetcher-plain").toFile();
        File plainFile = new File(dir, "binlog.000001");
        try (FileOutputStream fos = new FileOutputStream(plainFile)) {
            fos.write(full);
        }

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(plainFile);
            Assert.assertTrue(fetcher.fetch());
            Assert.assertEquals(16, fetcher.getLimit());
            for (int i = 0; i < 16; i++) {
                Assert.assertEquals(i, fetcher.getUint8(i));
            }
        } finally {
            fetcher.close();
            plainFile.delete();
            dir.delete();
        }
    }

    /**
     * 验证：zstd 压缩的 binlog 文件（文件名不带 .zst 后缀，但内容为 zstd 压缩）
     * 能被自动识别并解压，不再抛出 "Error binlog file header"，且解压后读取到的内容与原始一致。
     */
    @Test
    public void testOpenZstdCompressedBinlogFile() throws IOException {
        byte[] full = buildFakeBinlog();
        File dir = Files.createTempDirectory("filelogfetcher-zstd").toFile();
        // 模拟真实场景：本地文件名不含 .zst 后缀，但内容为 zstd 压缩数据
        File zstFile = new File(dir, "mysql-bin.001689");
        try (ZstdOutputStream zos = new ZstdOutputStream(new FileOutputStream(zstFile))) {
            zos.write(full);
        }

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(zstFile);
            Assert.assertTrue(fetcher.fetch());
            Assert.assertEquals(16, fetcher.getLimit());
            for (int i = 0; i < 16; i++) {
                Assert.assertEquals(i, fetcher.getUint8(i));
            }
        } finally {
            fetcher.close();
            zstFile.delete();
            dir.delete();
        }
    }

    /**
     * 验证：既非 binlog magic 也非 zstd magic 的非法文件头，仍应抛出 "Error binlog file header" 异常。
     */
    @Test
    public void testOpenIllegalFileHeader() throws IOException {
        File dir = Files.createTempDirectory("filelogfetcher-illegal").toFile();
        File badFile = new File(dir, "binlog.000002");
        try (FileOutputStream fos = new FileOutputStream(badFile)) {
            fos.write(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        }

        FileLogFetcher fetcher = new FileLogFetcher();
        IOException caught = null;
        try {
            fetcher.open(badFile);
        } catch (IOException e) {
            caught = e;
        } finally {
            fetcher.close();
            badFile.delete();
            dir.delete();
        }
        Assert.assertNotNull(caught);
        Assert.assertTrue(caught.getMessage().contains("Error binlog file header"));
    }

    /**
     * 构造一段较长的伪 binlog：4 字节 magic + bodyLen 字节 body。
     * 为覆盖 open 的 filePosition 定位分支，需保证 open 读取 format description event 后
     * 从偏移 EVENT_LEN_OFFSET(=9) 解析出的 event length 是一个可控的小值。
     * open 在读完 4 字节 magic 后，会从文件绝对偏移 4 处继续读入 buffer[0..]，
     * 因此 buffer[9..12] 对应文件绝对偏移 [13..16]，此处写入小端 19。
     */
    private byte[] buildLongFakeBinlog(int bodyLen) {
        byte[] full = new byte[FileLogFetcher.BINLOG_MAGIC.length + bodyLen];
        System.arraycopy(FileLogFetcher.BINLOG_MAGIC, 0, full, 0, FileLogFetcher.BINLOG_MAGIC.length);
        for (int i = FileLogFetcher.BINLOG_MAGIC.length; i < full.length; i++) {
            full[i] = (byte) (i % 100);
        }
        full[13] = 19;
        full[14] = 0;
        full[15] = 0;
        full[16] = 0;
        return full;
    }

    /**
     * 将内容写入临时目录下的文件，zstd 为 true 时以 zstd 压缩写入。
     */
    private File writeTempFile(String dirPrefix, String name, byte[] content, boolean zstd) throws IOException {
        File dir = Files.createTempDirectory(dirPrefix).toFile();
        File file = new File(dir, name);
        if (zstd) {
            try (ZstdOutputStream zos = new ZstdOutputStream(new FileOutputStream(file))) {
                zos.write(content);
            }
        } else {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content);
            }
        }
        return file;
    }

    /**
     * 验证：通过文件路径字符串重载 open(String) 打开普通 binlog 文件可正常读取。
     */
    @Test
    public void testOpenByStringPath() throws IOException {
        byte[] full = buildFakeBinlog();
        File plainFile = writeTempFile("filelogfetcher-strpath", "binlog.000010", full, false);

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(plainFile.getAbsolutePath());
            Assert.assertTrue(fetcher.fetch());
            Assert.assertEquals(16, fetcher.getLimit());
        } finally {
            fetcher.close();
            plainFile.delete();
            plainFile.getParentFile().delete();
        }
    }

    /**
     * 验证：普通 binlog 文件在 filePosition > BIN_LOG_HEADER_SIZE 时，
     * 走 FileChannel 随机定位分支（读取 format description event 并定位到指定位点），open 不抛异常且可继续 fetch。
     */
    @Test
    public void testOpenWithFilePositionPlain() throws IOException {
        byte[] full = buildLongFakeBinlog(100);
        File plainFile = writeTempFile("filelogfetcher-pos-plain", "binlog.000011", full, false);

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(plainFile, 20L);
            Assert.assertTrue(fetcher.fetch());
            Assert.assertTrue(fetcher.getLimit() > 0);
        } finally {
            fetcher.close();
            plainFile.delete();
            plainFile.getParentFile().delete();
        }
    }

    /**
     * 验证：通过 open(String, long) 重载在指定 filePosition 打开普通 binlog 文件。
     */
    @Test
    public void testOpenByStringPathWithFilePosition() throws IOException {
        byte[] full = buildLongFakeBinlog(100);
        File plainFile = writeTempFile("filelogfetcher-pos-strpath", "binlog.000012", full, false);

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(plainFile.getAbsolutePath(), 20L);
            Assert.assertTrue(fetcher.fetch());
            Assert.assertTrue(fetcher.getLimit() > 0);
        } finally {
            fetcher.close();
            plainFile.delete();
            plainFile.getParentFile().delete();
        }
    }

    /**
     * 验证：zstd 压缩 binlog 文件在 filePosition > BIN_LOG_HEADER_SIZE 时，
     * 走解压流重新打开 + skipFully 顺序跳过分支，open 不抛异常且可继续 fetch。
     */
    @Test
    public void testOpenZstdWithFilePosition() throws IOException {
        byte[] full = buildLongFakeBinlog(100);
        File zstFile = writeTempFile("filelogfetcher-pos-zstd", "mysql-bin.001690", full, true);

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(zstFile, 20L);
            Assert.assertTrue(fetcher.fetch());
            Assert.assertTrue(fetcher.getLimit() > 0);
        } finally {
            fetcher.close();
            zstFile.delete();
            zstFile.getParentFile().delete();
        }
    }

    /**
     * 验证：空文件（读不到 BIN_LOG_HEADER_SIZE 个字节）应抛出 "No binlog file header" 异常。
     */
    @Test
    public void testOpenEmptyFileThrows() throws IOException {
        File emptyFile = writeTempFile("filelogfetcher-empty", "binlog.000013", new byte[0], false);

        FileLogFetcher fetcher = new FileLogFetcher();
        IOException caught = null;
        try {
            fetcher.open(emptyFile);
        } catch (IOException e) {
            caught = e;
        } finally {
            fetcher.close();
            emptyFile.delete();
            emptyFile.getParentFile().delete();
        }
        Assert.assertNotNull(caught);
        Assert.assertTrue(caught.getMessage().contains("No binlog file header"));
    }

    /**
     * 验证：读取到文件末尾后 fetch() 返回 false（覆盖 in.read 返回 -1 的收尾分支）。
     */
    @Test
    public void testFetchReturnFalseWhenReachEnd() throws IOException {
        byte[] full = buildFakeBinlog();
        File plainFile = writeTempFile("filelogfetcher-end", "binlog.000014", full, false);

        FileLogFetcher fetcher = new FileLogFetcher();
        try {
            fetcher.open(plainFile);
            Assert.assertTrue(fetcher.fetch());
            // magic 之后仅 16 字节 body，已在首次 fetch 读完，再次 fetch 到达文件尾返回 false
            Assert.assertFalse(fetcher.fetch());
        } finally {
            fetcher.close();
            plainFile.delete();
            plainFile.getParentFile().delete();
        }
    }

    /**
     * 验证：zstd 压缩文件解压后内容不足 BIN_LOG_HEADER_SIZE 个字节时，
     * 在压缩分支内抛出 "No binlog file header"（覆盖 open 中 compressed 分支的头长度校验）。
     */
    @Test
    public void testOpenZstdDecompressedTooShortThrows() throws IOException {
        // 压缩内容仅 1 字节，文件头仍是 zstd magic，会被识别为压缩文件；
        // 但解压后读不满 4 字节的 binlog 文件头，触发 "No binlog file header"。
        File zstFile = writeTempFile("filelogfetcher-zstd-short", "mysql-bin.001691", new byte[] {0x01}, true);

        FileLogFetcher fetcher = new FileLogFetcher();
        IOException caught = null;
        try {
            fetcher.open(zstFile);
        } catch (IOException e) {
            caught = e;
        } finally {
            fetcher.close();
            zstFile.delete();
            zstFile.getParentFile().delete();
        }
        Assert.assertNotNull(caught);
        Assert.assertTrue(caught.getMessage().contains("No binlog file header"));
    }

    /**
     * 验证：openZstdStream 在 ZstdInputStream 构造失败时，会关闭已打开的底层 FileInputStream 并重新抛出异常
     * （覆盖 openZstdStream 的 catch 分支）。通过 mockConstruction 让 ZstdInputStream 构造抛异常，
     * 并借助反射调用该 private 方法。
     */
    @Test
    public void testOpenZstdStreamRethrowWhenConstructFails() throws Exception {
        byte[] full = buildFakeBinlog();
        File file = writeTempFile("filelogfetcher-zstd-ctorfail", "binlog.000015", full, false);

        FileLogFetcher fetcher = new FileLogFetcher();
        Method method = FileLogFetcher.class.getDeclaredMethod("openZstdStream", File.class);
        method.setAccessible(true);
        boolean thrownFromOpen = false;
        try (MockedConstruction<ZstdInputStream> ignored = Mockito.mockConstruction(ZstdInputStream.class,
            (mock, context) -> {
                throw new IOException("mock zstd construct fail");
            })) {
            try {
                method.invoke(fetcher, file);
            } catch (InvocationTargetException e) {
                // openZstdStream 在 ZstdInputStream 构造失败时进入 catch 分支关闭底层流并重新抛出，
                // 反射将其包装为 InvocationTargetException，说明 catch 分支被执行。
                thrownFromOpen = true;
            }
        } finally {
            fetcher.close();
            file.delete();
            file.getParentFile().delete();
        }
        Assert.assertTrue("openZstdStream 应在 ZstdInputStream 构造失败时抛出异常", thrownFromOpen);
    }

    /**
     * 验证：skipFully 在底层流 skip() 返回非正数时，走逐字节 read 补偿逻辑，
     * 并在读到流末尾（read 返回 -1）时提前结束（覆盖 skipped<=0 分支及 read()<0 的 break）。
     * 通过反射调用该 private static 方法，并注入 skip 恒返回 0 的自定义流。
     */
    @Test
    public void testSkipFullyWithNonPositiveSkip() throws Exception {
        final int[] readCount = {0};
        InputStream stream = new InputStream() {
            @Override
            public int read() {
                // 前 3 次返回有效字节，之后返回 -1 触发 break
                return readCount[0]++ < 3 ? 0x01 : -1;
            }

            @Override
            public long skip(long n) {
                // 恒返回 0，强制 skipFully 走 read 补偿分支
                return 0L;
            }
        };

        Method method = FileLogFetcher.class.getDeclaredMethod("skipFully", InputStream.class, long.class);
        method.setAccessible(true);
        // 请求跳过 10 字节，但流只有 3 字节可读，最终因 EOF 提前 break
        method.invoke(null, stream, 10L);

        Assert.assertTrue(readCount[0] >= 4);
    }
}
