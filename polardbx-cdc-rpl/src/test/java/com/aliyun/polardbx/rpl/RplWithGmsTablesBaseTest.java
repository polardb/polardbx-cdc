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
