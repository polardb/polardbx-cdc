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
package com.aliyun.polardbx.cdc.qatest.postcheck;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * 检查元数据snapshot能力是否正常触发
 *
 * @author yudong
 * @since 2023/8/9 14:38
 **/
public class BinlogPolarxCommandTest extends BaseTestCase {
    @SneakyThrows
    @Test
    public void testBinlogPolarxCommand() {
        try (Connection conn = getMetaConnection()) {
            String queryFailedCommand =
                "select * from `binlog_polarx_command` where `cmd_type` = 'BUILD_META_SNAPSHOT' order by `id`";
            ResultSet rs = JdbcUtil.executeQuery(queryFailedCommand, conn);
            int count = 0;
            while (rs.next()) {
                count++;
                int status = rs.getInt("cmd_status");
                Assert.assertNotEquals("build meta snapshot command failed!", status, 2);
            }
            Assert.assertTrue("meta snapshot command count <= 1", count > 1);
        }
    }

}
