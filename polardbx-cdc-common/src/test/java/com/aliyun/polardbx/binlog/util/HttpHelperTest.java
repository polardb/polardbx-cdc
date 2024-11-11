/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonMetrics;
import com.google.common.collect.Lists;
import org.apache.http.entity.ContentType;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpHelperTest {

    @Test
    @Ignore
    public void post() throws IOException, URISyntaxException, InterruptedException {
        List<CommonMetrics> commonMetrics = Lists.newArrayList(CommonMetrics.builder().key("a").type(1).value(100)
                .build(),
            CommonMetrics.builder().key("b").type(2).value(200).build(),
            CommonMetrics.builder().key("c").type(1).value(300).build()
        );

        for (int i = 0; i < 10; i++) {
            PooledHttpHelper.doPost("http://127.0.0.1:3007/cdc/reports", ContentType.APPLICATION_JSON,
                JSON.toJSONString(commonMetrics), 1000);
            TimeUnit.SECONDS.sleep(3);
        }

    }
}
