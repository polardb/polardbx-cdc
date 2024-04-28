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
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import lombok.extern.slf4j.Slf4j;

/**
 * ReconCoordinator test
 *
 * @author siyu.yusi
 */
@Slf4j
public class ReconCoordinatorTest extends RplWithGmsTablesBaseTest {
//
//    @Test
//    public void testReconCoordinator() throws Exception {
//
//        // create table for src and dst
//        // insert diff data
//        H2Util.execUpdate(srcDataSource, "create SCHEMA if not exists recontest1");
//        H2Util.execUpdate(dstDataSource, "create SCHEMA if not exists recontest2");
//        H2Util.execUpdate(srcDataSource, "CREATE TABLE IF NOT EXISTS recontest1.test (\n"
//            + "    `id1` bigint(20) unsigned NOT NULL,\n"
//            + "    `id2` bigint(20) unsigned NOT NULL DEFAULT '0',\n"
//            + "    PRIMARY KEY (`id1`)\n"
//            + "    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;");
//        H2Util.execUpdate(dstDataSource, "CREATE TABLE IF NOT EXISTS recontest2.test (\n"
//            + "    `id1` bigint(20) unsigned NOT NULL,\n"
//            + "    `id2` bigint(20) unsigned NOT NULL DEFAULT '0',\n"
//            + "    PRIMARY KEY (`id1`)\n"
//            + "    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;");
//        H2Util.execUpdate(srcDataSource, "insert into recontest1.test values (1,1)");
//        H2Util.execUpdate(srcDataSource, "insert into recontest1.test values (2,2)");
//        H2Util.execUpdate(dstDataSource, "insert into recontest2.test values (1,2)");
//
//        // data fix
//        ReconCoordinator coordinator = new ReconCoordinator(getContext());
//        coordinator.reconFullLoad();
//
//        // check data fix
//        List<List<Serializable>> results = H2Util.getStringData(dstDataSource,
//            "select * from recontest2.test order by id1", 2);
//        Assert.assertEquals(results.size(), 2);
//        Assert.assertEquals(results.get(0).get(0), "1");
//        Assert.assertEquals(results.get(0).get(1), "1");
//        Assert.assertEquals(results.get(1).get(0), "2");
//        Assert.assertEquals(results.get(1).get(1), "2");
//    }
//
//    private ValidationContext getContext() {
//        ValidationContext context = new ValidationContext();
//        try {
//            context.setSrcDs(srcDataSource);
//            context.setDstDs(dstDataSource);
//
//            context.setStateMachineId("1");
//            context.setServiceId("1");
//            context.setTaskId("1");
//            context.setSrcLogicalDB("recontest1");
//            context.setDstLogicalDB("recontest2");
//
//            List<TableInfo> tableList = new ArrayList<>();
//            String srcPhyTable = "test";
//            TableInfo srcTableInfo = DbMetaManager.getTableInfo(srcDataSource, context.getSrcLogicalDB(), srcPhyTable,
//                HostType.RDS, false);
//            srcTableInfo.getColumns().add(new ColumnInfo("id1", 0, null, false,
//                false));
//            srcTableInfo.getColumns().add(new ColumnInfo("id2", 0, null, false,
//                false));
//            tableList.add(srcTableInfo);
//            context.setSrcLogicalTableList(tableList);
//            // set up dst mapping table
//            String dstTable = "test";
//            Map<String, TableInfo> mappingTable = new HashMap<>();
//            TableInfo dstTableInfo =
//                DbMetaManager.getTableInfo(dstDataSource, context.getDstLogicalDB(), dstTable, HostType.RDS,
//                    false);
//            dstTableInfo.getColumns().add(new ColumnInfo("id1", 0, null, false,
//                false));
//            dstTableInfo.getColumns().add(new ColumnInfo("id2", 0, null, false,
//                false));
//            mappingTable.put(srcPhyTable, dstTableInfo);
//            context.setMappingTable(mappingTable);
//            context.setChunkSize(1000);
//            context.setType(ValidationTypeEnum.FORWARD);
//            context.setValSQLGenerator(ValSQLGenerator.builder().ctx(context).build());
//            context.setRepository(new ValidationTaskRepository(context));
//            context.getRepository().createValTasks(ValidationTypeEnum.FORWARD);
//
//            List<Serializable> v1 = new ArrayList<>(1);
//            List<Serializable> v2 = new ArrayList<>(1);
//            v1.add(1);
//            v2.add(2);
//            Record record1 = Record.builder().valList(v1).build();
//            Record record2 = Record.builder().valList(v2).build();
//            List<Record> keyRowValList = new ArrayList<>(2);
//            keyRowValList.add(record1);
//            keyRowValList.add(record2);
//            context.getRepository().persistDiffRows(srcTableInfo, keyRowValList);
//
//        } catch (Exception e) {
//            log.error("Error creating validationContext", e);
//        }
//        return context;
//    }

}
