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
package com.aliyun.polardbx.binlog.metadata;

import com.alibaba.polardbx.druid.DbType;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import org.junit.Assert;
import org.junit.Test;

public class TableMetaDelegateTest {

    private <T> T parse(String ddl) {
        return (T) SQLUtils.parseStatements(ddl, DbType.mysql).get(0);
    }

    @Test
    public void testDDLDropTable() {
        String ddl = "drop table aaa_A;";
        SQLDropTableStatement statement = parse(ddl);
        for (SQLExprTableSource expr : statement.getTableSources()) {
            Assert.assertEquals("aaa_A", expr.getTableName());
        }
    }

    @Test
    public void testDDLCreateTable() {
        String ddl = "create table test_abcdefgAAA(id int primary key auto_increment, name varchar(20))";
        SQLCreateTableStatement statement = parse(ddl);
        Assert.assertEquals("test_abcdefgAAA", statement.getTableName());
    }

    @Test
    public void testDDLCreateDatabase() {
        String ddl = "create database test_DDD";
        SQLCreateDatabaseStatement statement = parse(ddl);
        Assert.assertEquals("test_DDD", statement.getDatabaseName());
    }

    @Test
    public void testDDLRenameTable() {
        String ddl = "rename table table_aaaA to table_BBBB";
        MySqlRenameTableStatement statement = parse(ddl);
        Assert.assertEquals("table_aaaA", statement.getItems().get(0).getName().getSimpleName());
        Assert.assertEquals("table_BBBB", statement.getItems().get(0).getTo().getSimpleName());
    }
}

