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
 * @author jiyue 2021/09/06 20:58
 * @since 5.0.0.0
 */
public enum ExtractorType {
    // 用于replica
    RPL_INC(30);


    private int value;

    ExtractorType(int value) {
        this.value = value;
    }

    public static ExtractorType from(int state) {
        for (ExtractorType i : ExtractorType.values()) {
            if (i.value == state) {
                return i;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}
