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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import org.junit.Test;

public class XidTest {
    @Test
    public void testParseTid() throws Exception {
        String xid =
            "X'647264732d313261373530636132353030383030324062623236613963383163636433386265',X'5f5f4344435f5f5f3030303030325f47524f5550',1";
        Long tid = LogEventUtil.getTranIdFromXid(xid, "utf8");
        String groupName = LogEventUtil.getGroupFromXid(xid, "utf8");
        System.out.println("tid : " + tid + " , groupName : " + groupName);
    }
}
