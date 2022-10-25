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
package com.aliyun.polardbx.rpl.validation;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import com.aliyun.polardbx.rpl.validation.repository.impl.ValTaskRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ValidationCoordinator test
 *
 * @author siyu.yusi
 */

@Slf4j
public class ValidationCoordinatorTest {

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void testValidationCoordinator() {
        ValidationCoordinator coordinator = new ValidationCoordinator(getContext());
        coordinator.validateTable();
    }

    @Test
    public void testCrossCheckValidationCoor() {
        ValidationCoordinator coordinator = new ValidationCoordinator(getCrossCheckValContext());
        coordinator.validateTable();
    }

    private ValidationContext getContext() {
        ValidationContext context = new ValidationContext();
        try {
            Map<String, String> connParams = new HashMap<>();
            DruidDataSource srcDs =
                DataSourceUtil.createDruidMySqlDataSource("192.168.1.1",
                    3306,
                    "mytest",
                    "mytest",
                    "mytest",
                    "",
                    1,
                    4,
                    connParams,
                    null);
            context.setSrcDs(srcDs);

            DruidDataSource dstDs =
                DataSourceUtil.createDruidMySqlDataSource("192.168.1.2",
                    3306,
                    "mytest",
                    "mytest",
                    "mytest",
                    "",
                    1,
                    4,
                    connParams,
                    null);
            context.setDstDs(dstDs);
            context.setStateMachineId("1");
            context.setServiceId("1");
            context.setTaskId("1");
            context.setSrcPhyDB("mytest");
            context.setDstLogicalDB("mytest");

            List<TableInfo> tableList = new ArrayList<>();
            String srcPhyTable = "accounts_pk_test";
            TableInfo tableInfo = DbMetaManager.getTableInfo(srcDs, context.getSrcPhyDB(), srcPhyTable, HostType.RDS);
            tableList.add(tableInfo);
            context.setSrcPhyTableList(tableList);
            // set up dst mapping table
            String dstTable = "accounts_pk_test";
            Map<String, TableInfo> mappingTable = new HashMap<>();
            TableInfo dstTableInfo =
                DbMetaManager.getTableInfo(dstDs, context.getDstLogicalDB(), dstTable, HostType.POLARX2);
            mappingTable.put(srcPhyTable, dstTableInfo);
            context.setMappingTable(mappingTable);
            context.setChunkSize(1000);
            context.setType(ValidationTypeEnum.FORWARD);
            context.setValSQLGenerator(ValSQLGenerator.builder().ctx(context).build());
            context.setRepository(new ValTaskRepositoryImpl(context));
        } catch (Exception e) {
            log.error("Error creating validationContext", e);
        }
        return context;
    }

    private ValidationContext getCrossCheckValContext() {
        ValidationContext context = new ValidationContext();
        try {
            Map<String, String> connParams = new HashMap<>();
            DruidDataSource srcDs =
                DataSourceUtil.createDruidMySqlDataSource("192.168.1.1",
                    3306,
                    "mytest",
                    "mytest",
                    "mytest",
                    "",
                    1,
                    4,
                    connParams,
                    null);
            context.setSrcDs(srcDs);

            DruidDataSource dstDs =
                DataSourceUtil.createDruidMySqlDataSource("192.168.1.2",
                    3306,
                    "mytest",
                    "mytest",
                    "mytest",
                    "",
                    1,
                    4,
                    connParams,
                    null);
            context.setDstDs(dstDs);
            context.setStateMachineId("1");
            context.setServiceId("1");
            context.setTaskId("1");
            context.setSrcPhyDB("mytest");
            context.setDstLogicalDB("mytest");

            List<TableInfo> tableList = new ArrayList<>();
            String srcPhyTable = "accounts_pk_test";
            TableInfo tableInfo =
                DbMetaManager.getTableInfo(srcDs, context.getSrcPhyDB(), srcPhyTable, HostType.POLARX2);
            tableList.add(tableInfo);
            context.setSrcPhyTableList(tableList);
            // set up dst mapping table
            String dstTable = "accounts_pk_test";
            Map<String, TableInfo> mappingTable = new HashMap<>();
            TableInfo dstTableInfo =
                DbMetaManager.getTableInfo(dstDs, context.getDstLogicalDB(), dstTable, HostType.POLARX1);
            mappingTable.put(srcPhyTable, dstTableInfo);
            context.setMappingTable(mappingTable);
            context.setChunkSize(1000);
            context.setType(ValidationTypeEnum.BACKWARD);
            context.setValSQLGenerator(ValSQLGenerator.builder().ctx(context).build());
            context.setRepository(new ValTaskRepositoryImpl(context));
        } catch (Exception e) {
            log.error("Error creating validationContext", e);
        }
        return context;
    }
}
