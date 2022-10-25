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
package com.aliyun.polardbx.binlog.cdc.topology;

public enum TableType {
    SINGLE(0), SHARDING(1), BROADCAST(2), GSI(3);

    private final int value;

    TableType(int value) {
        this.value = value;
    }

    public static TableType from(int value) {
        switch (value) {
        case 0:
            return SINGLE;
        case 1:
            return SHARDING;
        case 2:
            return BROADCAST;
        case 3:
            return GSI;
        default:
            return null;
        }
    }

    public int getValue() {
        return value;
    }

    public boolean isPrimary() {
        return GSI != this;
    }
}
