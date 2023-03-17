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
package com.aliyun.polardbx.binlog.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author yudong
 * @since 2023/2/22 17:13
 **/
public class ConfigMapUtilTest {

    @Test
    public void getConfigNameTest() {
        String expect = "binlogx_stream_group_name";
        String configName = "binlogx.stream.group.name";
        String actual = ConfigMapUtil.getOldConfigName(configName);
        Assert.assertEquals(expect, actual);

        configName = "not_exist_config";
        expect = "";
        actual = ConfigMapUtil.getOldConfigName(configName);
        Assert.assertEquals(expect, actual);
    }
}
