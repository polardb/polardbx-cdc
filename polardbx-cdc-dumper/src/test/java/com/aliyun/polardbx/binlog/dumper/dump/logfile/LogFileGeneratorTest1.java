/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.protocol.TxnFlag;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

@Slf4j
public class LogFileGeneratorTest1 extends BaseTestWithGmsTables {

    @Test
    public void testArchiveServerIdCheck() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
            TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.ARCHIVE).setType(TxnType.DML).build();
        Mockito.when(generator.serverIdCheckFailed(currentToken)).thenCallRealMethod();
        Assert.assertFalse(generator.serverIdCheckFailed(currentToken));
    }

    @Test
    public void testNormalServerIdCheckFailed() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
            TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.NORMAL).setType(TxnType.DML).build();
        Mockito.when(generator.serverIdCheckFailed(currentToken)).thenCallRealMethod();
        Assert.assertTrue(generator.serverIdCheckFailed(currentToken));
    }

    @Test
    public void testNeedServerIdCheckNormal() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
            TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.NORMAL).setType(TxnType.DML).build();
        Mockito.when(generator.needCheckServerId(currentToken)).thenCallRealMethod();
        Assert.assertTrue(generator.needCheckServerId(currentToken));
    }

    @Test
    public void testNeedServerIdCheckArchive() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
            TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.ARCHIVE).setType(TxnType.DML).build();
        Mockito.when(generator.needCheckServerId(currentToken)).thenCallRealMethod();
        Assert.assertFalse(generator.needCheckServerId(currentToken));
    }
}
