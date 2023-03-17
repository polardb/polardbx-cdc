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

import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_REFRESH_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RECOVER_TSO_TESTING_ENABLE;

/**
 * created by ziyang.lb
 **/
public class ForceRebalanceSwitch extends RplBaseTestCase {
    private static final String SQL = "replace into binlog_system_config(config_key,config_value)values('%s','%s')";

    @Test
    public void open() throws SQLException {
        try (Connection connection = getMetaConnection()) {
            Statement stmt = connection.createStatement();
            stmt.execute(String.format(SQL, TOPOLOGY_FORCE_REFRESH_INTERVAL_MINUTE, 15));
            stmt.execute(String.format(SQL, TOPOLOGY_RECOVER_TSO_TESTING_ENABLE, "true"));
        }
        try (Connection connection = getPolardbxConnection()) {
            Statement stmt = connection.createStatement();
            stmt.execute("set global COMPLEX_DML_WITH_TRX=true");
        }
    }

    @Test
    public void close() throws SQLException {
        try (Connection connection = getMetaConnection()) {
            Statement stmt = connection.createStatement();
            stmt.execute(String.format(SQL, TOPOLOGY_FORCE_REFRESH_INTERVAL_MINUTE, 0));
            stmt.execute(String.format(SQL, TOPOLOGY_RECOVER_TSO_TESTING_ENABLE, "false"));
        }
    }
}
