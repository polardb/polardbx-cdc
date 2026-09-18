/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.FORMAT_DESCRIPTION_EVENT;

public class FormatDescriptionEventTest extends BaseTest {
    /**
     * 确保formatDescEvent的postHeader被及时更新
     */
    @Test
    @SneakyThrows
    public void testParseFormatDescription() {
        AutoExpandBuffer autoExpandBuffer = new AutoExpandBuffer(1024, 1024);
        FormatDescriptionEvent formatDescriptionEvent = new FormatDescriptionEvent((short) 4, "8.0.32", 1L);
        formatDescriptionEvent.write(autoExpandBuffer);
        byte[] data = new byte[autoExpandBuffer.size()];
        autoExpandBuffer.writeTo(data);
        FormatDescriptionLogEvent event =
            (FormatDescriptionLogEvent) LogDecoder.simpleDecode(data);
        int formatDescPostHeaderLen = event.getPostHeaderLen()[FORMAT_DESCRIPTION_EVENT - 1];
        int formatDescBodyLen = event.getEventLen() - event.getCommonHeaderLen();
        int checksumLen = formatDescBodyLen - formatDescPostHeaderLen;
        // 5: checksumAlg 1 + checksum 4
        Assert.assertEquals(5, checksumLen);
        Assert.assertEquals(LogEvent.BINLOG_CHECKSUM_ALG_CRC32, data[data.length - checksumLen]);
    }
}
