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

package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author shicai.xsc 2020/12/29 15:13
 * @since 5.0.0.0
 */
public enum ServiceStatus {
    /*
    not used
    * */
    NULL(0),

    READY(10),

    RUNNING(20),

    STOPPED(30),

    FINISHED(40),

    DEPRECATED(50);

    private int value;

    public int getValue() {
        return value;
    }

    public String getName() {
        return this.name();
    }

    ServiceStatus(int value) {
        this.value = value;
    }

    public static ServiceStatus from(int value) {
        for (ServiceStatus i : ServiceStatus.values()) {
            if (i.value == value) {
                return i;
            }
        }
        return NULL;
    }

    public static String nameFrom(int value) {
        for (ServiceStatus i : ServiceStatus.values()) {
            if (i.value == value) {
                return i.name();
            }
        }
        return "";
    }
}