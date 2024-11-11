/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.rpl.common.TaskContext;

import javax.sql.DataSource;

public class RplWithGmsTablesBaseTest extends BaseTestWithGmsTables {

    protected DataSource srcDataSource;
    protected DataSource dstDataSource;
    protected DataSource metaDataSource;

    public RplWithGmsTablesBaseTest() {
        super();
        mockTaskContext();
        srcDataSource = SpringContextHolder.getObject("srcDataSource");
        dstDataSource = SpringContextHolder.getObject("dstDataSource");
        metaDataSource = SpringContextHolder.getObject("metaDataSource");
    }

    private void mockTaskContext() {
        TaskContext context = TaskContext.getInstance();
        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(1L);
        RplService service = new RplService();
        service.setId(1L);
        RplTask task = new RplTask();
        task.setId(1L);
        context.setStateMachine(stateMachine);
        context.setService(service);
        context.setTask(task);
        context.setTaskConfig(new RplTaskConfig());
    }
}
