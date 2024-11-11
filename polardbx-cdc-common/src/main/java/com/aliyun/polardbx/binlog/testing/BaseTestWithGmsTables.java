/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.testing.h2.H2Util.executeUpdate;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTestWithGmsTables extends BaseTest {

    protected static final AtomicBoolean GMS_TABLE_INITED = new AtomicBoolean();

    @SneakyThrows
    public BaseTestWithGmsTables() {
        if (GMS_TABLE_INITED.compareAndSet(false, true)) {
            long start = System.currentTimeMillis();
            try (Connection connection = getGmsDataSource().getConnection()) {
                Resource resource = new DefaultResourceLoader().getResource("classpath:testing-conf/gms_tables.sql");
                H2Util.executeBatchSql(connection, resource.getFile());

                Resource resource2 = new DefaultResourceLoader().getResource(
                    "classpath:testing-conf/gms_additional.sql");
                H2Util.executeBatchSql(connection, resource2.getFile());
            }
            log.warn("successfully init gms tables, cost time {} (ms)", System.currentTimeMillis() - start);
        }
    }

    @Before
    @SneakyThrows
    public void initGmsTables() {
        if (truncateGmsTableAtEachBefore()) {
            truncateGmsTables();
        }
    }

    protected boolean truncateGmsTableAtEachBefore() {
        return true;
    }

    protected void truncateGmsTables() throws Exception {
        long start = System.currentTimeMillis();
        try (Connection connection = getGmsDataSource().getConnection()) {
            List<String> tables = H2Util.showTables(connection, null);
            tables.forEach(
                t -> executeUpdate(connection, String.format("truncate table `%s`", escape(t))));
        }
        log.warn("successfully truncate gms tables , cost time {} (ms)", System.currentTimeMillis() - start);
    }
}
