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
package com.aliyun.polardbx.rpl.task;

import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.ResultCode;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportTaskDetailInfo;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * FSMMetaManager test
 *
 * @author siyu.yusi
 */
public class FSMMetaManagerTest extends TestBase {
    @Test
    public void testGetStateMachineDetail() {
        ResultCode<DataImportTaskDetailInfo> ret = FSMMetaManager.getStateMachineDetail(1);
        Assert.assertNotNull(ret);
    }
}
