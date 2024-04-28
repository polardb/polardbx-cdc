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
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.rpl.validation.common.ValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2024/1/17 11:50
 **/
@Slf4j
public class ValidationSampler {

    private static final String SAMPLE_SQL_FORMAT =
        "/*+TDDL:cmd_extra(sample_percentage=%f,enable_push_sort=false,merge_union_size=1,enable_post_planner=false,enable_direct_plan=false)*/ SELECT %s FROM %s ORDER BY %s";

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
            throw new UnsupportedOperationException("Not Support Check Table without Primary Key!");
        }

        try (Connection conn = dataSource.getConnection()) {
            try {
                if (noNeedBatch(conn, dbName, tableName)) {
                    log.info("no need batch check");
                    return result;
                }
            } catch (SQLException e) {
                log.warn("failed to query statistic information!");
            }

            result = doSample(conn, dbName, tableName, primaryKeys);

            log.info("sample result size:{}", result.size());

            return result;
        }
    }

    private static String buildSampleSql(String dbName, String table, List<String> pks, double percentage) {
        String pkStr = ValidationUtil.buildPrimaryKeyStr(pks);
        String fullTableName = ValidationUtil.buildFullTableName(dbName, table);
        return String.format(SAMPLE_SQL_FORMAT, percentage, pkStr, fullTableName, pkStr);
    }

    private static List<List<Object>> doSample(Connection conn, String dbName, String tbName,
                                               List<String> primaryKeys)
        throws SQLException {
        List<List<Object>> result = new ArrayList<>();

        long totalCount;
        try {
            totalCount = ValidationUtil.getTableRowsCount(conn, dbName, tbName);
        } catch (SQLException e) {
            log.warn("will do count manually!");
            String sql = String.format("SELECT COUNT(1) FROM `%s`.`%s`", dbName, tbName);
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    totalCount = rs.getLong(1);
                } else {
                    throw new SQLException("failed to execute count!");
                }
            }
        }

        double percentage = calculateSamplePercentage(totalCount);
        long batchSize = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_BATCH_SIZE);
        long batchNum = totalCount / batchSize;
        int pickUpInterval = (int) ((totalCount * percentage) / (batchNum * 100));

        log.info(
            "prepare to do sample table {}.{}, tableRows:{}, batch num:{}, sample percentage:{}, sample result pick up interval:{}",
            dbName, tbName, totalCount, batchNum, percentage, pickUpInterval);

        String sql = buildSampleSql(dbName, tbName, primaryKeys, percentage);
        log.info("table:{}.{}, sample sql:{}", dbName, tbName, sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY)) {
            conn.setAutoCommit(false);
            stmt.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = stmt.executeQuery()) {
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
        }

        return result;
    }

    private static boolean noNeedBatch(Connection conn, String dbName, String tableName) throws SQLException {
        long tableRows = ValidationUtil.getTableRowsCount(conn, dbName, tableName);
        long avgSize = ValidationUtil.getTableAvgRowSize(conn, dbName, tableName);
        long minBatchRowsCount = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MIN_BATCH_ROWS_COUNT);
        if (tableRows < minBatchRowsCount) {
            log.info("table rows:{} is smaller than min batch:{}", tableRows, minBatchRowsCount);
            return true;
        }
        long minBatchByteSize = DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MIN_BATCH_BYTE_SIZE);
        long totalSize = tableRows * avgSize;
        if (totalSize < minBatchByteSize) {
            log.info("total size:{} is smaller than min batch size:{}", totalSize, minBatchByteSize);
            return true;
        }

        return false;
    }

    private static double calculateSamplePercentage(long totalCount) {
        final double maxSamplePercentage =
            DynamicApplicationConfig.getDouble(ConfigKeys.RPL_FULL_VALID_MAX_SAMPLE_PERCENTAGE);
        final long maxSampleRowsCount =
            DynamicApplicationConfig.getLong(ConfigKeys.RPL_FULL_VALID_MAX_SAMPLE_ROWS_COUNT);
        double calSamplePercentage = maxSampleRowsCount * 1.0f / totalCount * 100;
        if (calSamplePercentage <= 0 || calSamplePercentage > maxSamplePercentage) {
            calSamplePercentage = maxSamplePercentage;
        }
        return calSamplePercentage;
    }

}
