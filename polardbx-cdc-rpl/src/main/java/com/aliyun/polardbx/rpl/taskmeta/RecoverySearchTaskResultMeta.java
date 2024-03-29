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
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * created by ziyang.lb
 **/
@Data
public class RecoverySearchTaskResultMeta {
    long sqlCounter;
    int injectTroubleCount;
    Map<String, String> fileMd5Map;
    Map<String, Long> fileSizeMap;

    public RecoverySearchTaskResultMeta() {
        this.sqlCounter = 0;
        this.fileMd5Map = new HashMap<>();
        this.fileSizeMap = new HashMap<>();
    }
}
