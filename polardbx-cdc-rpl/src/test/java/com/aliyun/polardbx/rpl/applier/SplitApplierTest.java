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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SplitApplierTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testLogSerialExecuteInfo() {
        SplitApplier splitApplier = new SplitApplier(new ApplierConfig(), new HostInfo(), new HostInfo());
        Map<String, List<DefaultRowChange>> map = new HashMap<>();
        map.put("t1", Lists.newArrayList(new DefaultRowChange()));
        splitApplier.logSerialExecuteInfo(map);
        Assert.assertFalse(splitApplier.logSerialExecuteInfo(map));
    }
}
