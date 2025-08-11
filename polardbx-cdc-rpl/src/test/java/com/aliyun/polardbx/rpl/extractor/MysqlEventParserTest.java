/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;

@Slf4j
public class MysqlEventParserTest extends RplWithGmsTablesBaseTest {
    @Test
    @SneakyThrows
    public void testCheckPosition() {
        try (MockedStatic<LabEventManager> labEventManagerMockedStatic = Mockito.mockStatic(LabEventManager.class)) {
            MysqlEventParser parser = new MysqlEventParser(1024, null);
            LogEventConvert logEventConvert = Mockito.mock(LogEventConvert.class);
            LogEvent logEvent = Mockito.mock(LogEvent.class);
            LogHeader logHeader = Mockito.mock(LogHeader.class);
            Mockito.when(logEvent.getHeader()).thenReturn(logHeader);
            Mockito.when(logEvent.getLogPos()).thenReturn(125L);
            Mockito.when(logEventConvert.getBinlogFileName()).thenReturn("b.1");
            parser.setBinlogParser(logEventConvert);

            // 第一次check，还没有初始化lastFile和lastPosition
            parser.checkPosition(logEvent);
            parser.setLastFile("b.1");
            parser.setLastPosition(4);
            // 正确的lastPosition < logEvent.pos，检查通过
            parser.checkPosition(logEvent);
            // rotate event 不检查
            Mockito.when(logHeader.getType()).thenReturn(LogEvent.ROTATE_EVENT);
            parser.checkPosition(logEvent);
            // 错误的lastPosition > logEvent.pos，检查失败
            Mockito.when(logHeader.getType()).thenReturn(LogEvent.UPDATE_ROWS_EVENT);
            parser.setLastPosition(127);
            parser.checkPosition(logEvent);
            labEventManagerMockedStatic.verify(() -> LabEventManager.logEvent(any(), any()), Mockito.times(1));
        }
    }
}
