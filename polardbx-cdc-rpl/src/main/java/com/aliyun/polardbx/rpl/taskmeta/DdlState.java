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

/**
 * @author shicai.xsc 2021/4/6 16:26
 * @since 5.0.0.0
 */
public enum DdlState {
    NOT_START(0),

    RUNNING(10),

    FAILED(20),

    SUCCEED(30);

    private int value;

    public int getValue() {
        return value;
    }

    public boolean isFinished() {
        return this == FAILED || this == SUCCEED;
    }

    DdlState(int value) {
        this.value = value;
    }

    public static DdlState from(int state) {
        for (DdlState i : DdlState.values()) {
            if (i.value == state) {
                return i;
            }
        }
        return null;
    }
}
