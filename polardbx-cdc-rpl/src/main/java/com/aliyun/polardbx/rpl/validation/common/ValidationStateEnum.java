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
package com.aliyun.polardbx.rpl.validation.common;

/**
 * Validation state
 *
 * @author siyu.yusi
 */
public enum ValidationStateEnum {
    INIT(101),
    PREPARED(102),
    COMPARING(103),
    DONE(104),
    ERROR(500);

    private final int value;

    ValidationStateEnum(int value) {
        this.value = value;
    }

    public static ValidationStateEnum from(int value) {
        switch (value) {
        case 101:
            return INIT;
        case 102:
            return PREPARED;
        case 103:
            return COMPARING;
        case 104:
            return DONE;
        default:
            return null;
        }
    }

    public int getValue() {
        return value;
    }
}
