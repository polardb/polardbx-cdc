/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

public class LogDecoderTest {

    private LogEvent mockEventForLogPos(long logPos, int len){
        LogHeader header = Mockito.mock(LogHeader.class);
        LogEvent event = Mockito.mock(LogEvent.class, Mockito.withSettings().useConstructor(header));
        Mockito.when(event.getLogPos()).thenReturn(logPos);
        Mockito.when(header.getLogPos()).thenReturn(logPos);
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getEventLen()).thenReturn(len);
        return event;
    }

    @Test
    public void testDecodeMax4bytesPosition() throws IOException {
        LogDecoder decoder = Mockito.mock(LogDecoder.class, Mockito.withSettings().useConstructor());
        long pos1 = 100;
        // 200 - 196 = 4
        long pos2 = 4;
        // 4 + 100
        long pos3 = 104;
        // 204 - 196 = 8
        long pos4 = 8;
        int size = 100;
        // 回退阈值是196
        LogEvent event1 = mockEventForLogPos(pos1, size);
        LogEvent event2 = mockEventForLogPos(pos2, size);
        LogEvent event3 = mockEventForLogPos(pos3, size);
        LogEvent event4 = mockEventForLogPos(pos4, size);
        Mockito.when(decoder.decode(Mockito.any(), Mockito.any(), Mockito.any())).thenCallRealMethod();
        Mockito.when(decoder.innerDecode(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(event1, event2, event3, event4);
        LogContext lc = new LogContext();
        lc.setLogPosition(new LogPosition("test", pos1));
        LogBuffer buffer = Mockito.mock(LogBuffer.class);
        decoder.decode(buffer, event1.getHeader(), lc);
        Assert.assertEquals(pos1, lc.getLogPosition().getPosition());
        decoder.decode(buffer, event2.getHeader(), lc);
        //base = pos1
        Assert.assertEquals(200, lc.getLogPosition().getPosition());
        decoder.decode(buffer, event3.getHeader(), lc);
        //base = pos1
        Assert.assertEquals(300, lc.getLogPosition().getPosition());
        decoder.decode(buffer, event4.getHeader(), lc);
        //base = pos1 + pos3
        Assert.assertEquals(400, lc.getLogPosition().getPosition());
    }
}
