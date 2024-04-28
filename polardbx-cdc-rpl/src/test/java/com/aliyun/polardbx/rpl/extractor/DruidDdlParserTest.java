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
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class DruidDdlParserTest {
    @Test
    public void testForeignKey() {
        String sql1 = "ALTER TABLE `res_catalog`\n"
            + "  COMMENT = ''";
        String sql2 = "ALTER TABLE res_catalog";
        String sql3 = "ALTER TABLE `device` ADD CONSTRAINT FOREIGN KEY (`b`) REFERENCES `user2` (`a`)";
        DdlResult result1 = DruidDdlParser.parse(sql1, "abc");
        DdlResult result2 = DruidDdlParser.parse(sql2, "abc");
        DdlResult result3 = DruidDdlParser.parse(sql3, "abc");
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotNull(result3.getType());
    }

    @Test
    public void testDropTableIfExists() {
        // see https://aone.alibaba-inc.com/v2/project/860366/bug/55137024
        String sql = "DROP TABLE IF EXISTS d1.d2.rename_target_auto";
        DdlResult ddlResult = DruidDdlParser.parse(sql, "abc");
        Assert.assertEquals("d1", ddlResult.getSchemaName());
        Assert.assertEquals("rename_target_auto", ddlResult.getTableName());
    }
}
