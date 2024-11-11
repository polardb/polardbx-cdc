/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.postcheck.common;

import com.aliyun.polardbx.cdc.qatest.base.canal.CanalBaseTest;
import org.junit.Test;

import java.sql.SQLException;

public class CanalPostCheckTest extends CanalBaseTest {

    @Test
    public void test1() throws SQLException, InterruptedException {
        dump(DESTINATION_1);
    }

    @Test
    public void test2() throws SQLException, InterruptedException {
        dump(DESTINATION_2);
    }
}
