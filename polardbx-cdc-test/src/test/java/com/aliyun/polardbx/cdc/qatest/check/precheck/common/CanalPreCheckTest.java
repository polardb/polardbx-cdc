/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.precheck.common;

import com.aliyun.polardbx.cdc.qatest.base.canal.CanalBaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.SQLException;

@Slf4j
public class CanalPreCheckTest extends CanalBaseTest {

    @Test
    public void test() throws SQLException, InterruptedException {
        dump(DESTINATION_1);
    }
}
