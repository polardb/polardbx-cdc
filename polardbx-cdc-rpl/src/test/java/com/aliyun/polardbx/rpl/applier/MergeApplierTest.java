/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/5/18 21:38
 * @since 5.0.0.0
 */
public class MergeApplierTest extends TestBase {
    private MergeApplier applier;
    private Map<String, Map<RowKey, DefaultRowChange>> insertRowChanges;
    private Map<String, Map<RowKey, DefaultRowChange>> deleteRowChanges;
    private List<DBMSEvent> dbmsEvents;
    private HostInfo hostInfo;
    private TableInfo tableInfo;

    private final static String SCHEMA = "schema_1";
    private final static String TABLE = "table_1";
    private final static String TB_KEY = SCHEMA + "." + TABLE;

    @Before
    public void init() {
        applier = new MergeApplier(null, hostInfo);
        insertRowChanges = new HashMap<>();
        deleteRowChanges = new HashMap<>();
        dbmsEvents = new ArrayList<>();

        hostInfo = new HostInfo();
        hostInfo.setType(HostType.POLARX2);

        tableInfo = new TableInfo(SCHEMA, TABLE);

        Map<String, TableInfo> tableInfos = new HashMap<>();
        tableInfos.put(TB_KEY, tableInfo);
        applier.dbMetaCache = new DbMetaCache(hostInfo, 10);
        applier.dbMetaCache.setTableInfos(tableInfos);
    }

//    @Test
//    public void pk_NoChangePk() throws Throwable {
//
////        applier.getMergeDmlSqlContexts()
//
//        // I + D
//
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_ChangePk() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_shardKey_ChangePk() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_shardKey_ChangeShardKey() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_shardKey_ChangePk_ChangeShardKey() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_uk_ChangePk() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_uk_ChangeUk() throws Throwable {
//        applier.mergeByTable(dbmsEvents, insertRowChanges, deleteRowChanges);
//    }
//
//    @Test
//    public void pk_uk_shardKey() throws Throwable {
//
//    }

    private DefaultRowChange createRowChange(DBMSAction action, int f0, int f1, int f2, int f3) {
        return createRowChange(SCHEMA, TABLE, action, f0, f1, f2, f3, -1, -1, -1, -1);
    }
}
