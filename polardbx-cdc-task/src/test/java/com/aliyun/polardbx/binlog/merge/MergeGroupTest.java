/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.extractor.Extractor;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class MergeGroupTest extends BaseTest {

    @Test
    public void testParallelStartMergeGroup() {
        MergeGroup mergeGroup = new MergeGroup("mergeGroup1", null);
        List<MergeSource> mergeSourceList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MergeSource mergeSource = new MergeSource("source" + i, null);
            mergeSource.setExtractor(Mockito.mock(Extractor.class));
            mergeGroup.addMergeSource("mergeSource1" + i, mergeSource);
            mergeSourceList.add(mergeSource);
        }

        mergeGroup.parallelStartMergeGroup();
        mergeSourceList.forEach(mergeSource -> {
            Assert.assertTrue(mergeSource.isRunning());
        });
    }
}
