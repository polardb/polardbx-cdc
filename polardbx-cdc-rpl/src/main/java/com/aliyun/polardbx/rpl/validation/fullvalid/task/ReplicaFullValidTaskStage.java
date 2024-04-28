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
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

/**
 * @author yudong
 * @since 2023/10/18 17:21
 **/
public enum ReplicaFullValidTaskStage {
    INIT("INIT"),
    CHECK("CHECK"),
    REPAIR("REPAIR"),
    SCHEMA_CHECK("SCHEMA_CHECK");

    private final String stageName;

    ReplicaFullValidTaskStage(String stageName) {
        this.stageName = stageName;
    }

    @Override
    public String toString() {
        return stageName;
    }

    public static ReplicaFullValidTaskStage nextStage(ReplicaFullValidTaskStage stage) {
        if (stage == INIT) {
            return CHECK;
        } else if (stage == CHECK) {
            // todo add repair
            return null;
        }
        return null;
    }
}
