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
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.binlog.client.CdcClient;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.amazonaws.util.StringUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CdcClientTest extends BaseTestCase {

    private static final Logger logger = LoggerFactory.getLogger(CdcClientTest.class);

    private static final long timeoutInMinute = 30;

    private Throwable t;

    @Test
    public void testCdcClient() throws Throwable {
        CdcClient cdcClient = new CdcClient(this::getMetaConnection);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        cdcClient.setExceptionHandler(t -> {
            CdcClientTest.this.t = t;
            logger.error("cdc client dumper error!", t);
            countDownLatch.countDown();
        });

        cdcClient.setBinaryData();
        String endFile;
        long endPos;
        try (Connection polarxConnection = getPolardbxConnection()) {
            Statement st = polarxConnection.createStatement();
            ResultSet rs = st.executeQuery("show master status");
            rs.next();
            endFile = rs.getString(1);
            endPos = rs.getLong(2);
        }
        logger.info(String.format("record end offset %s:%s", endFile, endPos
            + ""));
        String startFile = resetBinlogFileName(endFile);
        cdcClient.startAsync(startFile, 4L, cdcEventData -> {
            int fileOffset = StringUtils.compare(cdcEventData.getBinlogFileName(), endFile);
            if ((fileOffset == 0 && cdcEventData.getPosition() >= endPos) || fileOffset > 0) {
                countDownLatch.countDown();
            }
        });
        logger.info(String.format("cdc client start @ pos %s:%s", startFile, 4L));
        countDownLatch.await(timeoutInMinute, TimeUnit.MINUTES);
        if (t != null) {
            throw t;
        }
    }

    public static String resetBinlogFileName(String fileName) {
        String[] fn = fileName.split("\\.");
        return fn[0] + "." + org.apache.commons.lang3.StringUtils.leftPad("1", fn[1].length(), "0");
    }

    public static int getFileNum(String fileName) {
        String[] fn = fileName.split("\\.");
        return Integer.parseInt(fn[1]);
    }
}
