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
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Test;

public class LogEventStorageTest extends BaseTestWithGmsTables {

    @Test
    public void tryRestart() {
        LogEventStorage storage =
            new LogEventStorage(new Repository(true, "test", PersistMode.AUTO, 0.5, 100, 100, DeleteMode.SINGLE, 1));
        storage.start();
        storage.stop();
        storage.start();
        storage.stop();
    }
}
