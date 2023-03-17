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
package com.aliyun.polardbx.binlog;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.CommonUtils.getTsoTimestamp;
import static com.aliyun.polardbx.binlog.CommonUtils.tso2physicalTime;

public class CommonUtilsTest {

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
        long seconds = getTsoPhysicalTime("703394672213701465615650119374759608330000000000127207", TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils.format(seconds * 1000, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void testGetActualTso() {
        Long actualTso = getTsoTimestamp("700984129973806700815409065152909189120000000000000000");
        System.out.println(actualTso);
        Assert.assertEquals("68305528648556871681361618080408547329", actualTso);
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
}
