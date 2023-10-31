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

import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import org.junit.Test;

import java.util.List;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatementList;
import static org.junit.Assert.assertNotNull;

public class DruidDdlParserTest {

    @Test
    public void DruidDdlParserTest() {
        String sql1 = "ALTER TABLE `res_catalog`\n"
            + "  COMMENT = ''";
        String sql2 =
            "create table if not exists `t_hash_hash_not_template_1690868091444` (a bigint unsigned not null,b bigint unsigned not null,c datetime NOT NULL,d varchar(16) NOT NULL,e varchar(16) NOT NULL)partition by hash (c,d) partitions 2 subpartition by hash (a,b)(  partition p1 subpartitions 2,  partition p2 subpartitions 4)";
        DdlResult result1 = DruidDdlParser.parse(sql1, "abc").get(0);
        List<DdlResult> resultList2 = DruidDdlParser.parse(sql2, "abc");
        List<SQLStatement> stmtList = parseSQLStatementList(sql2);
        String a = stmtList.get(0).toString();
        assertNotNull(result1);
        assertNotNull(resultList2);
    }
}
