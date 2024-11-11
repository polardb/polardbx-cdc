/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ValidationCoordinator test
 *
 * @author siyu.yusi
 */

@Ignore
@Slf4j
public class ValidationCoordinatorTest extends RplWithGmsTablesBaseTest {

//    @Test
//    public void testValidationCoordinator() {
//        // insert validation record
//        // create table for src and dst
//        // insert diff data
//        ValidationCoordinator coordinator = new ValidationCoordinator(getContext());
//        coordinator.validateTable();
//    }
//
//    @Test
//    public void testCrossCheckValidationCoor() {
//        ValidationCoordinator coordinator = new ValidationCoordinator(getCrossCheckValContext());
//        coordinator.validateTable();
//    }

//    private ValidationContext getContext() {
//        ValidationContext context = new ValidationContext();
//        try {
//            context.setSrcDs(srcDataSource);
//            context.setDstDs(dstDataSource);
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
//        } catch (Exception e) {
//            log.error("Error creating validationContext", e);
//        }
//        return context;
//    }
//
//    private ValidationContext getCrossCheckValContext() {
//        ValidationContext context = new ValidationContext();
//        try {
//            context.setSrcDs(srcDataSource);
//            context.setDstDs(dstDataSource);
//            context.setStateMachineId("1");
//            context.setServiceId("1");
//            context.setTaskId("1");
//            context.setSrcLogicalDB("mytest");
//            context.setDstLogicalDB("mytest");
//
//            List<TableInfo> tableList = new ArrayList<>();
//            String srcPhyTable = "accounts_pk_test";
//            TableInfo tableInfo =
//                DbMetaManager.getTableInfo(srcDataSource, context.getSrcLogicalDB(), srcPhyTable, HostType.RDS, false);
//            tableList.add(tableInfo);
//            context.setSrcLogicalTableList(tableList);
//            // set up dst mapping table
//            String dstTable = "accounts_pk_test";
//            Map<String, TableInfo> mappingTable = new HashMap<>();
//            TableInfo dstTableInfo =
//                DbMetaManager.getTableInfo(dstDataSource, context.getDstLogicalDB(), dstTable, HostType.RDS, false);
//            mappingTable.put(srcPhyTable, dstTableInfo);
//            context.setMappingTable(mappingTable);
//            context.setChunkSize(1000);
//            context.setType(ValidationTypeEnum.BACKWARD);
//            context.setValSQLGenerator(ValSQLGenerator.builder().ctx(context).build());
//            context.setRepository(new ValidationTaskRepository(context));
//        } catch (Exception e) {
//            log.error("Error creating validationContext", e);
//        }
//        return context;
//    }
}
