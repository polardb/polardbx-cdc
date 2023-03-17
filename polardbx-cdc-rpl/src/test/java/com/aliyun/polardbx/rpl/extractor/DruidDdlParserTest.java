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
package com.aliyun.polardbx.rpl.extractor;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DruidDdlParserTest {
    @Test
    public void DruidDdlParserTest() {
        String sql1 = "ALTER TABLE `res_catalog`\n"
            + "  COMMENT = ''";
        String sql2 = "ALTER TABLE res_catalog";
        DdlResult result1 = DruidDdlParser.parse(sql1, "abc").get(0);
        List<DdlResult> resultList2 = DruidDdlParser.parse(sql2, "abc");
        assertNotNull(result1);
        assertNotNull(resultList2);
        assertTrue(resultList2.isEmpty());
    }
}