/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

public class DecompressCmdTest {

    @Test
    public void testExecuteZstd() throws Exception {
        String srcFile = "a";
        String dstFile = "b";
        System.out.println(dstFile);
        ZstdCmd zstdCmd = Mockito.mock(ZstdCmd.class, Mockito.withSettings().useConstructor(srcFile, dstFile));
        ProcessBuilder builder = Mockito.mock(ProcessBuilder.class);
        Process process = Mockito.mock(Process.class);
        Mockito.when(process.waitFor()).thenReturn(0);
        Mockito.when(builder.start()).thenReturn(process);
        Mockito.when(zstdCmd.buildProcessBuilder()).thenReturn(builder);
        Mockito.doCallRealMethod().when(zstdCmd).execute();
        zstdCmd.execute();
        Mockito.verify(zstdCmd, Mockito.times(1)).renameFile();
    }

    @Test
    public void testBuildProcessBuilder() throws Exception {
        String srcFile = "a";
        String dstFile = "b";
        System.out.println(dstFile);
        ZstdCmd zstdCmd = Mockito.mock(ZstdCmd.class, Mockito.withSettings().useConstructor(srcFile, dstFile));
        Mockito.when(zstdCmd.buildProcessBuilder()).thenCallRealMethod();
        ProcessBuilder builder = zstdCmd.buildProcessBuilder();
        List<String> commands = builder.command();
        Assert.assertArrayEquals(new String[] {"zstd", "-d", srcFile, "-o", dstFile + "_tmp_zstd"}, commands.toArray());
    }

    @Test(expected = IOException.class)
    public void testBuildRenameFile() throws Exception {
        String srcFile = "a";
        String dstFile = "b";
        System.out.println(dstFile);
        ZstdCmd zstdCmd = Mockito.mock(ZstdCmd.class, Mockito.withSettings().useConstructor(srcFile, dstFile));
        Mockito.doCallRealMethod().when(zstdCmd).renameFile();
        zstdCmd.renameFile();

    }
}
