/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.transfer;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class PrepareData {

    private static final String CREATE_TABLE =
        "CREATE TABLE if not exists accounts (id INT PRIMARY KEY, balance INT NOT NULL) ENGINE=InnoDB  DBPARTITION BY HASH(id)";
    private static final String MYSQL_CREATE_TABLE =
        "CREATE TABLE if not exists accounts (id INT PRIMARY KEY, balance INT NOT NULL)";
    private static boolean isPolarx = true;

    private static final String PREPARE_DATA_FORMAT = "INSERT INTO accounts VALUES %s";

    private static void cleanTable(Connection connection) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("truncate table accounts");
        ps.executeUpdate();
    }

    private static void createTable(Connection connection) throws SQLException {
        String sql = isPolarx ? CREATE_TABLE : MYSQL_CREATE_TABLE;
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.executeUpdate();
    }

    private static void prepareData(Connection connection, int accountCount, int initialBalance) throws SQLException {
        List<String> valueList = Lists.newArrayListWithCapacity(accountCount);
        for (int i = 0; i < accountCount; i++) {
            valueList.add(String.format("(%d, %d)", i, initialBalance));
        }
        PreparedStatement ps = connection.prepareStatement(String.format(PREPARE_DATA_FORMAT,
            StringUtils.join(valueList, ",")));
        ps.executeUpdate();
    }

    public static void init(Connection connection, int accountCount, int initialBalance) throws SQLException {
        createTable(connection);
        cleanTable(connection);
        prepareData(connection, accountCount, initialBalance);
    }

    public static void setPolarx(boolean isPolarx) {
        PrepareData.isPolarx = isPolarx;
    }
}
