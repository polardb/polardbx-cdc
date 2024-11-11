/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.cdc.meta.PolarDbXTableMetaManager;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.extractor.filter.rebuild.LogicDDLHandler;
import com.aliyun.polardbx.binlog.extractor.log.DDLEvent;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class LogicDdlHandlerTest extends BaseTestWithGmsTables {
    @Test
    public void testProcessWithInstanceServerIdDdl() throws Exception {
        long instanceServerId = 1;
        long transactionServerId = 2;
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        HandlerContext context = Mockito.mock(HandlerContext.class);
        Mockito.doNothing().when(logicDDLHandler).rebuildDDL(transaction, context, transactionServerId);
        Mockito.doCallRealMethod().when(logicDDLHandler).processDDL(transaction, context);
        Mockito.when(logicDDLHandler.getInstanceServerId()).thenReturn(instanceServerId);
        Mockito.when(transaction.getServerId()) .thenReturn(null);
        logicDDLHandler.processDDL(transaction, context);
        Mockito.verify(logicDDLHandler, Mockito.times(1)).rebuildDDL(transaction, context, instanceServerId);
    }

    @Test
    public void testProcessWithServerIdDdl() throws Exception {
        long instanceServerId = 1;
        long transactionServerId = 2;
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Transaction transaction = Mockito.mock(Transaction.class);
        HandlerContext context = Mockito.mock(HandlerContext.class);
        Mockito.doNothing().when(logicDDLHandler).rebuildDDL(transaction, context, transactionServerId);
        Mockito.doCallRealMethod().when(logicDDLHandler).processDDL(transaction, context);
        Mockito.when(logicDDLHandler.getInstanceServerId()).thenReturn(instanceServerId);
        Mockito.when(transaction.getServerId()) .thenReturn(transactionServerId);
        logicDDLHandler.processDDL(transaction, context);
        Mockito.verify(logicDDLHandler, Mockito.times(1)).rebuildDDL(transaction, context, transactionServerId);
    }

    private Transaction mockTransaction(String ddlSql, String tableName, String schemaName, int visibility) {
        RuntimeContext rc = new RuntimeContext(new ThreadRecorder("aaa"));
        rc.setBinlogFile("bb.01");
        rc.setAuthenticationInfo(new AuthenticationInfo());
        rc.getAuthenticationInfo().setStorageInstId("aaa");
        Transaction tx = new Transaction(FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x, new FormatDescriptionEvent((short)5, "5.7", 1L), rc);
        tx.setVirtualTSO("111");
        DDLEvent event = new DDLEvent();
        event.initVisible(visibility, null);
        BinlogPosition pos = new BinlogPosition("", 1L, 1L, 1L);
        event.setPosition(pos);

        DDLRecord ddlRecord = new DDLRecord();

        ddlRecord.setJobId(1L);
        ddlRecord.setId(1L);
        ddlRecord.setDdlSql(ddlSql);
        ddlRecord.setSchemaName(schemaName);
        ddlRecord.setTableName(tableName);
        ddlRecord.setVisibility(visibility);
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);

        ddlRecord.setExtInfo(ddlExtInfo);
        event.setDdlRecord(ddlRecord);
        tx.setDdlEvent(event);
        return tx;
    }

    private JdbcTemplate mockPolarxJdbcTemplate() throws Exception {
        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.destroySingleton("polarxJdbcTemplate");
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);
        return polarxJdbcTemplate;
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
        int visibility = 1;

        Transaction tx = mockTransaction(createSql, "order_refund_manage", "test", visibility);
        JdbcTemplate polarxJdbcTemplate = mockPolarxJdbcTemplate();
        String sqlKind = "CREATE_TABLE";
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        Map<String, Object> returnMap = new HashMap();
        returnMap.put("ddl_sql", expected);
        returnMap.put("visibility", (long)visibility);
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
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.doCallRealMethod().when(logicDDLHandler).tryReplaceEventDataBefore(tx);
        logicDDLHandler.tryReplaceEventDataBefore(tx);

        DDLEvent event = tx.getDdlEvent();
        DDLRecord ddlRecord = event.getDdlRecord();
        Assert.assertEquals(expected, ddlRecord.getDdlSql());
        Assert.assertEquals(sqlKind, ddlRecord.getSqlKind());
        Assert.assertEquals(schemaName, ddlRecord.getSchemaName());
        Assert.assertEquals(tableName, ddlRecord.getTableName());
        Assert.assertEquals(visibility, ddlRecord.getVisibility());
        Assert.assertEquals(metaInfo, ddlRecord.getMetaInfo());
    }

    @Test
    public void testReplaceWithHistoryTable() {
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_HISTORY_FIRST, "true");
        setConfig(ConfigKeys.META_BUILD_RECORD_IGNORED_DDL_ENABLED, "false");
        setConfig(ConfigKeys.META_BUILD_APPLY_FROM_RECORD_FIRST, "false");
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
        int visibility = 1;
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        Transaction tx = mockTransaction(createSql, tableName, schemaName, visibility);
        tx.getDdlEvent().setPosition(new BinlogPosition("bb.01", "111"));
        BinlogLogicMetaHistoryMapper historyMapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        BinlogLogicMetaHistory history = new BinlogLogicMetaHistory();
        history.setDbName(schemaName);
        history.setTableName(tableName);
        history.setTso("111");
        history.setGmtCreated(new Date());
        history.setGmtModified(new Date());
        history.setSqlKind("CREATE_TABLE");
        history.setNeedApply(true);
        history.setType((byte) 2);
        history.setDdl(expected);

        historyMapper.insert(history);
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.doCallRealMethod().when(logicDDLHandler).tryReplaceEventDataBefore(tx);
        logicDDLHandler.tryReplaceEventDataBefore(tx);

        DDLEvent event = tx.getDdlEvent();
        DDLRecord ddlRecord = event.getDdlRecord();
        Assert.assertEquals(expected, ddlRecord.getDdlSql());

    }

    @Test
    public void testProcessDdlEventBefore(){
        DDLEvent ddlEvent = new DDLEvent();
        DDLRecord ddlRecord = new DDLRecord();
        ddlRecord.setSchemaName("d1");
        ddlRecord.setTableName("tb");
        ddlRecord.setSqlKind("CREATE_TABLE");
        ddlRecord.setId(1L);
        ddlRecord.setJobId(1L);
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);
        ddlRecord.setExtInfo(ddlExtInfo);
        ddlRecord.setDdlSql("move database db1,db2 to storageInstanceId1");
        ddlEvent.setDdlRecord(ddlRecord);

        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.doCallRealMethod().when(logicDDLHandler).processDdlEventBefore(any());
        Mockito.doCallRealMethod().when(logicDDLHandler).tryRewriteMoveDataBaseSql(any(), any());
        Mockito.when(logicDDLHandler.tryRewriteDropTableSql(any(),
            any(), any())).thenCallRealMethod();
        Mockito.when(logicDDLHandler.tryRewriteTruncateSql(any(), any())).thenCallRealMethod();


        //1 . rewrite move database sql
        ddlEvent.setVisible(true);
        logicDDLHandler.processDdlEventBefore(ddlEvent);
        Assert.assertEquals("select 1", ddlRecord.getDdlSql());
        Assert.assertFalse(ddlEvent.isVisible());

        //2. rewrite drop table sql
        ddlRecord.setDdlSql("drop table t1, t2, t3");
        ddlRecord.setTableName("t1");
        ddlEvent.setVisible(true);
        logicDDLHandler.processDdlEventBefore(ddlEvent);
        Assert.assertEquals("DROP TABLE t1", ddlRecord.getDdlSql());
        Assert.assertTrue(ddlEvent.isVisible());

        //3. rewrite truncate table sql
        ddlRecord.setDdlSql("truncate table tb1");
        ddlRecord.setTableName("__test_tb1");
        ddlEvent.setVisible(true);
        logicDDLHandler.processDdlEventBefore(ddlEvent);
        Assert.assertEquals("TRUNCATE TABLE __test_tb1", ddlRecord.getDdlSql());
        Assert.assertTrue(ddlEvent.isVisible());

    }

    @Test
    public void testBuildOutputDdlForPolarx(){
        DDLRecord ddlRecord = new DDLRecord();
        ddlRecord.setSchemaName("d1");
        ddlRecord.setTableName("tb");
        ddlRecord.setSqlKind("CREATE_TABLE");
        ddlRecord.setId(1L);
        ddlRecord.setJobId(1L);
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);
        ddlRecord.setExtInfo(ddlExtInfo);
        ddlRecord.setDdlSql("create table t1(id bigint primary key)");
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.doCallRealMethod().when(logicDDLHandler).buildOutputDdlForPolarx(ddlRecord);
        String outputPolarx = logicDDLHandler.buildOutputDdlForPolarx(ddlRecord);

        Assert.assertEquals(ddlRecord.getDdlSql(), outputPolarx);

        ddlExtInfo.setOriginalDdl("create table t2(id bigint primary key)");
        outputPolarx = logicDDLHandler.buildOutputDdlForPolarx(ddlRecord);
        Assert.assertEquals("create table t2(id bigint primary key)", outputPolarx);

    }

    @Test
    public void testBuildOutputDdlForMysql(){
        DDLRecord ddlRecord = new DDLRecord();
        ddlRecord.setSchemaName("d1");
        ddlRecord.setTableName("tb");
        ddlRecord.setSqlKind("CREATE_TABLE");
        ddlRecord.setId(1L);
        ddlRecord.setJobId(1L);
        DDLExtInfo ddlExtInfo = new DDLExtInfo();
        ddlExtInfo.setTaskId(1L);
        ddlRecord.setExtInfo(ddlExtInfo);
        ddlRecord.setDdlSql("create table t1(id bigint primary key)");
        EventAcceptFilter eventAcceptFilter = Mockito.mock(EventAcceptFilter.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(
            PolarDbXTableMetaManager.class);
        LogicDDLHandler logicDDLHandler = new LogicDDLHandler(1L, eventAcceptFilter, polarDbXTableMetaManager);

        //1. test create table with extra info
        ddlExtInfo.setOriginalDdl("/* //1/ */create table t2(id bigint primary key) engine=innodb");
        String outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("create table t1(id bigint primary key)", outputPolarx);

        ddlExtInfo.setOriginalDdl("create table t2(id bigint primary key) engine=innodb");
        outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("create table t2(id bigint primary key) engine=innodb", outputPolarx);
        //2. test foreign ddl
        ddlRecord.setSqlKind("TEST");
        ddlRecord.setDdlSql("select 1");
        ddlExtInfo.setOriginalDdl("CREATE TABLE employees (\n"
            + "    employee_id INT AUTO_INCREMENT PRIMARY KEY,\n"
            + "    employee_name VARCHAR(100) NOT NULL,\n"
            + "    department_id INT,\n"
            + "    FOREIGN KEY (department_id) REFERENCES departments(department_id)\n"
            + ");");
        outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("select 1", outputPolarx);
        ddlExtInfo.setForeignKeysDdl(true);
        outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("CREATE TABLE employees (\n"
            + "    employee_id INT AUTO_INCREMENT PRIMARY KEY,\n"
            + "    employee_name VARCHAR(100) NOT NULL,\n"
            + "    department_id INT,\n"
            + "    FOREIGN KEY (department_id) REFERENCES departments(department_id)\n"
            + ");", outputPolarx);

        //3. test drop shard_key
        ddlRecord.setSqlKind("TEST");
        ddlRecord.setDdlSql("alter table order_refund_manage drop index auto_shard_key_b");
        ddlExtInfo.setOriginalDdl("alter table order_refund_manage drop index auto_shard_key_b");
        Set<String> indexSet = new HashSet<>();
        Mockito.when(polarDbXTableMetaManager.findIndexes(any(), any())).thenReturn(indexSet);
        outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("ALTER TABLE order_refund_manage", outputPolarx);

        indexSet.add("auto_shard_key_b");
        ddlRecord.setSqlKind("TEST");
        ddlRecord.setDdlSql("alter table order_refund_manage drop index auto_shard_key_b");
        ddlExtInfo.setOriginalDdl("alter table order_refund_manage drop index auto_shard_key_b");
        outputPolarx = logicDDLHandler.buildOutputDdlForMysql(ddlRecord);
        Assert.assertEquals("alter table order_refund_manage drop index auto_shard_key_b", outputPolarx);
    }

    @Test
    public void testRebuildDdlForApply(){
        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ")  COMMENT '退款管理表 ';";

        // 本来tableName 应该是 `order_refund_manage `, 但是rebuildEventLogFilter.hack4RepairTableName 会订正表名为tableName
        String expected = "CREATE TABLE `order_refund_manage` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") DEFAULT CHARACTER SET = utf8mb4 DEFAULT COLLATE = utf8mb4_general_ci COMMENT '退款管理表 ';";
        int visibility = 1;
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        EventAcceptFilter eventAcceptFilter = Mockito.mock(EventAcceptFilter.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(
            PolarDbXTableMetaManager.class);
        LogicDDLHandler logicDDLHandler = new LogicDDLHandler(1L, eventAcceptFilter, polarDbXTableMetaManager);
        Transaction transaction = mockTransaction(createSql, tableName, schemaName, visibility);
        transaction.getDdlEvent().setVisible(true);
        logicDDLHandler.rebuildDdlForApply(transaction, "utf8mb4", "utf8mb4_general_ci");
        Assert.assertEquals(expected, transaction.getDdlEvent().getDdlRecord().getDdlSql());
        Assert.assertTrue(transaction.getDdlEvent().isVisible());

        // test cci
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setCci(true);
        transaction.getDdlEvent().setVisible(true);
        transaction.getDdlEvent().getDdlRecord().setDdlSql(createSql);
        logicDDLHandler.rebuildDdlForApply(transaction, "utf8mb4", "utf8mb4_general_ci");
        Assert.assertEquals(expected, transaction.getDdlEvent().getDdlRecord().getDdlSql());
        Assert.assertFalse(transaction.getDdlEvent().isVisibleToMysql());

        // test gsi
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setGsi(true);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setOrginalDdl(createSql);
        transaction.getDdlEvent().setVisible(true);
        transaction.getDdlEvent().getDdlRecord().setDdlSql(createSql);
        logicDDLHandler.rebuildDdlForApply(transaction, "utf8mb4", "utf8mb4_general_ci");
        Assert.assertEquals("select 1", transaction.getDdlEvent().getDdlRecord().getDdlSql());
        Assert.assertFalse(transaction.getDdlEvent().isVisibleToMysql());
    }

    @Test
    public void testDoApplyAndRebuildFilter(){
        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ")  COMMENT '退款管理表 ';";

        int visibility = 1;
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        EventAcceptFilter eventAcceptFilter = Mockito.mock(EventAcceptFilter.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(
            PolarDbXTableMetaManager.class);
        LogicDDLHandler logicDDLHandler = new LogicDDLHandler(1L, eventAcceptFilter, polarDbXTableMetaManager);
        Transaction transaction = mockTransaction(createSql, tableName, schemaName, visibility);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setGsi(false);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setCci(false);
        logicDDLHandler.doApplyAndRebuildFilter(transaction);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setGsi(true);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setCci(false);
        logicDDLHandler.doApplyAndRebuildFilter(transaction);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setGsi(false);
        transaction.getDdlEvent().getDdlRecord().getExtInfo().setCci(true);
        logicDDLHandler.doApplyAndRebuildFilter(transaction);
        Mockito.verify(polarDbXTableMetaManager, Mockito.times(3)).applyLogic(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(eventAcceptFilter, Mockito.times(1)).rebuild();


    }

    @Test
    public void testBuildQueryLogEvent() throws Exception {
        String createSql = "CREATE TABLE `order_refund_manage ` (\n"
            + "\t`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id',\n"
            + "\t`order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换',\n"
            + "\t`days` INT(8) NOT NULL COMMENT '退款支持的天数',\n"
            + "\t`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
            + "\t`update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',\n"
            + "\t`is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识',\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ")  COMMENT '退款管理表 ';";

        int visibility = 1;
        String schemaName = "d1";
        String tableName = "order_refund_manage";
        EventAcceptFilter eventAcceptFilter = Mockito.mock(EventAcceptFilter.class);
        PolarDbXTableMetaManager polarDbXTableMetaManager = Mockito.mock(
            PolarDbXTableMetaManager.class);
        HandlerContext context = Mockito.mock(HandlerContext.class);
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder("abc"));
        runtimeContext.setServerCharactorSet(new ServerCharactorSet());
        Mockito.when(context.getRuntimeContext()).thenReturn(runtimeContext);

        LogicDDLHandler logicDDLHandler = new LogicDDLHandler(1L, eventAcceptFilter, polarDbXTableMetaManager);
        Transaction transaction = mockTransaction(createSql, tableName, schemaName, visibility);

        logicDDLHandler.buildQueryLogEvent(transaction, context, createSql, createSql, 1L, "gbk", "gbk_chinese_ci");
        String queryString = transaction.getDdlEvent().getQueryEventBuilder().getQueryString();
        Assert.assertEquals("# POLARX_ORIGIN_SQL=CREATE TABLE `order_refund_manage ` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) DEFAULT CHARACTER SET = gbk DEFAULT COLLATE = gbk_chinese_ci COMMENT '退款管理表 ';\n"
            + "# POLARX_TSO=111\n"
            + "# POLARX_DDL_ID=0\n"
            + "CREATE TABLE `order_refund_manage` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) DEFAULT CHARACTER SET = gbk DEFAULT COLLATE = gbk_chinese_ci COMMENT '退款管理表 ';", queryString);


        // ignore output mysql
        transaction.getDdlEvent().setVisible(true);
        transaction.getDdlEvent().setVisibleToPolardbX(true);
        transaction.getDdlEvent().setVisibleToMysql(false);
        logicDDLHandler.buildQueryLogEvent(transaction, context, createSql, createSql, 1L, "gbk", "gbk_chinese_ci");
        queryString = transaction.getDdlEvent().getQueryEventBuilder().getQueryString();
        Assert.assertEquals(
            "# POLARX_ORIGIN_SQL=CREATE TABLE `order_refund_manage ` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) DEFAULT CHARACTER SET = gbk DEFAULT COLLATE = gbk_chinese_ci COMMENT '退款管理表 ';\n"
            + "# POLARX_TSO=111\n"
            + "# POLARX_DDL_ID=0\n", queryString);

        // ignore output polarx
        transaction.getDdlEvent().setVisible(true);
        transaction.getDdlEvent().setVisibleToMysql(true);
        transaction.getDdlEvent().setVisibleToPolardbX(false);
        logicDDLHandler.buildQueryLogEvent(transaction, context, createSql, createSql, 1L, "gbk", "gbk_chinese_ci");
        queryString = transaction.getDdlEvent().getQueryEventBuilder().getQueryString();
        Assert.assertEquals(
            "CREATE TABLE `order_refund_manage` ( `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键id', `order_type` TINYINT(32) NOT NULL COMMENT '订单类型：1堂食，2外卖，3买单，4积分兑换', `days` INT(8) NOT NULL COMMENT '退款支持的天数', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', `update_time` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间', `is_deleted` TINYINT(4) NOT NULL DEFAULT 0 COMMENT '删除标识', PRIMARY KEY (`id`) ) DEFAULT CHARACTER SET = gbk DEFAULT COLLATE = gbk_chinese_ci COMMENT '退款管理表 ';", queryString);



    }

    @Test
    public void testTryRewriteSql() {

        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.when(logicDDLHandler.tryRewriteDropTableSql(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
            .thenCallRealMethod();
        String s1 = logicDDLHandler.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb");
        String s2 = logicDDLHandler.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,xx.bb");
        String s3 = logicDDLHandler.tryRewriteDropTableSql("a`a", "b`b", "drop table `a``a`.`b``b`,`vv`.`b``b`,`a``a`.cc");
        String s4 = logicDDLHandler.tryRewriteDropTableSql("aa", "bb", "drop table bb,cc,dd");
        String s5 = logicDDLHandler.tryRewriteDropTableSql("aa", "bb", "drop table bb");
        String s6 = logicDDLHandler.tryRewriteDropTableSql("aa", "bb", "drop table aa.bb,aa.zz,aa.bb");
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

        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.when(logicDDLHandler.tryRewriteTruncateSql(Mockito.anyString(), Mockito.anyString()))
            .thenCallRealMethod();
        String sql = " /* //1/ *//*+tddl:cmd_extra(truncate_table_with_gsi=true)*/truncate table truncate_gsi_test_7";

        String rewriteSql = logicDDLHandler.tryRewriteTruncateSql("__test_truncate_gsi_test_7", sql);
        Assert.assertEquals(
            "/* //1/ */ /*+tddl:cmd_extra(truncate_table_with_gsi=true)*/ TRUNCATE TABLE __test_truncate_gsi_test_7",
            rewriteSql);

        rewriteSql = logicDDLHandler.tryRewriteTruncateSql("xxx", sql);
        Assert.assertEquals(sql, rewriteSql);
    }

    @Test
    public void testDropShardKeyTable(){
        String sql1 = "create table d1.t1(id bigint primary key , name varchar(20))";
        String sql2 = "alter table d1.t1 dbpartition by hash(`name`)";
        String sql3 = "create table `d1`.`t2` like `d1`.`t1`";
        String sql4 = "alter table t2 drop index auto_shard_key_name";
        String ddlSql = "CREATE TABLE `t2` (\n"
            + "\t`id` bigint(20) NOT NULL,\n"
            + "\t`name` varchar(20) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`),\n"
            + "\tINDEX `auto_shard_key_name` USING BTREE (`name`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "DBPARTITION BY hash(`name`) COLLATE utf8mb4_general_ci";
        DDLRecord record = new DDLRecord();
        record.setDdlSql(ddlSql);
        LogicDDLHandler logicDDLHandler = Mockito.mock(LogicDDLHandler.class);
        Mockito.doCallRealMethod().when(logicDDLHandler).compareAndFixShardKey(Mockito.any(), Mockito.anyString());
        logicDDLHandler.compareAndFixShardKey(record, sql3);
        String expetected = "CREATE TABLE `t2` (\n"
            + "\t`id` bigint(20) NOT NULL,\n"
            + "\t`name` varchar(20) DEFAULT NULL,\n"
            + "\tPRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4\n"
            + "DBPARTITION BY hash(`name`) COLLATE utf8mb4_general_ci";
        Assert.assertEquals(expetected, record.getDdlSql());
    }
}
