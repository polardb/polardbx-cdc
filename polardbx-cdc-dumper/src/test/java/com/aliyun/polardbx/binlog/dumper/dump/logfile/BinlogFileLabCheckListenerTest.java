/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

@Slf4j
public class BinlogFileLabCheckListenerTest extends BaseTest {
    @Test
    @SneakyThrows
    public void testOnFinishFile() {
        mockConfig(ConfigKeys.BINLOG_FIRST_FILE_CHECK_ENABLED, "false");
        BinlogFileLabCheckListener listener = new BinlogFileLabCheckListener();
        BinlogEndInfo binlogEndInfo = new BinlogEndInfo();
        binlogEndInfo.setLastXid(1L);
        binlogEndInfo.setLastEventTso("0");
        File file =
            new File(
                BinlogFileLabCheckListenerTest.class.getClassLoader().getResource("binlog/small_event_binlog.000906")
                    .toURI());
        listener.onFinishFile(file, binlogEndInfo);
        log.info(listener.getLastErrMsg());
        Assert.assertEquals("small_event_binlog.000906:Failed:1:42518491:v1", listener.getLastErrMsg());
        File file2 = new File("zimian_null.000001");
        listener.onFinishFile(file2, binlogEndInfo);
        Assert.assertEquals("zimian_null.000001:Failed:0:v1", listener.getLastErrMsg());
    }
}
