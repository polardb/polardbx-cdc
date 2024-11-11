/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.task;

import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportTaskDetailInfo;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * FSMMetaManager test
 *
 * @author siyu.yusi
 */
@Ignore
public class FSMMetaManagerTest extends TestBase {
    @Test
    public void testGetStateMachineDetail() {
        ResultCode<DataImportTaskDetailInfo> ret = FSMMetaManager.getStateMachineDetail(1);
        Assert.assertNotNull(ret);
    }
}
