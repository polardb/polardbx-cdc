/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitApplierTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testLogSerialExecuteInfo() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        Map<String, List<DefaultRowChange>> map = new HashMap<>();
        map.put("t1", Lists.newArrayList(new DefaultRowChange()));
        splitApplier.logSerialExecuteInfo(map);
        Assert.assertFalse(splitApplier.logSerialExecuteInfo(map));
    }
}
