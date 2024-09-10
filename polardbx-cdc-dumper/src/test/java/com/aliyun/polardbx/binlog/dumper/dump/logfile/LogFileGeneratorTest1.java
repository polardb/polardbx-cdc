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
