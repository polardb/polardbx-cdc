/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Test;

import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 *
 **/
public class CommonUtilsTest {

    @Test
    public void testGetTsoPhysicalTime() {
        long seconds = CommonUtils.getTsoPhysicalTime("683441847923029260813654836947808829450000000001114250",
            TimeUnit.SECONDS);
        System.out.println("seconds is :" + seconds);
        System.out.println(new Date(seconds * 1000));
    }

    @Test
    public void testTso2Datetime() {
        long seconds = CommonUtils.tso2physicalTime(6829959026120614912L, TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils
            .format(seconds * 1000, "yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("utc")));
    }

    @Test
    public void testGetTsoDatetime() {
        long seconds = CommonUtils.getTsoPhysicalTime("681932084883116608013503860643882680350000000000754228",
            TimeUnit.SECONDS);
        System.out.println("seconds is :" + DateFormatUtils.format(seconds * 1000, "yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    public void testTest() {
        String actualTso = CommonUtils.getActualTso("683055286485568716813616180804085473290000000000048688");
        System.out.println("actualTso is :" + actualTso);
    }

    @Test
    public void testGetTsoTimestamp() {
        long tsoTimestamp = CommonUtils.getTsoTimestamp("675206541385165747212831306291557171200000000000417494");
        System.out.println("actualTso is :" + tsoTimestamp);
    }
}
