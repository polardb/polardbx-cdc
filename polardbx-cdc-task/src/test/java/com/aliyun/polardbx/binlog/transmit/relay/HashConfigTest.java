/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
