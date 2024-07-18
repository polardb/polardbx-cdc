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
package com.aliyun.polardbx.cdc.qatest.check.bothcheck.binlog;

import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yudong
 * @since 2022/9/17
 **/
@Slf4j
public class DDLCountTest extends RplBaseTestCase {
    private static final String POLARX_DDL_COUNT_SQL = "SELECT COUNT(*) FROM __cdc__.__cdc_ddl_record__ "
        + "WHERE job_id IS NOT NULL ";
    private static final String MYSQL_DDL_COUNT_SQL = "SELECT COUNT(*) FROM binlog_logic_meta_history "
        + "WHERE type = 2 " + "AND ddl_job_id IS NOT NULL";

    private static final String POLARX_DDL_DETAIL_SQL =
        "SELECT job_id,schema_name,ddl_sql FROM __cdc__.__cdc_ddl_record__ "
            + "WHERE job_id IS NOT NULL ";
    private static final String MYSQL_DDL_DETAIL_SQL = "SELECT ddl_job_id,db_name,ddl "
        + "FROM binlog_logic_meta_history " + "WHERE type = 2 " + "AND ddl_job_id IS NOT NULL";

    @Test
    public void checkDdlCount() throws SQLException, InterruptedException {
        log.info("send token");
        sendTokenAndWait(CheckParameter.builder().build());
        log.info("received token");

        long executeCount = 0;
        while (true) {
            int count1 = 0;
            // create if not exits，drop if exists，这两种情况如果库表已经存在或不存在时，binlog_logic_meta_history中不会记录
            // 验证两天打标数据量是否一致时，忽略掉job_id为null的情况，drop database除外
            try (Connection conn = getPolardbxConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet resultSet =
                        stmt.executeQuery(POLARX_DDL_COUNT_SQL);
                    while (resultSet.next()) {
                        count1 = resultSet.getInt(1);
                    }
                }
            } catch (SQLException e) {
                throw e;
            }

            int count2 = 0;
            try (Connection conn = getMetaConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    ResultSet resultSet =
                        stmt.executeQuery(MYSQL_DDL_COUNT_SQL);
                    while (resultSet.next()) {
                        count2 = resultSet.getInt(1);
                    }
                }
            } catch (SQLException e) {
                throw e;
            }

            log.info("src count is {}, target count is {}", count1, count2);
            try {
                Assert.assertEquals(count1, count2);
                break;
            } catch (Throwable t) {
                printDiff();
                executeCount++;
                if (executeCount < 5) {
                    Thread.sleep(1000);
                } else {
                    throw t;
                }
            }
        }
    }

    private void printDiff() throws SQLException {
        Map<String, Triple<String, String, String>> srcMap = new HashMap<>();
        Map<String, Triple<String, String, String>> targetMap = new HashMap<>();

        try (Connection conn = getPolardbxConnection()) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet resultSet = stmt.executeQuery(POLARX_DDL_DETAIL_SQL);
                while (resultSet.next()) {
                    Triple<String, String, String> triple = Triple.of(
                        resultSet.getString(1), resultSet.getString(2), resultSet.getString(3));
                    srcMap.put(triple.getLeft(), triple);
                }
            }
        } catch (SQLException e) {
            throw e;
        }

        try (Connection conn = getMetaConnection()) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet resultSet = stmt.executeQuery(MYSQL_DDL_DETAIL_SQL);
                while (resultSet.next()) {
                    Triple<String, String, String> triple = Triple.of(
                        resultSet.getString(1), resultSet.getString(2), resultSet.getString(3));
                    targetMap.put(triple.getLeft(), triple);
                }
            }
        } catch (SQLException e) {
            throw e;
        }

        Sets.SetView<String> diff1 = Sets.difference(srcMap.keySet(), targetMap.keySet());
        Sets.SetView<String> diff2 = Sets.difference(targetMap.keySet(), srcMap.keySet());

        log.info("ddl exist in source but not in target {}", diff1);
        diff1.forEach(s -> {
            log.info(srcMap.get(s).toString());
        });

        log.info("ddl exist in target but not in source {}", diff2);
        diff2.forEach(s -> {
            log.info(targetMap.get(s).toString());
        });
    }
}
