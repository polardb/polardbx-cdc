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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.util.FastSQLConstant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class PolarDbXStorageTableMetaTest {
    SpringContextBootStrap appContextBootStrap;
    Gson gson = new GsonBuilder().create();

    @Before
    public void init() {
        System.setProperty("taskName", "Final");
        appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void applyHistory() {

        String ddl =
            "create or replace algorithm=undefined definer=`admin`@`%` sql security definer view `v` as select * from select_base_two_one_db_one_tb";

        System.out.println(ddl);

        List<SQLStatement> statements = SQLUtils.parseStatements(ddl, DbType.mysql, FastSQLConstant.FEATURES);
        String reformatDDL = statements.get(0).toString();
        System.out.println(reformatDDL);

        List<SQLStatement> statements1 = SQLUtils.parseStatements(reformatDDL, DbType.mysql, FastSQLConstant.FEATURES);
//        SQLStatementParser parser =
//            SQLParserUtils.createSQLStatementParser(ddl, DbType.mysql, FastSQLConstant.FEATURES);
//        List<SQLStatement> statementList = parser.parseStatementList();
//        SQLStatement st = statementList.get(0);
//        System.out.println(st);
//        PolarDbXStorageTableMeta tableMeta = new PolarDbXStorageTableMeta("", null, new TopologyManager());
//        tableMeta.apply(new BinlogPosition("000", "000"), "andor_qatest_polarx1", ddl, null);
    }
}
