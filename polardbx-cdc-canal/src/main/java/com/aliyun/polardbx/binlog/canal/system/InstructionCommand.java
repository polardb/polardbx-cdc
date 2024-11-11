/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.system;

import com.aliyun.polardbx.binlog.InstructionType;
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
