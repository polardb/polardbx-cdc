/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.jdbc;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ServiceLoader;

public class PolarDbxCompatDriverTest {

    @Test
    public void testAcceptsMysqlUrl() throws Exception {
        PolarDbxCompatDriver driver = new PolarDbxCompatDriver();
        Assert.assertTrue(driver.acceptsURL("jdbc:mysql://127.0.0.1:3306/db"));
        Assert.assertFalse(driver.acceptsURL("jdbc:postgresql://127.0.0.1:5432/db"));
    }

    @Test
    public void testJdbcRegistration() throws Exception {
        Driver driver = DriverManager.getDriver("jdbc:mysql://127.0.0.1:3306/db");
        Assert.assertEquals(PolarDbxCompatDriver.class, driver.getClass());

        boolean found = false;
        for (Driver serviceDriver : ServiceLoader.load(Driver.class)) {
            if (serviceDriver instanceof PolarDbxCompatDriver) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);
    }

    @Test
    public void testDruidUsesCompatibilityDriver() throws Exception {
        DruidDataSource dataSource = new DruidDataSource();
        try {
            dataSource.setInitialSize(0);
            dataSource.setUrl("jdbc:mysql://127.0.0.1:3306/db");
            dataSource.setDriverClassName(PolarDbxCompatDriver.class.getName());
            dataSource.init();
            Assert.assertEquals(PolarDbxCompatDriver.class, dataSource.getDriver().getClass());
        } finally {
            dataSource.close();
        }
    }
}
