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
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

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
                H2Util.executeBatchSql(connection, uncompressSqlFile());
            }

            if (log.isDebugEnabled()) {
                log.debug("successfully init gms cdc tables and data, cost time {} (ms)",
                    System.currentTimeMillis() - start);
            }
        }
    }

    @SneakyThrows
    private String uncompressSqlFile() {
        Resource resource = new DefaultResourceLoader().getResource(
            "classpath:testing-conf/gms_cdc_tables_and_data.sql.gz");

        try (FileInputStream fileInputStream = new FileInputStream(resource.getFile());
            GZIPInputStream gzipInputStream = new GZIPInputStream(fileInputStream);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                byteArrayOutputStream.write(buffer, 0, len);
            }

            return byteArrayOutputStream.toString();
        }
    }
}
