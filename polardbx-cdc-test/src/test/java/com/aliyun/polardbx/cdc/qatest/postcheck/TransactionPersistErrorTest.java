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

import com.aliyun.polardbx.binlog.util.LabEventType;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * description: check if exists transaction persist error
 * author: ziyang.lb
 * create: 2023-08-11 15:15
 **/
public class TransactionPersistErrorTest extends RplBaseTestCase {

    @Test
    public void testTransactionPersistError() throws SQLException {
        String labEventCountQuery =
            "select count(*) as c from binlog_lab_event where event_type = %d";

        try (Connection conn = getMetaConnection()) {
            String result = JdbcUtil.executeQueryAndGetFirstStringResult(
                String.format(labEventCountQuery, LabEventType.TASK_TRANSACTION_PERSIST_ERROR.ordinal()), conn);
            Assert.assertEquals(0L, NumberUtils.createLong(result).longValue());
        }
    }
}
