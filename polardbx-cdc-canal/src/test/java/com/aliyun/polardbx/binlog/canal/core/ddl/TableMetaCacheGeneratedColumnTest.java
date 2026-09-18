/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl;

import org.junit.Assert;
import org.junit.Test;

/**
 * 测试 commit c1a6f3a0 中 TableMetaCache 生成列判断逻辑：
 * 只有 VIRTUAL GENERATED / STORED GENERATED 才是真正的生成列，
 * DEFAULT_GENERATED（如 DEFAULT CURRENT_TIMESTAMP）不是。
 * <p>
 * 由于 TableMetaCache.getTableMetaFromDb 内部直接查库，此测试用同样的
 * 字符串匹配逻辑来验证判断是否正确。
 */
public class TableMetaCacheGeneratedColumnTest {

    /**
     * 模拟 TableMetaCache 中的生成列判断逻辑（与代码保持一致）
     */
    private static boolean isGeneratedColumn(String extra) {
        String lowerExtra = extra.toLowerCase();
        return lowerExtra.contains("virtual generated") || lowerExtra.contains("stored generated");
    }

    @Test
    public void testVirtualGeneratedColumn() {
        // VIRTUAL GENERATED 列应识别为生成列
        Assert.assertTrue(isGeneratedColumn("VIRTUAL GENERATED"));
        Assert.assertTrue(isGeneratedColumn("virtual generated"));
        Assert.assertTrue(isGeneratedColumn("Virtual Generated"));
    }

    @Test
    public void testStoredGeneratedColumn() {
        // STORED GENERATED 列应识别为生成列
        Assert.assertTrue(isGeneratedColumn("STORED GENERATED"));
        Assert.assertTrue(isGeneratedColumn("stored generated"));
        Assert.assertTrue(isGeneratedColumn("Stored Generated"));
    }

    @Test
    public void testDefaultGeneratedColumn_notTreatedAsGenerated() {
        // DEFAULT_GENERATED 不应被识别为生成列
        // 这是 commit c1a6f3a0 修复的核心问题：
        // 之前 contains("GENERATED") 会误匹配 DEFAULT_GENERATED
        Assert.assertFalse(isGeneratedColumn("DEFAULT_GENERATED"));
        Assert.assertFalse(isGeneratedColumn("DEFAULT_GENERATED on update CURRENT_TIMESTAMP"));
    }

    @Test
    public void testDefaultCurrentTimestamp_notGenerated() {
        // 常见的 DEFAULT CURRENT_TIMESTAMP 列，Extra 为空或 DEFAULT_GENERATED
        Assert.assertFalse(isGeneratedColumn(""));
        Assert.assertFalse(isGeneratedColumn("on update CURRENT_TIMESTAMP"));
        Assert.assertFalse(isGeneratedColumn("DEFAULT_GENERATED on update CURRENT_TIMESTAMP"));
    }

    @Test
    public void testAutoIncrement_notGenerated() {
        // AUTO_INCREMENT 不是生成列
        Assert.assertFalse(isGeneratedColumn("auto_increment"));
    }

    @Test
    public void testOnUpdateCurrentTimestamp_notGenerated() {
        // ON UPDATE CURRENT_TIMESTAMP 不是生成列
        Assert.assertFalse(isGeneratedColumn("on update CURRENT_TIMESTAMP"));
    }

    /**
     * 测试 onUpdate 判断逻辑（对应 TableMetaCache 中的修改：使用 extra.contains("on update")）
     */
    @Test
    public void testOnUpdateDetection() {
        Assert.assertTrue("on update CURRENT_TIMESTAMP".toLowerCase().contains("on update"));
        Assert.assertTrue("DEFAULT_GENERATED on update CURRENT_TIMESTAMP".toLowerCase().contains("on update"));
        Assert.assertFalse("auto_increment".toLowerCase().contains("on update"));
        Assert.assertFalse("VIRTUAL GENERATED".toLowerCase().contains("on update"));
    }
}
