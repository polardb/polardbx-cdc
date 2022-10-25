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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.error.PolardbxException;

public enum FlushPolicy {
    /**
     * 每个事务flush一次
     */
    FlushPerTxn(0),
    /**
     * 定时flush
     */
    FlushAtInterval(1);

    private final int value;

    FlushPolicy(int value) {// 编写枚举的构造函数，这里的构造方法只能是私有的
        this.value = value;
    }

    public static FlushPolicy parseFrom(int value) { // 将数值转换成枚举值
        switch (value) {
        case 0:
            return FlushPerTxn;
        case 1:
            return FlushAtInterval;
        default:
            throw new PolardbxException("invalid flush policy.");
        }
    }

    public int getValue() { // 将枚举值转换成数值
        return this.value;
    }
}
