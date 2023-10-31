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
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import org.junit.Assert;
import org.junit.Test;

public class SqlModeUtilTest {

    @Test
    public void getStrictModeTest1() {
        int sqlMode = 4194304;
        String sqlModeString = SqlModeUtil.convertSqlMode(sqlMode);
        Assert.assertEquals("STRICT_ALL_TABLES",
            sqlModeString);
    }

    @Test
    public void getStrictModeTest2() {
        int sqlMode = 1075838976;
        String sqlModeString = SqlModeUtil.convertSqlMode(sqlMode);
        Assert.assertEquals("NO_ENGINE_SUBSTITUTION,STRICT_TRANS_TABLES",
            sqlModeString);
    }
}
