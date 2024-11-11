/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log.processor;

import org.junit.Assert;
import org.junit.Test;

public class FilterBlacklistTableFilterTest {

    @Test
    public void test() {
        FilterBlacklistTableFilter filter = new FilterBlacklistTableFilter("cdc_blacklist_db.*\\.cdc_black_table.*");
        boolean flag = filter.doFilter1("cdc_blacklist_db", "cdc_black_table");
        Assert.assertTrue(flag);
    }
}
