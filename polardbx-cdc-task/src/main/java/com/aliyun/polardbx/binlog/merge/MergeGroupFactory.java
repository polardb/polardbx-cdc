/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.storage.Storage;

import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_GROUP_MAX_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_GROUP_UNIT_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

/**
 * created by ziyang.lb
 **/
public class MergeGroupFactory {

    private static final String ROOT_IDENTIFIER = "root";

    public static MergeGroup build(Map<String, MergeSource> mergeSources) {
        int mergeGroupUnitSize = getInt(TASK_MERGE_GROUP_UNIT_SIZE);
        int mergeGroupMaxLevel = Math.max(2, getInt(TASK_MERGE_GROUP_MAX_LEVEL));
        Storage storage = mergeSources.values().stream().findFirst().get().getStorage();

        if (mergeSources.size() <= mergeGroupUnitSize) {
            MergeGroup rootMergeGroup = new MergeGroup(ROOT_IDENTIFIER, storage);
            for (Map.Entry<String, MergeSource> entry : mergeSources.entrySet()) {
                rootMergeGroup.addMergeSource(entry.getKey(), entry.getValue());
            }
            return rootMergeGroup;
        } else {
            int x = mergeSources.size() / mergeGroupUnitSize;
            int y = mergeSources.size() % mergeGroupUnitSize;
            int z = x + (y > 0 ? 1 : 0);

            MergeGroup[] mergeGroups = new MergeGroup[z];
            for (int i = 0; i < z; i++) {
                mergeGroups[i] = new MergeGroup("1-" + (i + 1), storage);
            }

            int count = 0;
            for (Map.Entry<String, MergeSource> entry : mergeSources.entrySet()) {
                int index = count % z;
                mergeGroups[index].addMergeSource(entry.getKey(), entry.getValue());
                count++;
            }

            return build(mergeGroups, 2, storage, mergeGroupUnitSize, mergeGroupMaxLevel);
        }
    }

    private static MergeGroup build(MergeGroup[] mergeGroups, int level, Storage storage, int mergeGroupUnitSize,
                                    int mergeGroupMaxLevel) {
        if (mergeGroups.length <= mergeGroupUnitSize || level >= mergeGroupMaxLevel) {
            MergeGroup rootMergeGroup = new MergeGroup(ROOT_IDENTIFIER, storage);
            for (MergeGroup mergeGroup : mergeGroups) {
                rootMergeGroup.addMergeGroup(mergeGroup);
            }
            return rootMergeGroup;
        } else {
            int x = mergeGroups.length / mergeGroupUnitSize;
            int y = mergeGroups.length % mergeGroupUnitSize;
            int z = x + (y > 0 ? 1 : 0);

            MergeGroup[] mergeGroupArray = new MergeGroup[z];
            for (int i = 0; i < z; i++) {
                mergeGroupArray[i] = new MergeGroup(level + "-" + (i + 1), storage);
            }

            int count = 0;
            for (MergeGroup mergeGroup : mergeGroups) {
                int index = count % z;
                mergeGroupArray[index].addMergeGroup(mergeGroup);
                count++;
            }

            return build(mergeGroupArray, level + 1, storage, mergeGroupUnitSize, mergeGroupMaxLevel);
        }
    }
}
