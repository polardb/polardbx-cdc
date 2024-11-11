/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.reformat;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.IConfigDataProvider;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.MemoryTableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.LogicTableMeta;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class TableMetaCompareTest {

    private static final Logger logger = LoggerFactory.getLogger(TableMetaCompareTest.class);

    private final String TEST_DB = "test_db";

    private final String ddl = "CREATE TABLE `t_member_info` (\n"
        + "        `id` bigint(20) NOT NULL COMMENT '主键id',\n"
        + "        `one_id` varchar(32) NOT NULL COMMENT 'OneId',\n"
        + "        `register_time` datetime DEFAULT NULL COMMENT '注册时间',\n"
        + "        `nick_name` varchar(128) DEFAULT '' COMMENT '昵称',\n"
        + "        `bind_status` tinyint(4) DEFAULT '0' COMMENT '明文手机号绑定状态 0-未绑定 1-已绑定',\n"
        + "        `use_status` tinyint(4) DEFAULT '1' COMMENT '使用状态 1-生效 0-失效。合并会员后，某些globalId失效',\n"
        + "        `staging_status` tinyint(4) DEFAULT '0' COMMENT '暂存状态: 0-否 1-是,没有明文手机号的暂存，暂存状态，不返回给前端，但积分等业务需要正常处理',\n"
        + "        `create_by` varchar(32) DEFAULT '' COMMENT '创建人',\n"
        + "        `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n"
        + "        `updated_by` varchar(32) DEFAULT '' COMMENT '更新人',\n"
        + "        `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n"
        + "        `channel_code` varchar(32) DEFAULT '' COMMENT '渠道编码',\n"
        + "        `system_code` varchar(32) DEFAULT '' COMMENT '系统编码',\n"
        + "        `shop_code` varchar(32) DEFAULT '' COMMENT '店铺编码',\n"
        + "        `shop_name` varchar(255) DEFAULT '' COMMENT '店铺名称',\n"
        + "        `member_code` varchar(255) DEFAULT '' COMMENT '会员账号',\n"
        + "        `brand_code` varchar(32) DEFAULT '' COMMENT '会员品牌编码',\n"
        + "        `mobile` varchar(32) DEFAULT '' COMMENT '电话号码',\n"
        + "        `mobile_encrypted` varchar(255) DEFAULT '' COMMENT '手机密文',\n"
        + "        `mobile_sha` varchar(64) DEFAULT '' COMMENT '手机哈希值',\n"
        + "        PRIMARY KEY USING BTREE (`id`),\n"
        + "        UNIQUE KEY `idx_member_info_one_id` USING BTREE (`one_id`),\n"
        + "        KEY `idx_member_info_register_time` USING BTREE (`register_time`),\n"
        + "        KEY `idx_nick_name` USING BTREE (`nick_name`),\n"
        + "        KEY `idx_member_info_member_code` USING BTREE (`member_code`),\n"
        + "        KEY `idx_member_info_mobile_sha` (`mobile_sha`)\n"
        + ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '全域会员主表'";

    @BeforeClass
    public static void beforeClass() throws Exception {
        DynamicApplicationConfig.setConfigDataProvider(new IConfigDataProvider() {
            @Override
            public String getValue(String key) {
                if (key.equals(ConfigKeys.TASK_REFORMAT_COLUMN_TYPE_ENABLED)) {
                    return "true";
                } else if (key.equals(ConfigKeys.META_RECOVER_ROLLBACK_MODE)) {
                    return RollbackMode.SNAPSHOT_EXACTLY.name();
                }
                return "";
            }
        });
    }

    /**
     * test drop column case
     * 1、 drop phy table
     * 2、 drop logic
     */
    @Test
    public void testCompoareDropColumn() {

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(logger, false);

        memoryTableMeta.apply(new BinlogPosition("0001", 0, -1, -1), TEST_DB, ddl, null);
        TableMeta logicTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");

        String alterDDL = "alter table t_member_info drop column mobile";

        memoryTableMeta.apply(new BinlogPosition("0001", 1, -1, -1), TEST_DB, alterDDL, null);
        TableMeta phyTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");

        MockPolarDbXTableMetaManager manager = new MockPolarDbXTableMetaManager(phyTableMeta, logicTableMeta);
        LogicTableMeta.FieldMetaExt mobileField = null;
        LogicTableMeta logicTableMetaExt = manager.compare(TEST_DB, "tt", phyTableMeta.getFields().size());
        int idx = 0;
        for (LogicTableMeta.FieldMetaExt logicFieldExt : logicTableMetaExt.getLogicFields()) {
            if (logicFieldExt.getColumnName().equals("mobile")) {
                mobileField = logicFieldExt;
                idx++;
            } else {
                Assert.assertEquals(logicFieldExt.getPhyIndex() + idx, logicFieldExt.getLogicIndex());
            }
            Assert.assertTrue(logicFieldExt.isTypeMatch());
        }
        Assert.assertNotNull(mobileField);
        Assert.assertEquals(-1, mobileField.getPhyIndex());

    }

    /**
     * test add column case
     * 1、 add phy table
     * 2、 add logic
     */
    @Test
    public void testCompoareAddColumn() {

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(logger, false);

        memoryTableMeta.apply(new BinlogPosition("0001", 0, -1, -1), TEST_DB, ddl, null);
        TableMeta logicTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");

        String alterDDL = "alter table t_member_info add column mobile_number varchar(32) DEFAULT '' after mobile";

        memoryTableMeta.apply(new BinlogPosition("0001", 1, -1, -1), TEST_DB, alterDDL, null);
        TableMeta phyTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");
        MockPolarDbXTableMetaManager manager = new MockPolarDbXTableMetaManager(phyTableMeta, logicTableMeta);
        LogicTableMeta logicTableMetaExt = manager.compare(TEST_DB, "tt", phyTableMeta.getFields().size());
        int idx = 0;
        for (LogicTableMeta.FieldMetaExt logicFieldExt : logicTableMetaExt.getLogicFields()) {
            Assert.assertEquals(logicFieldExt.getPhyIndex() - idx, logicFieldExt.getLogicIndex());
            Assert.assertTrue(logicFieldExt.isTypeMatch());
            if (logicFieldExt.getColumnName().equals("mobile")) {
                idx++;
            }
        }

    }

    /**
     * test modify column type case
     * 1、 modify phy table column type
     * 2、 add logic
     */
    @Test
    public void testCompoareModifyColumnType() {

        MemoryTableMeta memoryTableMeta = new MemoryTableMeta(logger, false);

        memoryTableMeta.apply(new BinlogPosition("0001", 0, -1, -1), TEST_DB, ddl, null);
        TableMeta logicTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");

        String alterDDL = "alter table t_member_info modify column mobile bigint(32) DEFAULT '0'";

        memoryTableMeta.apply(new BinlogPosition("0001", 1, -1, -1), TEST_DB, alterDDL, null);
        TableMeta phyTableMeta = memoryTableMeta.find(TEST_DB, "t_member_info");
        MockPolarDbXTableMetaManager manager = new MockPolarDbXTableMetaManager(phyTableMeta, logicTableMeta);
        LogicTableMeta logicTableMetaExt = manager.compare(TEST_DB, "tt", phyTableMeta.getFields().size());
        for (LogicTableMeta.FieldMetaExt logicFieldExt : logicTableMetaExt.getLogicFields()) {
            Assert.assertEquals(logicFieldExt.getPhyIndex(), logicFieldExt.getLogicIndex());
            if (logicFieldExt.getColumnName().equals("mobile")) {
                Assert.assertFalse(logicFieldExt.isTypeMatch());
            } else {
                Assert.assertTrue(logicFieldExt.isTypeMatch());
            }
        }

    }
}
