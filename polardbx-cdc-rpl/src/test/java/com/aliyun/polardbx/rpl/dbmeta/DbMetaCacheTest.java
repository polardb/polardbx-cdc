/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
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

    @Test
    public void testPrepareConnectionInitSqlsWithLabEnvAndLab80() {
        // Mock DynamicApplicationConfig to return specific values
        mockConfig(ConfigKeys.RPL_DEFAULT_SQL_MODE, "STRICT_TRANS_TABLES");
        mockConfig(ConfigKeys.RPL_LAB_80_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_CONNECTION_INIT_SQL, "");
        mockConfig(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION, "false");
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");

        // Create HostInfo with POLARX2 type to enter the relevant code path
        HostInfo hostInfo = new HostInfo();
        hostInfo.setType(HostType.POLARX2);

        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 1, 1, true);

        // Use reflection to access the private prepareConnectionInitSqls method
        try {
            java.lang.reflect.Method method = DbMetaCache.class.getDeclaredMethod("prepareConnectionInitSqls");
            method.setAccessible(true);
            List<String> connectionInitSqls = (List<String>) method.invoke(dbMetaCache);

            // Verify that "set sql_require_primary_key = 0" is added when both isLabEnv and isLab80 are true
            Assert.assertTrue("Should contain 'set sql_require_primary_key = 0' when isLabEnv=true and isLab80=true",
                connectionInitSqls.contains("set sql_require_primary_key = 0"));
        } catch (Exception e) {
            Assert.fail("Failed to invoke prepareConnectionInitSqls method: " + e.getMessage());
        }
    }

    @Test
    public void testPrepareConnectionInitSqlsWithoutLabEnv() {
        // Mock DynamicApplicationConfig to return specific values
        mockConfig(ConfigKeys.RPL_DEFAULT_SQL_MODE, "STRICT_TRANS_TABLES");
        mockConfig(ConfigKeys.RPL_LAB_80_ENABLED, "true");
        mockConfig(ConfigKeys.RPL_CONNECTION_INIT_SQL, "");
        mockConfig(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION, "false");
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");

        // Create HostInfo with POLARX2 type to enter the relevant code path
        HostInfo hostInfo = new HostInfo();
        hostInfo.setType(HostType.POLARX2);

        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 1, 1, true);

        // Use reflection to access the private prepareConnectionInitSqls method
        try {
            java.lang.reflect.Method method = DbMetaCache.class.getDeclaredMethod("prepareConnectionInitSqls");
            method.setAccessible(true);
            List<String> connectionInitSqls = (List<String>) method.invoke(dbMetaCache);

            // Verify that "set sql_require_primary_key = 0" is NOT added when isLabEnv=false
            Assert.assertFalse("Should NOT contain 'set sql_require_primary_key = 0' when isLabEnv=false",
                connectionInitSqls.contains("set sql_require_primary_key = 0"));
        } catch (Exception e) {
            Assert.fail("Failed to invoke prepareConnectionInitSqls method: " + e.getMessage());
        }
    }

    @Test
    public void testPrepareConnectionInitSqlsWithoutLab80() {
        // Mock DynamicApplicationConfig to return specific values
        mockConfig(ConfigKeys.RPL_DEFAULT_SQL_MODE, "STRICT_TRANS_TABLES");
        mockConfig(ConfigKeys.RPL_LAB_80_ENABLED, "false");
        mockConfig(ConfigKeys.RPL_CONNECTION_INIT_SQL, "");
        mockConfig(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION, "false");
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");

        // Create HostInfo with POLARX2 type to enter the relevant code path
        HostInfo hostInfo = new HostInfo();
        hostInfo.setType(HostType.POLARX2);

        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 1, 1, true);

        // Use reflection to access the private prepareConnectionInitSqls method
        try {
            java.lang.reflect.Method method = DbMetaCache.class.getDeclaredMethod("prepareConnectionInitSqls");
            method.setAccessible(true);
            List<String> connectionInitSqls = (List<String>) method.invoke(dbMetaCache);

            // Verify that "set sql_require_primary_key = 0" is NOT added when isLab80=false
            Assert.assertFalse("Should NOT contain 'set sql_require_primary_key = 0' when isLab80=false",
                connectionInitSqls.contains("set sql_require_primary_key = 0"));
        } catch (Exception e) {
            Assert.fail("Failed to invoke prepareConnectionInitSqls method: " + e.getMessage());
        }
    }

    @Test
    public void testPrepareConnectionInitSqlsWithoutLabEnvAndLab80() {
        // Mock DynamicApplicationConfig to return specific values

        mockConfig(ConfigKeys.RPL_DEFAULT_SQL_MODE, "STRICT_TRANS_TABLES");
        mockConfig(ConfigKeys.RPL_LAB_80_ENABLED, "false");
        mockConfig(ConfigKeys.RPL_CONNECTION_INIT_SQL, "");
        mockConfig(ConfigKeys.RPL_POLARDBX1_OLD_VERSION_OPTION, "false");
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");

        // Create HostInfo with POLARX2 type to enter the relevant code path
        HostInfo hostInfo = new HostInfo();
        hostInfo.setType(HostType.POLARX2);

        DbMetaCache dbMetaCache = new DbMetaCache(hostInfo, 1, 1, true);

        // Use reflection to access the private prepareConnectionInitSqls method
        try {
            java.lang.reflect.Method method = DbMetaCache.class.getDeclaredMethod("prepareConnectionInitSqls");
            method.setAccessible(true);
            List<String> connectionInitSqls = (List<String>) method.invoke(dbMetaCache);

            // Verify that "set sql_require_primary_key = 0" is NOT added when both isLabEnv and isLab80 are false
            Assert.assertFalse(
                "Should NOT contain 'set sql_require_primary_key = 0' when isLabEnv=false and isLab80=false",
                connectionInitSqls.contains("set sql_require_primary_key = 0"));
        } catch (Exception e) {
            Assert.fail("Failed to invoke prepareConnectionInitSqls method: " + e.getMessage());
        }
    }
}