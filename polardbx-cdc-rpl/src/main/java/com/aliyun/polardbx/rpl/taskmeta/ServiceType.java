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
 * @author shicai.xsc 2020/12/8 14:04
 * @since 5.0.0.0
 */
public enum ServiceType {
    INC_COPY(101),

    REPLICA(3),

    FULL_COPY(100),

    FULL_VALIDATION(200),

    REVISE(201),

    REVISE_CHECK(202),

    CDC_INC(300),

    REC_SEARCH(301),

    REC_COMBINE(302);

    private int value;

    ServiceType(int value) {
        this.value = value;
    }

    public static ServiceType from(int value) {
        for (ServiceType i : ServiceType.values()) {
            if (i.getValue() == value) {
                return i;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}
