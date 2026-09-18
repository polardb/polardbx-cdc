/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.seekhandler.SeekResult;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * 单测 {@link LogFileGenerator#tryRecoverXidFromPrevFileRecord(SeekResult, String)}：
 * 该方法负责在 DumperX 启动恢复时，当 seek 到的 binlog 文件没有 XID 事件时，
 * 从前序文件的 binlog_oss_record 中兜底获取 lastXid，写回 SeekResult。
 *
 * <p>这是修复 Bug 的核心断点：保证 checkRotate 之前 XID_SEQ 已恢复，
 * 避免 rotate 出的新文件继承 lastXid=0，导致 rebuild 后 binlog 与原始位点不一致。
 */
public class LogFileGeneratorXidRecoverTest extends BaseTest {

    private LogFileGenerator logFileGenerator;
    private LogFileManager logFileManager;

    @Before
    public void setUp() {
        logFileManager = Mockito.mock(LogFileManager.class);
        ExecutionConfig executionConfig = Mockito.mock(ExecutionConfig.class);
        logFileGenerator = new LogFileGenerator(
            logFileManager,
            1024 * 1024 * 100,
            false,
            FlushPolicy.FlushPerTxn,
            1000,
            1024 * 64,
            "test-task",
            TaskType.Dumper,
            "test-group",
            "test-stream",
            executionConfig
        );
    }

    /**
     * 场景 1：seekResult 已携带 lastXid，应直接返回，不查 record。
     */
    @Test
    public void testSkipWhenAlreadyHasXid() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        seekResult.setLastXid(6115L);

        spy.tryRecoverXidFromPrevFileRecord(seekResult, "binlog.000004");

        Assert.assertEquals(Long.valueOf(6115L), seekResult.getLastXid());
        Mockito.verify(spy, Mockito.never())
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.anyString());
    }

    /**
     * 场景 2：seekResult 为 null，方法应安全返回不抛异常。
     */
    @Test
    public void testNullSeekResult() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);

        spy.tryRecoverXidFromPrevFileRecord(null, "binlog.000004");

        Mockito.verify(spy, Mockito.never())
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.anyString());
    }

    /**
     * 场景 3：currentFileName 为 null，方法应跳过查询。
     */
    @Test
    public void testNullCurrentFileName() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);

        spy.tryRecoverXidFromPrevFileRecord(seekResult, null);

        Assert.assertNull(seekResult.getLastXid());
        Mockito.verify(spy, Mockito.never())
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.anyString());
    }

    /**
     * 场景 4：当前文件是首文件（无前序文件），方法应跳过 record 查询。
     */
    @Test
    public void testNoPrevFile() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);

        try (MockedStatic<BinlogFileUtil> mocked = Mockito.mockStatic(BinlogFileUtil.class)) {
            mocked.when(() -> BinlogFileUtil.getPrevBinlogFileName("binlog.000001")).thenReturn(null);

            spy.tryRecoverXidFromPrevFileRecord(seekResult, "binlog.000001");
        }

        Assert.assertNull(seekResult.getLastXid());
        Mockito.verify(spy, Mockito.never())
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.anyString());
    }

    /**
     * 场景 5：前序文件 record 中 lastXid 为 null，seekResult.lastXid 保持 null。
     */
    @Test
    public void testRecordReturnsNull() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        Mockito.doReturn(null).when(spy)
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.eq("binlog.000003"));

        try (MockedStatic<BinlogFileUtil> mocked = Mockito.mockStatic(BinlogFileUtil.class)) {
            mocked.when(() -> BinlogFileUtil.getPrevBinlogFileName("binlog.000004"))
                .thenReturn("binlog.000003");

            spy.tryRecoverXidFromPrevFileRecord(seekResult, "binlog.000004");
        }

        Assert.assertNull(seekResult.getLastXid());
    }

    /**
     * 场景 6（修复 Bug 的核心场景）：前序文件 record 中存在 lastXid 时，应写回到 seekResult。
     * <p>这是 BUG 触发的关键路径：
     * - 当前 binlog 文件（如 000004）只有 FDE+CTS+RotateEvent，没有 XID 事件，seek 出 lastXid=null；
     * - 前序 binlog 文件（如 000003）的 binlog_oss_record 已记录 lastXid=6115；
     * - 修复后，方法能从 record 兜底回 6115，避免 XID_SEQ 停留在 0。
     */
    @Test
    public void testRecoverFromPrevRecord() {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        Mockito.doReturn(6115L).when(spy)
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.eq("binlog.000003"));

        try (MockedStatic<BinlogFileUtil> mocked = Mockito.mockStatic(BinlogFileUtil.class)) {
            mocked.when(() -> BinlogFileUtil.getPrevBinlogFileName("binlog.000004"))
                .thenReturn("binlog.000003");

            spy.tryRecoverXidFromPrevFileRecord(seekResult, "binlog.000004");
        }

        Assert.assertEquals(Long.valueOf(6115L), seekResult.getLastXid());
    }

    /**
     * 场景 7：recoverXidAndApplyToBinlogFile 成功恢复时，XID_SEQ被正确设置。
     */
    @Test
    public void testRecoverXidAndApplyToBinlogFile_success() throws Exception {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        Mockito.doReturn(6115L).when(spy)
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.eq("binlog.000003"));

        // mock binlogFile
        BinlogFile mockBinlogFile = Mockito.mock(BinlogFile.class);
        spy.setBinlogFile(mockBinlogFile);

        try (MockedStatic<BinlogFileUtil> mocked = Mockito.mockStatic(BinlogFileUtil.class)) {
            mocked.when(() -> BinlogFileUtil.getPrevBinlogFileName("binlog.000004"))
                .thenReturn("binlog.000003");

            spy.recoverXidAndApplyToBinlogFile(seekResult, "binlog.000004");
        }

        Assert.assertEquals(Long.valueOf(6115L), seekResult.getLastXid());
        Mockito.verify(mockBinlogFile).updateXid(6115L);
    }

    /**
     * 场景 8：recoverXidAndApplyToBinlogFile 无法恢复（record无值）时，不应调用updateXid。
     */
    @Test
    public void testRecoverXidAndApplyToBinlogFile_noRecovery() throws Exception {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        Mockito.doReturn(null).when(spy)
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.eq("binlog.000003"));

        BinlogFile mockBinlogFile = Mockito.mock(BinlogFile.class);
        spy.setBinlogFile(mockBinlogFile);

        try (MockedStatic<BinlogFileUtil> mocked = Mockito.mockStatic(BinlogFileUtil.class)) {
            mocked.when(() -> BinlogFileUtil.getPrevBinlogFileName("binlog.000004"))
                .thenReturn("binlog.000003");

            spy.recoverXidAndApplyToBinlogFile(seekResult, "binlog.000004");
        }

        Assert.assertNull(seekResult.getLastXid());
        Mockito.verify(mockBinlogFile, Mockito.never()).updateXid(Mockito.anyLong());
    }

    /**
     * 场景 9：recoverXidAndApplyToBinlogFile 已有lastXid时，直接apply不查record。
     */
    @Test
    public void testRecoverXidAndApplyToBinlogFile_alreadyHasXid() throws Exception {
        LogFileGenerator spy = Mockito.spy(logFileGenerator);
        SeekResult seekResult = new SeekResult("tso-1", (byte) 0, 0L);
        seekResult.setLastXid(9999L);

        BinlogFile mockBinlogFile = Mockito.mock(BinlogFile.class);
        spy.setBinlogFile(mockBinlogFile);

        spy.recoverXidAndApplyToBinlogFile(seekResult, "binlog.000004");

        Assert.assertEquals(Long.valueOf(9999L), seekResult.getLastXid());
        Mockito.verify(mockBinlogFile).updateXid(9999L);
        Mockito.verify(spy, Mockito.never())
            .seekLastXidFromBinlogOssRecord(Mockito.any(), Mockito.anyString());
    }
}
