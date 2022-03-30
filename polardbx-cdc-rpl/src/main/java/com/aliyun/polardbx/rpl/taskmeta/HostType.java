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
 * @author shicai.xsc 2020/12/29 16:07
 * @since 5.0.0.0
 */
public enum HostType {
    RDS(10),

    MYSQL(15),

    POLARX2(30);

    private int value;

    public int getValue() {
        return value;
    }

    HostType(int value) {
        this.value = value;
    }

    public static HostType from(int state) {
        for (HostType i : HostType.values()) {
            if (i.value == state) {
                return i;
            }
        }
        return null;
    }
}