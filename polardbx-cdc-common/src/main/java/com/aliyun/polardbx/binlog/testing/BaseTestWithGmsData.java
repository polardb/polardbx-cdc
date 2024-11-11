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
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.File;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTestWithGmsData extends BaseTest {

    private static final AtomicBoolean GMS_TABLE_INITED = new AtomicBoolean();

    @SneakyThrows
    public BaseTestWithGmsData() {
        //同一进程内，所有test method 共享这一份内存库表数据
        if (GMS_TABLE_INITED.compareAndSet(false, true)) {
            long start = System.currentTimeMillis();
            try (Connection connection = getGmsDataSource().getConnection()) {
                H2Util.executeBatchSqlGzip(connection, getCompressSqlFile());

                Resource resource2 = new DefaultResourceLoader().getResource(
                    "classpath:testing-conf/gms_additional.sql");
                H2Util.executeBatchSql(connection, resource2.getFile());
            }
            H2Util.execUpdate(getGmsDataSource(), "alter table binlog_logic_meta_history "
                + "add column `need_apply` tinyint(1) default 1 not null");

            if (log.isDebugEnabled()) {
                log.debug("successfully init gms cdc tables and data, cost time {} (ms)",
                    System.currentTimeMillis() - start);
            }
        }
    }

    @SneakyThrows
    private File getCompressSqlFile() {
        Resource resource = new DefaultResourceLoader().getResource(
            "classpath:testing-conf/gms_cdc_tables_and_data.sql.gz");
        return resource.getFile();
    }
}
