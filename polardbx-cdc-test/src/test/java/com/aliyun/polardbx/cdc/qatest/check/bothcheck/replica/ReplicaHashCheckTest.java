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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.replica;

import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * check replica hashcheck sql
 *
 * @author yudong
 * @since 2023/11/21 14:47
 **/
@Slf4j
public class ReplicaHashCheckTest extends BaseTestCase {
    private static final String REPLICA_HASH_CHECK = "REPLICA HASHCHECK * FROM `%s`.`%s`";

    @Test
    @SneakyThrows
    public void baseTest() {
        List<String> failedTables = new ArrayList<>();
        int total = 0;
        int failed = 0;
        try (Connection conn = getPolardbxConnection();
            Statement stmt = conn.createStatement()) {
            List<String> dbList = getDatabaseList();
            for (String db : dbList) {
                List<String> tbList = getTableList(db);
                for (String tb : tbList) {
                    total++;
                    String sql = String.format(REPLICA_HASH_CHECK, escape(db), escape(tb));
                    try {
                        stmt.execute(sql);
                    } catch (Exception e) {
                        log.error("replica hashcheck table:{}.{} failed!", db, tb);
                        failed++;
                        failedTables.add(String.format("%s.%s", db, tb));
                    }
                }
            }
        }
        log.info("replica hashcheck total:{}, failed:{}, failedTables:{}", total, failed, failedTables);
    }

    private List<String> getDatabaseList() throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.showDatabases(conn);
        }
    }

    private List<String> getTableList(String db) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.showTables(conn, db);
        }
    }

}
