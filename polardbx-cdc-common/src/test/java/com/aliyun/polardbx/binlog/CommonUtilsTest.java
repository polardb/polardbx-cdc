/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.base.BaseTest;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CommonUtils.getActualTso;
import static com.aliyun.polardbx.binlog.CommonUtils.getTsoPhysicalTime;
import static com.aliyun.polardbx.binlog.CommonUtils.getTsoTimestamp;
import static com.aliyun.polardbx.binlog.CommonUtils.tso2physicalTime;

public class CommonUtilsTest extends BaseTest {

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
        long seconds = getTsoPhysicalTime("690153487492710406414326000904841543680000000000000000", TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils.format(seconds * 1000, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void testGetActualTso() {
        String actualTso = getActualTso("683055286485568716813616180804085473290000000000048688");
        Assert.assertEquals("68305528648556871681361618080408547329", actualTso);
    }

    @Test
    public void testGetTsoTimestamp() {
        long tsoTimestamp = getTsoTimestamp("683872748963535353613697927051770675200000000000282415");
        Assert.assertEquals(6838727489635353536L, tsoTimestamp);
    }
}
