/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_GROUP_MAX_LEVEL;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_GROUP_UNIT_SIZE;

@Slf4j
public class MergeGroupFactoryTest extends BaseTest {

    @Test
    public void testBuild() {
        doTest(41, 2, Integer.MAX_VALUE);
        doTest(41, 1, 2);
        doTest(900, 617, Integer.MAX_VALUE);

        // random test
        log.info("random test start : " + new Date());
        for (int i = 0; i < 500; i++) {
            int totalLeafSize = RandomUtils.nextInt(2, 1025);
            int mergeGroupUnitSize = RandomUtils.nextInt(2, 1025);
            int mergeGroupMaxLevel = RandomUtils.nextBoolean() ? RandomUtils.nextInt(2, 65) : Integer.MAX_VALUE;
            doTest(totalLeafSize, mergeGroupUnitSize, mergeGroupMaxLevel);
            log.info("random test round " + (i + 1));
        }
        log.info("random test end : " + new Date());
    }

    private void doTest(int totalLeafSize, int mergeGroupUnitSize, int mergeGroupMaxLevel) {
        try {
            mockConfig(TASK_MERGE_GROUP_UNIT_SIZE, String.valueOf(mergeGroupUnitSize));
            mockConfig(TASK_MERGE_GROUP_MAX_LEVEL, String.valueOf(mergeGroupMaxLevel));

            Map<String, MergeSource> mergeSources = new HashMap<>();
            for (int i = 0; i < totalLeafSize; i++) {
                MergeSource mergeSource = new MergeSource("source" + i, null);
                mergeSources.put("source" + i, mergeSource);
            }

            MergeGroup mergeGroup = MergeGroupFactory.build(mergeSources);
            checkResult(mergeGroup, totalLeafSize, mergeGroupUnitSize, mergeGroupMaxLevel);
        } catch (Throwable t) {
            log.error("test error, with parameter : totalLeafSize {}, mergeGroupUnitSize {}, mergeGroupMaxLevel {}",
                totalLeafSize, mergeGroupUnitSize, mergeGroupMaxLevel);
            throw t;
        }
    }

    private void checkResult(MergeGroup mergeGroup, int expectTotalLeafSize, int mergeGroupUnitSize,
                             long mergeGroupMaxLevel) {
        // check total leaf size and level
        AtomicInteger totalLeafSize = new AtomicInteger(0);
        calcLeafSize(mergeGroup, totalLeafSize);
        Assert.assertEquals(expectTotalLeafSize, totalLeafSize.get());

        int totalHeight = height(mergeGroup);
        Assert.assertTrue("calc total height is " + totalHeight + " , max level is " + mergeGroupMaxLevel,
            totalHeight <= (mergeGroupMaxLevel + 1));

        // check merge group unit size if max level is Integer.MAX_VALUE
        if (mergeGroupMaxLevel == Integer.MAX_VALUE) {
            checkMergeGroupUnitSize(mergeGroup, mergeGroupUnitSize);
        }
    }

    private void checkMergeGroupUnitSize(MergeGroup mergeGroup, int mergeGroupUnitSize) {
        if (mergeGroup.getDirectMergeSource() == null) {
            Assert.assertTrue(
                "actual merge group size : " + mergeGroup.getMergeGroupMap().size() + ", input merge group size : "
                    + mergeGroupUnitSize, mergeGroup.getMergeGroupMap().size() <= mergeGroupUnitSize);

            for (MergeGroup item : mergeGroup.getMergeGroupMap().values()) {
                checkMergeGroupUnitSize(item, mergeGroupUnitSize);
            }
        }
    }

    private void calcLeafSize(MergeGroup mergeGroup, AtomicInteger totalLeafSize) {
        for (MergeGroup item : mergeGroup.getMergeGroupMap().values()) {
            if (item.getDirectMergeSource() != null) {
                totalLeafSize.incrementAndGet();
            } else {
                calcLeafSize(item, totalLeafSize);
            }
        }
    }

    private int height(MergeGroup mergeGroup) {
        if (mergeGroup == null) {
            return 0;
        }
        if (mergeGroup.getMergeGroupMap() == null || mergeGroup.getDirectMergeSource() != null) {
            return 1;
        }

        if (mergeGroup.getMergeGroupMap().size() == 1) {
            return 1;
        }

        int maxHeight = 0;
        for (MergeGroup child : mergeGroup.getMergeGroupMap().values()) {
            int childHeight = height(child);
            if (childHeight > maxHeight) {
                maxHeight = childHeight;
            }
        }
        return maxHeight + 1;  // 当前节点的高度为其子树最大高度 + 1
    }
}
