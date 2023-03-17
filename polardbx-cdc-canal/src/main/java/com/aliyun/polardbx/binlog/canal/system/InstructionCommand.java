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
package com.aliyun.polardbx.binlog.canal.system;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class InstructionCommand {
    private final String instructionId;
    private final InstructionType type;
    private final String content;

    public InstructionCommand(String instructionId, InstructionType type,
                              String content) {
        this.instructionId = instructionId;
        this.type = type;
        this.content = content;
    }

    public boolean isCdcStart() {
        return type == InstructionType.CdcStart;
    }

    public boolean isStorageChangeCmd() {
        return type == InstructionType.StorageInstChange;
    }

    public boolean isMetaSnapshotCmd() {
        return type == InstructionType.MetaSnapshot;
    }
}
