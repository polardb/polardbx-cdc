/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.jdbc;

import com.alibaba.polardbx.MysqlDriver;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Registers PolarDB-X Connector/J for legacy {@code jdbc:mysql:} URLs.
 */
public final class PolarDbxCompatDriver implements Driver {

    private static final Driver DELEGATE = MysqlDriver.DRIVER;

    static {
        try {
            DriverManager.registerDriver(new PolarDbxCompatDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return DELEGATE.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return DELEGATE.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return DELEGATE.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return DELEGATE.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return DELEGATE.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return DELEGATE.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return DELEGATE.getParentLogger();
    }
}
