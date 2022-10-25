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
package com.aliyun.polardbx.binlog.rest;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.TopologyManager;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ConnectionInfo;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfig;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskCreateRequest;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportApiResourceTest {
    @Test
    public void testCreate() {
        ImportTaskCreateRequest request = new ImportTaskCreateRequest();
//        request.setDesc("test import");
//        request.setDstDbName("import_test_123");
//        request.setName("test import");
//        request.setSourceInstanceId("drdsbabd069736r1");
//        request.setType("IMPORT");
        ImportTaskConfig importTaskConfig = new ImportTaskConfig();
        importTaskConfig.setRules("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE beans PUBLIC \"-//SPRING//DTD BEAN//EN\" \"http://www.springframework.org/dtd/spring-beans.dtd\">\n"
            + "<beans>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.VirtualTableRoot\" id=\"vtabroot\" init-method=\"init\">\n"
            + "    <property name=\"dbType\" value=\"MYSQL\" />\n"
            + "    <property name=\"defaultDbIndex\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tableRules\">\n"
            + "      <map>\n"
            + "        <entry value-ref=\"id_shardtargettabletest1780146256\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[shard_target_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_broadcastcasttabletest783008795\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[broadcast_cast_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_singletabletest852113734\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[single_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_multidbsingletbl422654686\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[multi_db_single_tbl]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_multidbsingletbl2222446097570\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[multi_db_single_tbl2222]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_alltype1798286104\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[all_type]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_alltypetest893944153\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[all_type_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlconfig1438471842\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_config]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlwarehouse475991847\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_warehouse]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqldistrict1477132970\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_district]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlcustomer1794346746\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_customer]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlhistory1667137032\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_history]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlneworder2078166957\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_new_order]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqloorder1094804901\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_oorder]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlorderline127728225\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_order_line]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlitem1723824175\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_item]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlstock1908186490\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_stock]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "      </map>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_shardtargettabletest1780146256\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[shard_target_table_test_WESS_{00}]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbRuleArray\">\n"
            + "      <value><![CDATA[((#name,1,10#).hashCode().abs().longValue() % 10)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_broadcastcasttabletest783008795\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[broadcast_cast_table_test_JWTx]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "    <property name=\"broadcast\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_singletabletest852113734\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[single_table_test_dh3r]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_multidbsingletbl422654686\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[multi_db_single_tbl_AQfM]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_multidbsingletbl2222446097570\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[multi_db_single_tbl2222]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_alltype1798286104\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#type_int,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[all_type_6yTA]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_alltypetest893944153\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#type_int,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[all_type_test_YTgz]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlconfig1438471842\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_config]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlwarehouse475991847\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_warehouse_37Qc]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqldistrict1477132970\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#d_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_district_a5ig]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlcustomer1794346746\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#c_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_customer_b1L0]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlhistory1667137032\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#h_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_history_OgEZ]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlneworder2078166957\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#no_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_new_order_92Qy]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqloorder1094804901\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#o_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_oorder_Sys7]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlorderline127728225\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#ol_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_order_line_sa3T]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlitem1723824175\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#i_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_item_ykC4]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlstock1908186490\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#s_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_stock_mHSm]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "</beans>\n");
        ConnectionInfo connectionInfo = new ConnectionInfo();
        connectionInfo.setDbNameList(Arrays.asList("import_test_123"));
        connectionInfo.setIp("100.100.124.1");
        connectionInfo.setPort(16539);
        connectionInfo.setUser("drds_123456");
        connectionInfo.setPwd("drds@123456");
        //drds: mysql -h100.100.124.1 -P16539 -udrds_123456 -pdrds@123456

        importTaskConfig.setSrcConn(connectionInfo);
        importTaskConfig
            .setTableList(
                Arrays.asList("bmsql_district", "bmsql_oorder", "bmsql_stock", "bmsql_customer", "multi_db_single_tbl",
                    "single_table_test", "bmsql_item", "all_type_test", "broadcast_cast_table_test", "bmsql_new_order",
                    "multi_db_single_tbl2222", "shard_target_table_test", "all_type", "bmsql_warehouse"
                    , "bmsql_config", "bmsql_order_line"));
        List<ConnectionInfo> srcPhyConnList = new ArrayList<>();
        //mysql -h100.100.124.1 -P10512 -uitheql54 -pd4Xa4yNaUFBh
        ConnectionInfo srcRds1 = new ConnectionInfo();
        srcRds1.setDbInstanceId("rm-hp30nn69j426410yx");
        srcRds1.setIp("100.100.124.1");
        srcRds1.setPort(10512);
        srcRds1.setUser("itheql54");
        srcRds1.setPwd("d4Xa4yNaUFBh");
        srcRds1.setDbNameList(Arrays.asList(
            "import_test_123_tvba_0000",
            "import_test_123_tvba_0001",
            "import_test_123_tvba_0002",
            "import_test_123_tvba_0003",
            "import_test_123_tvba_0004",
            "import_test_123_tvba_0005",
            "import_test_123_tvba_0006",
            "import_test_123_tvba_0007"
        ));

        //mysql -h100.100.124.1 -P9212 -uitheql54 -pd4Xa4yNaUFBh
        ConnectionInfo srcRds2 = new ConnectionInfo();
        srcRds2.setDbInstanceId("rm-hp3ogmu2p8i14x750");
        srcRds2.setIp("100.100.124.1");
        srcRds2.setPort(9212);
        srcRds2.setUser("itheql54");
        srcRds2.setPwd("d4Xa4yNaUFBh");
        srcRds2.setDbNameList(Arrays.asList(
            "import_test_123_tvba_0008",
            "import_test_123_tvba_0009",
            "import_test_123_tvba_0010",
            "import_test_123_tvba_0011",
            "import_test_123_tvba_0012",
            "import_test_123_tvba_0013",
            "import_test_123_tvba_0014",
            "import_test_123_tvba_0015"
        ));

        srcPhyConnList.add(srcRds1);
        srcPhyConnList.add(srcRds2);
        importTaskConfig.setSrcPhyConnList(srcPhyConnList);
        request.setConfig(importTaskConfig);

        System.out.println(JSON.toJSONString(importTaskConfig));

    }

    @Test
    public void test() {
        TopologyManager tm = new TopologyManager("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE beans PUBLIC \"-//SPRING//DTD BEAN//EN\" \"http://www.springframework.org/dtd/spring-beans.dtd\">\n"
            + "<beans>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.VirtualTableRoot\" id=\"vtabroot\" init-method=\"init\">\n"
            + "    <property name=\"dbType\" value=\"MYSQL\" />\n"
            + "    <property name=\"defaultDbIndex\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tableRules\">\n"
            + "      <map>\n"
            + "        <entry value-ref=\"id_shardtargettabletest1780146256\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[shard_target_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_broadcastcasttabletest783008795\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[broadcast_cast_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_singletabletest852113734\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[single_table_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_multidbsingletbl422654686\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[multi_db_single_tbl]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_multidbsingletbl2222446097570\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[multi_db_single_tbl2222]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_alltype1798286104\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[all_type]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_alltypetest893944153\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[all_type_test]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlconfig1438471842\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_config]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlwarehouse475991847\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_warehouse]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqldistrict1477132970\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_district]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlcustomer1794346746\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_customer]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlhistory1667137032\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_history]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlneworder2078166957\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_new_order]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqloorder1094804901\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_oorder]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlorderline127728225\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_order_line]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlitem1723824175\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_item]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "        <entry value-ref=\"id_bmsqlstock1908186490\">\n"
            + "          <key>\n"
            + "            <value><![CDATA[bmsql_stock]]></value>\n"
            + "          </key>\n"
            + "        </entry>\n"
            + "      </map>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_shardtargettabletest1780146256\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[shard_target_table_test_WESS_{00}]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbRuleArray\">\n"
            + "      <value><![CDATA[((#name,1,10#).hashCode().abs().longValue() % 10)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_broadcastcasttabletest783008795\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[broadcast_cast_table_test_JWTx]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "    <property name=\"broadcast\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_singletabletest852113734\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[single_table_test_dh3r]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_multidbsingletbl422654686\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[multi_db_single_tbl_AQfM]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_multidbsingletbl2222446097570\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[multi_db_single_tbl2222]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_alltype1798286104\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#type_int,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[all_type_6yTA]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_alltypetest893944153\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#type_int,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[all_type_test_YTgz]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlconfig1438471842\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_0000\" />\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_config]]></value>\n"
            + "    </property>\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlwarehouse475991847\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_warehouse_37Qc]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqldistrict1477132970\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#d_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_district_a5ig]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlcustomer1794346746\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#c_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_customer_b1L0]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlhistory1667137032\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#h_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_history_OgEZ]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlneworder2078166957\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#no_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_new_order_92Qy]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqloorder1094804901\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#o_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_oorder_Sys7]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlorderline127728225\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#ol_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_order_line_sa3T]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlitem1723824175\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#i_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_item_ykC4]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "  <bean class=\"com.taobao.tddl.interact.rule.TableRule\" id=\"id_bmsqlstock1908186490\">\n"
            + "    <property name=\"dbNamePattern\" value=\"IMPORT_TEST_123_1631532251585ZTAG_N1JH_{0000}\" />\n"
            + "    <property name=\"dbRuleArray\">\n"
            + "      <value><![CDATA[((#s_w_id,1,16#).longValue().abs() % 16)]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"tbNamePattern\">\n"
            + "      <value><![CDATA[bmsql_stock_mHSm]]></value>\n"
            + "    </property>\n"
            + "    <property name=\"allowFullTableScan\" value=\"true\" />\n"
            + "  </bean>\n"
            + "</beans>\n");
        List<String> tableList =
            tm.getAllPhyTableList("import_test_123_tvba_0000", Sets.newHashSet(Arrays
                .asList("broadcast_cast_table_test", "shard_target_table_test", "single_table_test", "all_type")));
        System.out.println(JSON.toJSONString(tableList));
        System.out.println(JSON.toJSONString(tm.getLogicTableSet()));

    }
}