/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST_REGEX;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;

/**
 * @author yudong
 * @since 2023/4/24 21:11
 **/
public class HashConfigTest extends BaseTestWithGmsTables {

    @Test
    public void testTableRegexList() {
        setConfig(BINLOGX_TRANSMIT_HASH_LEVEL, "RECORD");
        setConfig(BINLOGX_STREAM_COUNT, "3");
        setConfig(BINLOGX_TABLE_LEVEL_HASH_TABLE_LIST_REGEX,
            ".*\\.modify_im_pk_test_tbl1,.*\\.modify_pk_with_insert_1,.*\\.modify_sk_simple_checker_test_tbl");
        HashLevel actual = HashConfig.getHashLevel("", "modify_im_pk_test_tbl1");
        Assert.assertEquals(HashLevel.TABLE, actual);
        actual = HashConfig.getHashLevel("", "modify_pk_with_insert_1");
        Assert.assertEquals(HashLevel.TABLE, actual);
        actual = HashConfig.getHashLevel("", "modify_sk_simple_checker_test_tbl");
        Assert.assertEquals(HashLevel.TABLE, actual);

        actual = HashConfig.getHashLevel("test_db0", "modify_im_pk_test_tbl1");
        Assert.assertEquals(HashLevel.TABLE, actual);
        actual = HashConfig.getHashLevel("test_db1", "modify_pk_with_insert_1");
        Assert.assertEquals(HashLevel.TABLE, actual);
        actual = HashConfig.getHashLevel("test_db2", "modify_sk_simple_checker_test_tbl");
        Assert.assertEquals(HashLevel.TABLE, actual);
    }
}
