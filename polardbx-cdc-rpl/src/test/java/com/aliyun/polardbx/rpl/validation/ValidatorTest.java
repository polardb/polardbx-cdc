/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ValidatorTest extends BaseTest {

    @Before
    public void setUp() {
        mockConfig(ConfigKeys.RPL_FULL_VALID_MAX_ROW_SIZE_PER_SECOND, "0");
    }

    /**
     * 测试用例1: 当配置键对应的值为正数时，检查rpsLimit是否正确赋值。
     */
    @Test
    public void testRpsLimitPositiveValue() {
        TaskContext.getInstance().setStateMachine(new RplStateMachine());
        TaskContext.getInstance().getStateMachine().setId(1L);
        TaskContext.getInstance().setService(new RplService());
        TaskContext.getInstance().getService().setId(1L);
        TaskContext.getInstance().setTask(new RplTask());
        TaskContext.getInstance().getTask().setId(1L);
        // 设置前置条件
        mockConfig(ConfigKeys.RPL_FULL_VALID_MAX_ROW_SIZE_PER_SECOND, "10");
        // 执行
        int actualRpsLimit = Validator.getRpsLimit();

        // 验证
        assertEquals("rpsLimit should be set correctly", 10, actualRpsLimit);
    }
}



