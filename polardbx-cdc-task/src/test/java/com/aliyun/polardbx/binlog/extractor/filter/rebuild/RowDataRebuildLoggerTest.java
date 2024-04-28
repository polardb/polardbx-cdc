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
package com.aliyun.polardbx.binlog.extractor.filter.rebuild;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 *
 */
public class RowDataRebuildLoggerTest extends BaseTest {

    @Test
    public void testEnum() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        Field field =
            MakeFieldFactory.makeField("enum('x-small','small','medium','large','x-large')", "x-large", "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(5, s);
    }

    @Test
    public void testDecimal() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        float b = 23.1415f;
        Field field =
            MakeFieldFactory.makeField("decimal(10,3)", b + "", "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(new BigDecimal("23.141"), s);
    }

    @Test
    public void testVarchar() {
        setConfig(ConfigKeys.TASK_EXTRACT_REBUILD_DATA_LOG, "true");
        String str = "abcdefgh";
        Field field =
            MakeFieldFactory.makeField("varchar(5)", str, "utf8", false,
                false);
        RowDataRebuildLogger logger = new RowDataRebuildLogger();
        Serializable s =
            logger.doDecode(field.getMysqlType().getType(), field.doGetTableMeta(), field.encode(), "utf8", false);
        Assert.assertEquals(str, s);
    }
}
