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
package com.aliyun.polardbx.binlog.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Scanner;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.util.CommonUtils.getActualTso;
import static com.aliyun.polardbx.binlog.util.CommonUtils.getCurrentStackTrace;
import static com.aliyun.polardbx.binlog.util.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.util.CommonUtils.getTsoTimestamp;
import static com.aliyun.polardbx.binlog.util.CommonUtils.tso2physicalTime;

public class CommonUtilsTest {

    @Test
    public void testEscape() {
        String s1 = "ab``ab";
        String s2 = "ab`ab";
        String s3 = "ab```ab";
        String s4 = "ab````ab";
        String s5 = "abab";

        Assert.assertEquals(CommonUtils.escape(s1), s1);
        Assert.assertEquals(CommonUtils.escape(s2), "ab``ab");
        Assert.assertEquals(CommonUtils.escape(s3), s3);
        Assert.assertEquals(CommonUtils.escape(s4), s4);
        Assert.assertEquals(CommonUtils.escape(s5), s5);
    }

    @Test
    @Ignore
    public void testTso2Datetime() {
        long seconds = tso2physicalTime(6829959026120614912L, TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils
            .format(seconds * 1000, "yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("utc")));
    }

    @Test
    @Ignore
    public void testGetTsoDatetime() {
        long seconds = getTsoPhysicalTime("709459645764626028816256616724818862080000000010991244", TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils.format(seconds * 1000, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void testGetActualTso() {
        String actualTso = getActualTso("700984129973806700815409065152909189120000000000000000");
        System.out.println(actualTso);
        Assert.assertEquals("70098412997380670081540906515290918912", actualTso);
    }

    @Test
    public void testGetTsoTimestamp() {
        long tsoTimestamp = getTsoTimestamp("683872748963535353613697927051770675200000000000282415");
        Assert.assertEquals(6838727489635353536L, tsoTimestamp);
    }

    @Test
    public void testIsRealTsoTrans() {
        String s1 = "683872748963535353613697927051770675200000000000282415";
        String s2 = "683872748963535353613697927051770675200000000001282415";
        Assert.assertTrue(CommonUtils.isTsoPolicyTrans(s1));
        Assert.assertFalse(CommonUtils.isTsoPolicyTrans(s2));
    }

    @Test
    public void testGetCurrentStackTrace() {
        String stackTrace = getCurrentStackTrace();
        Scanner scanner = new Scanner(stackTrace);
        int count = 0;
        while (scanner.hasNextLine()) {
            count++;
            String line = scanner.nextLine();
            if (count == 2) {
                Assert.assertTrue(
                    StringUtils.startsWith(line, "com.aliyun.polardbx.binlog.util.CommonUtils.getCurrentStackTrace"));
            }
        }
    }
}
