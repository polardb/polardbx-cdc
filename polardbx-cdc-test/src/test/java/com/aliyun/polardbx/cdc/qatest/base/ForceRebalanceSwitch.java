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
package com.aliyun.polardbx.cdc.qatest.base;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED;

/**
 * created by ziyang.lb
 **/
public class ForceRebalanceSwitch extends RplBaseTestCase {
    private static final String SQL =
        "replace into metadb.binlog_system_config(config_key,config_value)values('%s','%s')";

    @Test
    public void open() throws SQLException {
        try (Connection connection = getPolardbxConnection()) {
            internalOpen(connection);
        }

        if (dstIsReplica()) {
            try (Connection connection = getCdcSyncDbConnection()) {
                internalOpen(connection);
            }
        }

        try (Connection connection = getPolardbxConnection()) {
            Statement stmt = connection.createStatement();
            stmt.execute("set global COMPLEX_DML_WITH_TRX=true");
        }
    }

    @Test
    public void close() throws SQLException {
        try (Connection connection = getPolardbxConnection()) {
            internalClose(connection);
        }

        if (dstIsReplica()) {
            try (Connection connection = getCdcSyncDbConnection()) {
                internalClose(connection);
            }
        }
    }

    private void internalOpen(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute(String.format(SQL, DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, 15));
        stmt.execute(String.format(SQL, TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED, "RANDOM"));

    }

    private void internalClose(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute(String.format(SQL, DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, 0));
        stmt.execute(String.format(SQL, TOPOLOGY_FORCE_USE_RECOVER_TSO_ENABLED, "false"));
    }

    private boolean dstIsReplica() throws SQLException {
        ResultSet resultSet = JdbcUtil.executeQuery("select version()", getCdcSyncDbConnection());
        if (resultSet.next()) {
            String version = resultSet.getString(1);
            return StringUtils.contains(version, "TDDL");
        }
        return false;
    }
}
