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

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTestWithGmsTables extends BaseTest {

    protected static final AtomicBoolean GMS_TABLE_INITED = new AtomicBoolean();
    protected static final String GMS_DB_NAME = "polardbx_meta_db";

    @SneakyThrows
    public BaseTestWithGmsTables() {
        if (GMS_TABLE_INITED.compareAndSet(false, true)) {
            long start = System.currentTimeMillis();
            try (Connection connection = getGmsDataSource().getConnection()) {
                Resource resource = new DefaultResourceLoader().getResource("classpath:testing-conf/gms_tables.sql");
                H2Util.executeBatchSql(connection, resource.getFile());
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
            List<String> tables = H2Util.showTables(connection, GMS_DB_NAME);
            tables.forEach(t -> executeUpdate(connection, String.format("truncate table %s.%s", GMS_DB_NAME, t)));
        }
        log.warn("successfully truncate gms tables , cost time {} (ms)", System.currentTimeMillis() - start);
    }
}
