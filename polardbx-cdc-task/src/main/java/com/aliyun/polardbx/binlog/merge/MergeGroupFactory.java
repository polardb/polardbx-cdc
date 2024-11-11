/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
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
    private static final int MERGE_GROUP_UNIT_SIZE = getInt(TASK_MERGE_GROUP_UNIT_SIZE);
    private static final int MERGE_GROUP_MAX_LEVEL = Math.max(2, getInt(TASK_MERGE_GROUP_MAX_LEVEL));
    private static final String ROOT_IDENTIFIER = "root";

    public static MergeGroup build(Map<String, MergeSource> mergeSources) {
        Storage storage = mergeSources.values().stream().findFirst().get().getStorage();

        if (mergeSources.size() <= MERGE_GROUP_UNIT_SIZE) {
            MergeGroup rootMergeGroup = new MergeGroup(ROOT_IDENTIFIER, storage);
            for (Map.Entry<String, MergeSource> entry : mergeSources.entrySet()) {
                rootMergeGroup.addMergeSource(entry.getKey(), entry.getValue());
            }
            return rootMergeGroup;
        } else {
            int x = mergeSources.size() / MERGE_GROUP_UNIT_SIZE;
            int y = mergeSources.size() % MERGE_GROUP_UNIT_SIZE;
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

            return build(mergeGroups, 2, storage);
        }
    }

    private static MergeGroup build(MergeGroup[] mergeGroups, int level, Storage storage) {
        if (mergeGroups.length <= MERGE_GROUP_UNIT_SIZE || level >= MERGE_GROUP_MAX_LEVEL) {
            MergeGroup rootMergeGroup = new MergeGroup(ROOT_IDENTIFIER, storage);
            for (MergeGroup mergeGroup : mergeGroups) {
                rootMergeGroup.addMergeGroup(mergeGroup);
            }
            return rootMergeGroup;
        } else {
            int x = mergeGroups.length / MERGE_GROUP_UNIT_SIZE;
            int y = mergeGroups.length % MERGE_GROUP_UNIT_SIZE;
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

            return build(mergeGroupArray, level + 1, storage);
        }
    }
}
