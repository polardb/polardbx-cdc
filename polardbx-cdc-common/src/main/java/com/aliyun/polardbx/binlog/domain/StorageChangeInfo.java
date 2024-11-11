/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class StorageChangeInfo {
    private String instructionId;
    private StorageChangeEntity storageChangeEntity;

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public StorageChangeEntity getStorageChangeEntity() {
        return storageChangeEntity;
    }

    public void setStorageChangeEntity(StorageChangeEntity storageChangeEntity) {
        this.storageChangeEntity = storageChangeEntity;
    }

    public static class StorageChangeEntity {
        private List<String> storageInstList;

        public List<String> getStorageInstList() {
            return storageInstList;
        }

        public void setStorageInstList(List<String> storageInstList) {
            this.storageInstList = storageInstList;
        }
    }
}
