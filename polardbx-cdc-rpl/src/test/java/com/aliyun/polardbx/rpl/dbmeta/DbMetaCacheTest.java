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
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class DbMetaCacheTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testGetDataSource() {
        DruidDataSource druidDataSource1 = new DruidDataSource();
        DruidDataSource druidDataSource2 = new DruidDataSource();
        DbMetaCache dbMetaCache = new DbMetaCache(new HostInfo(), 1, 1, true) {
            @Override
            DruidDataSource loadDataSource(String schema) throws Exception {
                if (StringUtils.isNotBlank(schema)) {
                    return druidDataSource1;
                } else {
                    return druidDataSource2;
                }
            }
        };
        DataSource dataSource1 = dbMetaCache.getDataSource("test");
        DataSource dataSource2 = dbMetaCache.getDataSource("");
        Assert.assertEquals(druidDataSource1, dataSource1);
        Assert.assertEquals(druidDataSource2, dataSource2);
    }

    @Test
    public void testGetConnection() throws SQLException {
        DbMetaCache dbMetaCache = new DbMetaCache(new HostInfo(), 1, 1, true) {
            @Override
            public DataSource getDataSource(String schema) {
                return dstDataSource;
            }
        };
        try (Connection connection = dbMetaCache.getConnection("test")) {
            Assert.assertTrue(connection.isValid(1));
            ResultSet resultSet = connection.createStatement().executeQuery("show databases");
            Set<String> databases = new HashSet<>();
            while (resultSet.next()) {
                databases.add(resultSet.getString(1));
            }
            Assert.assertEquals(Sets.newHashSet("INFORMATION_SCHEMA", "PUBLIC"), databases);
        }
    }
}
