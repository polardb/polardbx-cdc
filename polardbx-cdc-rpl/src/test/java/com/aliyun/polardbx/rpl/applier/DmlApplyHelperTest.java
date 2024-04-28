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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.extractor.full.RowChangeBuilder;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DmlApplyHelperTest extends RplWithGmsTablesBaseTest {

    @Test
    public void getDeleteThenReplaceDml() throws Exception {

        H2Util.execUpdate(srcDataSource, "create SCHEMA if not exists testdb");
        H2Util.execUpdate(srcDataSource, "CREATE TABLE IF NOT EXISTS testdb.testtable (\n"
            + "    `id1` bigint(20) unsigned NOT NULL,\n"
            + "    `id2` bigint(20) unsigned NOT NULL DEFAULT '0',\n"
            + "    PRIMARY KEY (`id1`)\n"
            + "    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;");

        TableInfo tableInfo = DbMetaManager.getTableInfo(srcDataSource, "testdb", "testtable",
            HostType.RDS, false);
        tableInfo.getColumns().add(new ColumnInfo("id1", 0, null, false,
            false, "", 0));
        tableInfo.getColumns().add(new ColumnInfo("id2", 0, null, false,
            false, "", 0));

        RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, "testdb", "testtable",
            DBMSAction.UPDATE);
        Map<String, Serializable> fieldValueMap = new HashMap<>(tableInfo.getColumns().size());
        for (ColumnInfo column : tableInfo.getColumns()) {
            Serializable value = "1";
            fieldValueMap.put(column.getName(), value);
        }
        builder.addRowData(fieldValueMap);

        Map<String, Serializable> fieldValueMapChange = new HashMap<>(tableInfo.getColumns().size());
        for (ColumnInfo column : tableInfo.getColumns()) {
            Serializable value = "2";
            fieldValueMapChange.put(column.getName(), value);
        }
        builder.addChangeRowData(fieldValueMapChange);
        DefaultRowChange rowChange = builder.build();

        List<SqlContext> sqlContexts = DmlApplyHelper.getDeleteThenReplaceSqlExecContext(rowChange, tableInfo);
        log.info(sqlContexts.get(0).toString());
        log.info(sqlContexts.get(1).toString());
        Assert.assertEquals(sqlContexts.size(), 2);
        // random compare all make sqlContexts.get(0).params.size() 1 or 2
        // Assert.assertEquals(sqlContexts.get(0).params.size(), 1);
        Assert.assertEquals(sqlContexts.get(1).params.size(), 2);
        Assert.assertEquals(sqlContexts.get(0).params.get(0), "1");
        Assert.assertEquals(sqlContexts.get(1).params.get(0), "2");
        Assert.assertEquals(sqlContexts.get(1).params.get(1), "2");
    }
}
