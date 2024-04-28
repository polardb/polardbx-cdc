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
package com.aliyun.polardbx.rpl.validation.fullvalid;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2023/10/8 15:47
 **/
@Slf4j
public class ReplicaFullValidSampler {
    private static final String SAMPLE_SQL_FORMAT =
        "/*+TDDL:cmd_extra(sample_percentage=%f,enable_push_sort=false)*/ "
            + "SELECT %s FROM %s ORDER BY %s";
    private static final String SELECT_PK_SQL_FORMAT = "SELECT %s FROM %s ORDER BY %s";

    // 实验室测试环境
    private static final boolean mock_sample =
        DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_FULL_VALID_MOCK_SAMPLE);

    /**
     * 对逻辑表进行采样，采样结果按主键有序
     * 无主键表不采样
     *
     * @return sample result, a list of primary keys(ordered by definition)
     */
    public static List<List<Object>> sample(DataSource dataSource, String dbName, String tableName,
                                            List<String> primaryKeys) throws SQLException {
        List<List<Object>> result = new ArrayList<>();

        if (CollectionUtils.isEmpty(primaryKeys)) {
            log.info("table {}.{} has no pk, will not do sample.", dbName, tableName);
            return result;
        }

        try (Connection conn = dataSource.getConnection()) {
            if (noNeedBatch(conn, dbName, tableName)) {
                if (mock_sample) {
                    result = mockSample(conn, dbName, tableName);
                    log.info("mock sample for table:{}.{}, mock data:{}", dbName, tableName, result);
                } else {
                    log.info("no need batch check");
                }
                return result;
            }

            result = doSample(conn, dbName, tableName, primaryKeys);

            if (log.isDebugEnabled()) {
                log.debug("sample result set size:{}, detail:{}", result.size(), result);
            }

            return result;
        }
    }

    private static List<List<Object>> mockSample(Connection conn, String dbName, String tbName) throws SQLException {
        List<List<Object>> res = new ArrayList<>();
        List<String> pks = ReplicaFullValidUtil.getPrimaryKey(conn, dbName, tbName);
        String pkStr = ReplicaFullValidUtil.buildPrimaryKeyStr(pks);
        String fullTableName = ReplicaFullValidUtil.buildFullTableName(dbName, tbName);
        String sql = String.format(SELECT_PK_SQL_FORMAT, pkStr, fullTableName, pkStr);
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                List<Object> row = new ArrayList<>();
                for (String pk : pks) {
                    row.add(rs.getObject(pk));
                }
                res.add(row);

                // 防止mock数据量过大
                if (res.size() > 5) {
                    break;
                }
            }
        }
        return res;
    }

    private static String buildSampleSql(String dbName, String table, List<String> pks, double percentage) {
        String pkStr = ReplicaFullValidUtil.buildPrimaryKeyStr(pks);
        String fullTableName = ReplicaFullValidUtil.buildFullTableName(dbName, table);
        return String.format(SAMPLE_SQL_FORMAT, percentage, pkStr, fullTableName, pkStr);
    }

    private static List<List<Object>> doSample(Connection conn, String dbName, String tbName, List<String> primaryKeys)
        throws SQLException {
        List<List<Object>> result = new ArrayList<>();

        long tableRows = ReplicaFullValidUtil.getTableRowsCount(conn, dbName, tbName);
        double percentage = calculateSamplePercentage(tableRows);
        long maxBatchRowsCount = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_BATCH_ROWS_COUNT);
        long batchNum = tableRows / maxBatchRowsCount;
        int pickUpInterval = (int) ((tableRows * percentage) / (batchNum * 100));

        log.info(
            "prepare to do sample table {}.{}, tableRows:{}, batch num:{}, sample percentage:{}, sample result pick up interval:{}",
            dbName, tbName, tableRows, batchNum, percentage, pickUpInterval);

        String sql = buildSampleSql(dbName, tbName, primaryKeys, percentage);
        log.info("table:{}.{}, sample sql:{}", dbName, tbName, sql);

        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            int cnt = 0;
            while (rs.next()) {
                cnt++;
                if (cnt == pickUpInterval) {
                    cnt = 0;
                    List<Object> row = new ArrayList<>();
                    for (String pk : primaryKeys) {
                        row.add(rs.getObject(pk));
                    }
                    result.add(row);
                }
            }
        }

        return result;
    }

    private static boolean noNeedBatch(Connection conn, String dbName, String tableName) {
        // IMPORTANT: 为了避免统计信息不准，校验之前先执行analyze table收集统计信息
        long tableRows = ReplicaFullValidUtil.getTableRowsCount(conn, dbName, tableName);
        long maxBatchRowsCount = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_BATCH_ROWS_COUNT);
        if (tableRows < maxBatchRowsCount) {
            return true;
        }
        long avgSize = ReplicaFullValidUtil.getTableAvgRowSize(conn, dbName, tableName);
        long maxBatchSize = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_BATCH_SIZE);
        return tableRows * avgSize < maxBatchSize;
    }

    private static double calculateSamplePercentage(long tableRows) {
        final double maxSamplePercentage =
            DynamicApplicationConfig.getDouble(ConfigKeys.RPL_FULL_VALID_MAX_SAMPLE_PERCENTAGE);
        final long maxSampleRowsCount =
            DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_SAMPLE_ROWS_COUNT);
        double calSamplePercentage = maxSampleRowsCount * 1.0f / tableRows * 100;
        if (calSamplePercentage <= 0 || calSamplePercentage > maxSamplePercentage) {
            calSamplePercentage = maxSamplePercentage;
        }
        return calSamplePercentage;
    }
}
