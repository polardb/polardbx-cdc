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
package com.aliyun.polardbx.rpl.taskmeta;

public enum FilterType {

    // 无filter， 自行在applier中实现过滤逻辑
    NO_FILTER(0),

    // 标准filter，目前用于data import
    NORMAL_FILTER(10),

    // replica使用的filter
    RPL_FILTER(20);

    private int value;

    public int getValue() {
        return value;
    }

    FilterType(int value) {
        this.value = value;
    }

    public static FilterType from(int state) {
        for (FilterType i : FilterType.values()) {
            if (i.value == state) {
                return i;
            }
        }
        return null;
    }
}

