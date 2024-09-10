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
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.HandlerEvent;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.cdc.meta.ConsistencyChecker;
import com.aliyun.polardbx.binlog.cdc.meta.ConsistencyCheckerFactory;
import com.aliyun.polardbx.binlog.cdc.meta.MetaFilter;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXLogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXLogicTableMetaFactory;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXStorageTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXStorageTableMetaFactory;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackModeUtil;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.extractor.log.DDLEvent;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Created by ziyang.lb
 **/
public class RebuildEventLogFilterTest extends BaseTestWithGmsTables {

    @Test
    public void testTryRewriteSql() {
        RebuildEventLogFilter filter = new RebuildEventLogFilter(1, null, false, null);
        String s1 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb");
        String s2 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,xx.bb");
        String s3 = filter.tryRewriteDropTableSql("a`a", "b`b", "drop table `a``a`.`b``b`,`vv`.`b``b`,`a``a`.cc");
        String s4 = filter.tryRewriteDropTableSql("aa", "bb", "drop table bb,cc,dd");
        String s5 = filter.tryRewriteDropTableSql("aa", "bb", "drop table bb");
        String s6 = filter.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,aa.bb");
        System.out.println(s1);
        System.out.println(s2);
        System.out.println(s3);
        System.out.println(s4);
        System.out.println(s5);
        System.out.println(s6);
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s1));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s2));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table `a``a`.`b``b`", s3));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table bb", s4));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table bb", s5));
        Assert.assertTrue(StringUtils.equalsIgnoreCase("drop table aa.bb", s6));
    }

    @Test
    public void testTryRewriteDropTableSql() {
        String sql = " /* //1/ *//*+tddl:cmd_extra(truncate_table_with_gsi=true)*/truncate table truncate_gsi_test_7";
        RebuildEventLogFilter filter = new RebuildEventLogFilter(1, null, false, null);

        String rewriteSql = filter.tryRewriteTruncateSql("__test_truncate_gsi_test_7", sql);
        Assert.assertEquals(
            "/* //1/ */ /*+tddl:cmd_extra(truncate_table_with_gsi=true)*/ TRUNCATE TABLE __test_truncate_gsi_test_7",
            rewriteSql);

        rewriteSql = filter.tryRewriteTruncateSql("xxx", sql);
        Assert.assertEquals(sql, rewriteSql);
    }

    private String applyDdl;

    @Test
    public void testCciFilter() throws Exception {
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST, "false");
        setConfig(ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED, "false");

        TransactionGroup tg = new TransactionGroup(new LinkedList<>());
        Transaction tx = mockBaseDdlTransaction();

        DDLEvent event = new DDLEvent();
        event.initVisible(1, null);
        BinlogPosition pos = new BinlogPosition("", 1L, 1L, 1L);
        event.setPosition(pos);

        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(
            "/*DDL_ID=7224350753635172416*/ALTER TABLE  `c_gr_t1`.`c_r` ADD PARTITION ( PARTITION p2 VALUES LESS THAN (30000) )");
        ddlRecord.setSchemaName("drds_auto");
        ddlRecord.setTableName("c_gr_t1");
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setCci(true);
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);
        event.setDdlRecord(ddlRecord);
        when(tx.getDdlEvent()).thenReturn(event);

        tg.getTransactionList().add(tx);

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);

        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Map<String, Object> returnMap = new HashMap();
        returnMap.put("ddl_sql", ddlRecord.getDdlSql());
        returnMap.put("visibility", 0L);
        when(polarxJdbcTemplate.queryForMap(
            "select ddl_sql,visibility, ext from __cdc_ddl_record__ where id = 1")).thenReturn(returnMap);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.destroySingleton("polarxJdbcTemplate");
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);

        mockFilter().handle(tg, mockHandlerContext());

        QueryEventBuilder builder = event.getQueryEventBuilder();
        String expected =
            "# POLARX_ORIGIN_SQL=ALTER TABLE `c_gr_t1`.`c_r` ADD PARTITION (PARTITION p2 VALUES LESS THAN (30000))\n"
                + "# POLARX_TSO=11111\n"
                + "# POLARX_DDL_ID=7224350753635172416\n"
                + "# POLARX_DDL_TYPES=CCI\n";
        Assert.assertEquals(expected, builder.getQueryString());
    }

    @Test
    public void testTableWithBlankApply() throws Exception {
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST, "false");
        setConfig(ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED, "false");

        TransactionGroup tg = new TransactionGroup(new LinkedList<>());
        Transaction tx = mockBaseDdlTransaction();

        DDLEvent event = new DDLEvent();
        event.initVisible(1, null);
        BinlogPosition pos = new BinlogPosition("", 1L, 1L, 1L);
        event.setPosition(pos);

        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";
        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(createSql);
        ddlRecord.setSchemaName("d1");
        ddlRecord.setTableName("order_refund_manage ");
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);
        event.setDdlRecord(ddlRecord);
        when(tx.getDdlEvent()).thenReturn(event);

        tg.getTransactionList().add(tx);

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);

        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Map<String, Object> returnMap = new HashMap();
        returnMap.put("ddl_sql", createSql);
        returnMap.put("visibility", 0L);
        returnMap.put("sql_kind", "CREATE_TABLE");
        returnMap.put("schema_name", "d1");
        returnMap.put("table_name", "test_tb");
        List<Map<String, Object>> lst = new ArrayList();
        lst.add(returnMap);
        when(polarxJdbcTemplate.queryForList(
            "select sql_kind, schema_name, table_name, meta_info, ddl_sql,visibility, ext from __cdc_ddl_record__ where id = 1")).thenReturn(
            lst);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);

        mockFilter().handle(tg, mockHandlerContext());

        QueryEventBuilder builder = event.getQueryEventBuilder();
        String expected =
            "# POLARX_ORIGIN_SQL=CREATE TABLE `order_refund_manage ` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';\n"
                + "# POLARX_TSO=11111\n"
                + "# POLARX_DDL_ID=0\n"
                + "CREATE TABLE `order_refund_manage ` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";
        Assert.assertEquals(expected, builder.getQueryString());
        Assert.assertEquals(createSql, applyDdl);
    }

    @Test
    public void testTableWithUseCdcRecordFirst() throws Exception {
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST, "false");
        setConfig(ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED, "false");
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_RECORD_FIRST, "true");
        setConfig(ConfigKeys.IS_LAB_ENV, "false");

        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";

        long visibility = 1L;

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);

        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Map<String, Object> returnMap = new HashMap();
        returnMap.put("ddl_sql", "select 1");
        returnMap.put("visibility", visibility);
        DDLExtInfo newDdlExtInfo = new DDLExtInfo();
        returnMap.put("ext", JSON.toJSONString(newDdlExtInfo));
        returnMap.put("sql_kind", "CREATE_TABLE");
        returnMap.put("schema_name", "d1");
        returnMap.put("table_name", "test_tb");
        List<Map<String, Object>> lst = new ArrayList();
        lst.add(returnMap);
        when(polarxJdbcTemplate.queryForList(
            "select sql_kind, schema_name, table_name, meta_info, ddl_sql,visibility, ext from __cdc_ddl_record__ where id = 1")).thenReturn(
            lst);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.destroySingleton("polarxJdbcTemplate");
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);
        TransactionGroup tg = new TransactionGroup(new LinkedList<>());
        Transaction tx = mockBaseDdlTransaction();

        DDLEvent event = new DDLEvent();
        event.initVisible(1, null);
        BinlogPosition pos = new BinlogPosition("", 1L, 1L, 1L);
        event.setPosition(pos);

        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(createSql);
        ddlRecord.setSchemaName("d1");
        ddlRecord.setTableName("order_refund_manage ");
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);

        event.setDdlRecord(ddlRecord);
        tx.setDdlEvent(event);

        tg.getTransactionList().add(tx);

        mockFilter().handle(tg, mockHandlerContext());
        event = tg.getTransactionList().get(0).getDdlEvent();
        QueryEventBuilder builder = event.getQueryEventBuilder();
        Assert.assertEquals("# POLARX_ORIGIN_SQL=SELECT 1\n"
            + "# POLARX_TSO=11111\n"
            + "# POLARX_DDL_ID=0\n"
            + "SELECT 1", builder.getQueryString());
        Assert.assertEquals("select 1", applyDdl);
    }

    @Test
    public void testTableWithUseCdcRecordFirstForExt() throws Exception {
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST, "false");
        setConfig(ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED, "false");
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_RECORD_FIRST, "true");
        setConfig(ConfigKeys.IS_LAB_ENV, "false");

        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";

        // 本来tableName 应该是 `order_refund_manage `, 但是rebuildEventLogFilter.hack4RepairTableName 会订正表名为tableName
        String expected = "CREATE TABLE `order_refund_manage` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = INNODB DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";
        long visibility = 1L;

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);

        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        String sqlKind = "CREATE_TABLE";
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        Map<String, Object> returnMap = new HashMap();
        returnMap.put("ddl_sql", createSql);
        returnMap.put("visibility", visibility);
        DDLExtInfo newDdlExtInfo = new DDLExtInfo();
        newDdlExtInfo.setOriginalDdl("select 1");
        returnMap.put("ext", JSON.toJSONString(newDdlExtInfo));
        returnMap.put("sql_kind", sqlKind);
        returnMap.put("schema_name", schemaName);
        returnMap.put("table_name", tableName);
        TopologyRecord tr = new TopologyRecord();
        tr.setLowerCased(true);
        tr.setLogicDbMeta(new LogicMetaTopology.LogicDbTopology());
        tr.setLogicTableMeta(new LogicMetaTopology.LogicTableMetaTopology());
        String metaInfo = JSON.toJSONString(tr);
        returnMap.put("meta_info", metaInfo);
        List<Map<String, Object>> lst = new ArrayList();
        lst.add(returnMap);
        when(polarxJdbcTemplate.queryForList(
            "select sql_kind, schema_name, table_name, meta_info, ddl_sql,visibility, ext from __cdc_ddl_record__ where id = 1")).thenReturn(
            lst);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.destroySingleton("polarxJdbcTemplate");
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);
        TransactionGroup tg = new TransactionGroup(new LinkedList<>());
        Transaction tx = mockBaseDdlTransaction();

        DDLEvent event = new DDLEvent();
        event.initVisible(1, null);
        BinlogPosition pos = new BinlogPosition("", 1L, 1L, 1L);
        event.setPosition(pos);

        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(createSql);
        ddlRecord.setSchemaName("ddd ");
        ddlRecord.setTableName("order_refund_manage ");
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);

        event.setDdlRecord(ddlRecord);
        tx.setDdlEvent(event);

        tg.getTransactionList().add(tx);

        mockFilter().handle(tg, mockHandlerContext());
        event = tg.getTransactionList().get(0).getDdlEvent();
        ddlRecord = event.getDdlRecord();
        QueryEventBuilder builder = event.getQueryEventBuilder();
        Assert.assertEquals("# POLARX_ORIGIN_SQL=SELECT 1\n"
                + "# POLARX_TSO=11111\n"
                + "# POLARX_DDL_ID=0\n"
                + "SELECT 1",
            builder.getQueryString());
        Assert.assertEquals(expected, applyDdl);
        Assert.assertEquals(tableName, ddlRecord.getTableName());
        Assert.assertEquals(schemaName, ddlRecord.getSchemaName());
        Assert.assertEquals(sqlKind, ddlRecord.getSqlKind());
        Assert.assertEquals(metaInfo, ddlRecord.getMetaInfo());
    }

    @Test
    public void testMetaFilter() {

        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(
            "/*DDL_ID=7224350753635172416*/ALTER TABLE  `c_gr_t1`.`c_r` ADD PARTITION ( PARTITION p2 VALUES LESS THAN (30000) )");
        ddlRecord.setSchemaName("drds_auto");
        ddlRecord.setTableName("c_gr_t1");
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setCci(true);
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);
        Assert.assertFalse(MetaFilter.isSupportApply(ddlRecord));
    }

    private MockedStatic<PolarDbXStorageTableMetaFactory> mockStorageTableMetaFactory;
    private MockedStatic<PolarDbXLogicTableMetaFactory> mockPolarDbXLogicTableMetaFactory;
    private MockedStatic<ConsistencyCheckerFactory> mockConsistencyCheckerFactory;
    private MockedStatic<RollbackModeUtil> mockRollbackModeUtil;

    @Before
    public void setUpFactory() {
        mockStorageTableMetaFactory =
            Mockito.mockStatic(PolarDbXStorageTableMetaFactory.class);
        Mockito.when(
                PolarDbXStorageTableMetaFactory.create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).
            thenReturn(new PolarDbXStorageTableMeta(null, null, null, null));

        mockPolarDbXLogicTableMetaFactory =
            Mockito.mockStatic(PolarDbXLogicTableMetaFactory.class);
        Mockito.when(PolarDbXLogicTableMetaFactory.create(Mockito.any(), Mockito.any()))
            .thenReturn(new PolarDbXLogicTableMeta(null, null) {
                @Override
                public boolean apply(BinlogPosition position, DDLRecord record, String cmdId) {
                    applyDdl = record.getDdlSql();
                    return false;
                }
            });

        mockConsistencyCheckerFactory =
            Mockito.mockStatic(ConsistencyCheckerFactory.class);

        Mockito.when(ConsistencyCheckerFactory.create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
            .thenReturn(new ConsistencyChecker(null, null, null, ""));

        mockRollbackModeUtil = Mockito.mockStatic(RollbackModeUtil.class);
        Mockito.when(RollbackModeUtil.getRollbackMode()).thenReturn(RollbackMode.RANDOM);
    }

    @After
    public void afterCloseFactor() {
        mockStorageTableMetaFactory.close();
        mockPolarDbXLogicTableMetaFactory.close();
        mockConsistencyCheckerFactory.close();
        mockRollbackModeUtil.close();
    }

    public Transaction mockBaseDdlTransaction() {
        Transaction tx = Mockito.mock(Transaction.class);

        when(tx.needRevert()).thenReturn(false);
        when(tx.isDescriptionEvent()).thenReturn(false);
        when(tx.isMetadataBuildCommand()).thenReturn(false);
        when(tx.isHeartbeat()).thenReturn(false);
        when(tx.isInstructionCommand()).thenReturn(false);
        when(tx.isDDL()).thenReturn(true);
        when(tx.isValidTransaction()).thenReturn(true);
        when(tx.isVisibleDdl()).thenReturn(true);
        when(tx.getVirtualTsoStr()).thenReturn("11111");
        when(tx.getDdlEvent()).thenCallRealMethod();
        Mockito.doCallRealMethod().when(tx).setDdlEvent(Mockito.any());
        return tx;
    }

    public RebuildEventLogFilter mockFilter() {
        PolarDbXTableMetaManager polarDbXTableMetaManager =
            new PolarDbXTableMetaManager("111", () -> false, () -> "5.4.17");

        polarDbXTableMetaManager.init();
        EventAcceptFilter acceptor = Mockito.mock(EventAcceptFilter.class);
        Mockito.when(acceptor.accept(Mockito.any())).thenReturn(true);
        Mockito.doNothing().when(acceptor).rebuild();
        return new RebuildEventLogFilter(111, acceptor, false, polarDbXTableMetaManager);
    }

    public HandlerContext mockHandlerContext() {
        HandlerContext context = new HandlerContext(new EmptyFilter());

        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        context.getRuntimeContext().setServerCharactorSet(new ServerCharactorSet());
        context.getRuntimeContext().setLowerCaseTableNames(1);
        context.getRuntimeContext().setAuthenticationInfo(new AuthenticationInfo());
        return context;
    }

    static class EmptyFilter implements LogEventFilter {

        @Override
        public void handle(HandlerEvent event, HandlerContext context) throws Exception {

        }

        @Override
        public void onStart(HandlerContext context) {

        }

        @Override
        public void onStop() {

        }

        @Override
        public void onStartConsume(HandlerContext context) {

        }
    }
}
