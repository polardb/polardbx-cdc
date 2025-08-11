/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.check.icbc;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class IcbcGdnCheckTest extends RplBaseTestCase {

    public static final String QUERY_TOTAL_META_INFO =
        "select id,job_id,source_job_id from metadb.ddl_table_meta_info where schema_name <> '__cdc__'";
    public static final String QUERY_BY_JOB_ID =
        "select * from metadb.ddl_table_meta_info where job_id = '%s'";

    public static final String QUERY_BY_ID =
        "select * from metadb.ddl_table_meta_info where id = '%s'";

    @Test
    public void testSlaveStatus() throws SQLException, InterruptedException {
        sendTokenAndWait(CheckParameter.builder().build());
        Thread.sleep(5000);
        try (Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection();
            Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery("show slave status for ddl_load");
            while (resultSet.next()) {
                Long maxId = resultSet.getLong("MAX_DDL_ID");
                Long execDdlId = resultSet.getLong("EXEC_DDL_ID");
                Long delayCount = resultSet.getLong("DELAY_COUNT");
                String errorInfo = resultSet.getString("ERROR_INFO");
                String status = resultSet.getString("STATUS");

                Assert.assertEquals("max_ddl_id is " + maxId + ", exec_ddl_id is " + execDdlId,
                    0L, delayCount.longValue());
                Assert.assertTrue(StringUtils.isBlank(errorInfo));
                Assert.assertEquals("RUNNING", status);
            }
        }
    }

    @Test
    public void testMetaDetailInfo() throws SQLException {
        List<Triple<Long, Long, Long>> upStreamCheckDataList = getUpStreamCheckDataList();
        List<Triple<Long, Long, Long>> downStreamCheckDataList = getDownStreamCheckDataList();

        Set<Long> upStreamJobIds =
            upStreamCheckDataList.stream().map(Triple::getMiddle).collect(Collectors.toSet());
        Set<Long> downStreamSourceJobIds =
            downStreamCheckDataList.stream().map(Triple::getRight).collect(Collectors.toSet());

        //check count
        String errMsg = String.format("check meta info error, up -> down diff is %s, down -> up diff is %s",
            Sets.difference(upStreamJobIds, downStreamSourceJobIds),
            Sets.difference(downStreamSourceJobIds, upStreamJobIds));
        Assert.assertEquals(errMsg, upStreamJobIds, downStreamSourceJobIds);

        // check one by one
        List<Long> downStreamFailedIds = new ArrayList<>();
        List<Long> upStreamFailedJobIds = new ArrayList<>();
        Map<Long, List<Pair<String, String>>> diff = new HashMap<>();

        for (Triple<Long, Long, Long> triple : downStreamCheckDataList) {
            DdlTableMetaInfoRecord record1 = getDdlTableMetaInfoRecordById(triple.getLeft());
            DdlTableMetaInfoRecord record2 = getDdlTableMetaInfoRecordByJobId(triple.getRight());
            assertEquals(record1, record2, diff, downStreamFailedIds, upStreamFailedJobIds, triple);
        }

        Assert.assertEquals(
            "downstream failed ids is " + downStreamFailedIds + "\n" +
                "corresponding upstream failed job ids is " + upStreamFailedJobIds + "\n" +
                "diff detail is \n" + JSONObject.toJSONString(diff, true),
            0, downStreamFailedIds.size());
        log.info("check done, check ddl count is " + downStreamCheckDataList.size());
    }

    private void assertEquals(DdlTableMetaInfoRecord record1,
                              DdlTableMetaInfoRecord record2,
                              Map<Long, List<Pair<String, String>>> diff,
                              List<Long> downStreamFailedIds,
                              List<Long> upStreamFailedJobIds,
                              Triple<Long, Long, Long> triple) {
        if (StringUtils.equals(record1.getTableMetaInfo(), "SKIP")) {
            return;
        }

        if (!StringUtils.equals(record1.getTableMetaInfo(), record2.getTableMetaInfo())) {
            String[] metaInfoArray1 = record1.getTableMetaInfo().split(",");
            String[] metaInfoArray2 = record2.getTableMetaInfo().split(",");

            List<Pair<String, String>> diffList = new ArrayList<>();
            for (int i = 0; i < metaInfoArray1.length; i++) {
                String str1 = metaInfoArray1[i];
                String str2 = metaInfoArray2[i];

                if (!StringUtils.equals(str1, str2)) {
                    Pair<String, String> pair = Pair.of(str1, str2);
                    diffList.add(pair);
                }
            }

            diff.put(triple.getLeft(), diffList);
            downStreamFailedIds.add(triple.getLeft());
            upStreamFailedJobIds.add(triple.getRight());

            log.error("check one record error " + triple);
        }
    }

    private List<Triple<Long, Long, Long>> getDownStreamCheckDataList() throws SQLException {
        try (Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection()) {
            return getTotalMetaInfoList(connection);
        }
    }

    private List<Triple<Long, Long, Long>> getUpStreamCheckDataList() throws SQLException {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            return getTotalMetaInfoList(connection);
        }
    }

    private List<Triple<Long, Long, Long>> getTotalMetaInfoList(Connection connection) throws SQLException {
        List<Triple<Long, Long, Long>> list = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet resultSet = stmt.executeQuery(QUERY_TOTAL_META_INFO);
            while (resultSet.next()) {
                list.add(Triple.of(resultSet.getLong(1), resultSet.getLong(2), resultSet.getLong(3)));
            }
        }

        return list;
    }

    private DdlTableMetaInfoRecord getDdlTableMetaInfoRecordByJobId(Long jobId) throws SQLException {
        DdlTableMetaInfoRecord record = new DdlTableMetaInfoRecord();
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(String.format(QUERY_BY_JOB_ID, jobId));
            if (resultSet.next()) {
                return record.fill(resultSet);
            }
        }

        throw new SQLException("can`t find ddl_table_meta_info record by jobId:" + jobId);
    }

    private DdlTableMetaInfoRecord getDdlTableMetaInfoRecordById(Long id) throws SQLException {
        DdlTableMetaInfoRecord record = new DdlTableMetaInfoRecord();
        try (Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection();
            Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(String.format(QUERY_BY_ID, id));
            if (resultSet.next()) {
                return record.fill(resultSet);
            }
        }

        throw new SQLException("can`t find ddl_table_meta_info record by id:" + id);
    }
}
