/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.alibaba.polardbx.druid.util.FnvHash;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

public class TxnKeyBuilderTest extends BaseTest {

    @Before
    public void resetAppendXidFlagCache() throws Exception {
        // appendXidFlag 是 static DCL 缓存。如果前置测试已初始化过，本测试会拿到错误值，
        // 需在测试前重置为 null 以验证「首次读取后缓存」的 NeedRestart 语义。
        Field field = TxnKeyBuilder.class.getDeclaredField("appendXidFlag");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    public void testAppendBuilderNeedRestart() {
        mockConfig(ConfigKeys.TASK_EXTRACT_BUILD_PARTITION_APPEND_HASH_XID, "true");
        Assert.assertTrue(TxnKeyBuilder.isAppendXidFlag());
        mockConfig(ConfigKeys.TASK_EXTRACT_BUILD_PARTITION_APPEND_HASH_XID, "false");
        Assert.assertTrue(TxnKeyBuilder.isAppendXidFlag());
        String xid =
            "X'647264732d313936303739623066343430373030314064373332663532353662393163616538',X'52455441494c5f5030303030325f47524f5550',1";
        Pair<Long, String> pair = TxnKeyBuilder.getTransIdGroupIdPair(xid);
        long tid = pair.getLeft();
        Assert.assertEquals(1828595249631490049L, tid);
        Assert.assertEquals("RETAIL_P00002_GROUP@" + FnvHash.hashCode64(xid), pair.getRight());
    }

}
