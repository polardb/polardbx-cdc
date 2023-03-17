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
package com.aliyun.polardbx.cdc.qatest.check;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.cdc.qatest.base.CheckParameter;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.StreamHashUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author yudong
 * @since 2022/9/18
 **/
@Slf4j
public class DataConsistencyTest extends RplBaseTestCase {
    // 处理无主键表重复行
    private static final String CALCULATE_CHECKSUM_SQL_FORMAT =
        "SELECT BIT_XOR( "
            + "CAST( CRC32( CONCAT_WS( %s ) ) AS UNSIGNED )"
            + ") AS checksum FROM `%s`.`%s`";
    private static final String CALCULATE_CHECKSUM_WITH_IN_SQL_FORMAT =
        "SELECT BIT_XOR( "
            + "CAST( CRC32( CONCAT_WS( %s ) ) AS UNSIGNED )"
            + ") AS checksum FROM `%s`.`%s` "
            + "WHERE (%s) IN (%s)";

    private static final int STREAM_NUM = 3;
    private static final int SOURCE_DS = 0;
    private static final int SYNC_DS = 1;
    private static final int SYNC_FIRST_DS = 2;
    private static final int SYNC_SECOND_DS = 3;
    private static final int SYNC_THIRD_DS = 4;

    private final ThreadLocal<DetailReport>
        threadLocalReport = new ThreadLocal<>();
    private final SummaryReport
        testSummary = new SummaryReport();

    @Test
    public void forwardCheckTest() throws SQLException {
        sendTokenAndWait(CheckParameter.builder().build());
        check(SOURCE_DS);
        if (testSummary.getFailedTableCount() > 0) {
            log.info("failed tables num:{}", testSummary.getFailedTableCount());
            log.info("failed tables:{}", testSummary.getFailedTables());
            for (DetailReport report : testSummary.getFailedTableReports()) {
                log.info(report.toString());
            }
        }
        Assert.assertEquals(0, testSummary.getFailedTableCount());
    }

    @Test
    public void backwardCheckTest() throws SQLException {
        sendTokenAndWait(CheckParameter.builder().build());
        if (usingBinlogX) {
            if (StreamHashUtil.getDefaultHashLevel() == HashLevel.RECORD) {
                check(SYNC_FIRST_DS);
            } else {
                check(SYNC_FIRST_DS);
                check(SYNC_SECOND_DS);
                check(SYNC_THIRD_DS);
            }
        } else {
            check(SYNC_DS);
        }
        if (testSummary.getFailedTableCount() > 0) {
            log.info("failed tables num:{}", testSummary.getFailedTableCount());
            log.info("failed tables:{}", testSummary.getFailedTables());
            for (DetailReport report : testSummary.getFailedTableReports()) {
                log.info(report.toString());
            }
        }
        Assert.assertEquals(0, testSummary.getFailedTableCount());
    }

    /**
     * 校验polarx和下游Mysql中库表数据是否一致
     *
     * @param srcDs 取srcDs中的表来校验
     */
    public void check(int srcDs) throws SQLException {
        List<Pair<String, String>> testTables = getTestTables(srcDs);
        testSummary.setTotalTableCount(testTables.size());
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        ExecutorCompletionService<DetailReport> completionService =
            new ExecutorCompletionService<>(executorService);
        for (Pair<String, String> tablePair : testTables) {
            futures.add(completionService.submit(
                () -> checkTable(tablePair.getKey(), tablePair.getValue())));
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                DetailReport report = completionService.take().get();
                if (!report.isSuccess()) {
                    testSummary.addFailedTableCount();
                    testSummary.addFailedTables(report.getTable());
                    testSummary.addFailedReport(report);
                }
            } catch (Throwable e) {
                testSummary.addFailedTableCount();
                log.error("check failed exception ", e);
            }
        }
    }

    public DetailReport checkTable(String db, String table) throws Exception {
        try {
            if (log.isDebugEnabled()) {
                log.debug("start to check table {}.{}", db, table);
            }
            DetailReport report = new DetailReport();
            threadLocalReport.set(report);
            report.setTable(db + "." + table);
            if (!checkColumns(db, table)) {
                report.setSuccess(false);
                report.setReason("check columns failed");
                return report;
            }
            if (!checkRows(db, table)) {
                report.setSuccess(false);
                report.setReason("check rows failed");
                return report;
            }
            if (log.isDebugEnabled()) {
                log.debug("table {}.{} check finished", db, table);
            }
            report.setSuccess(true);
            return report;
        } catch (Throwable t) {
            throw new PolardbxException(String.format("check error %s:%s", db, table), t);
        }
    }

    /**
     * 获得所有需要校验的表名
     *
     * @param srcDs 取srcDs中的表
     */
    private List<Pair<String, String>> getTestTables(int srcDs)
        throws SQLException {
        List<Pair<String, String>> testTables = new ArrayList<>();

        String checkTableWhiteList = PropertiesUtil.getCdcCheckTableWhiteList();
        if (StringUtils.isNotBlank(checkTableWhiteList)) {
            List<String> tables = new ArrayList<>(
                Arrays.asList(checkTableWhiteList.split(";")));
            for (String str : tables) {
                str = str.trim();
                int idx = str.indexOf('.');
                String db = str.substring(0, idx);
                String tb = str.substring(idx + 1);
                Pair<String, String> pair = Pair.of(db, tb);
                if (!filterTable(pair)) {
                    testTables.add(Pair.of(db, tb));
                }
            }
            return testTables;
        }

        List<String> filteredTables = new ArrayList<>();
        List<String> databases = getDatabaseList(srcDs);
        for (String db : databases) {
            if (filterDatabase(db)) {
                log.info("database [{}] is filtered for check", db);
                continue;
            }

            List<String> tables = getTableList(db, srcDs);
            for (String tb : tables) {
                Pair<String, String> tablePair = Pair.of(db, tb);
                if (filterTable(tablePair)) {
                    log.info("table [{}.{}] is filtered for check",
                        tablePair.getKey(), tablePair.getValue());
                    filteredTables.add(StringUtils.lowerCase(
                        tablePair.getKey() + "." + tablePair.getValue()));
                    continue;
                }
                testTables.add(tablePair);
            }
        }
        log.info("filtered tables count: " + filteredTables.size());
        log.info("filtered tables list: " + filteredTables);
        return testTables;
    }

    private boolean filterDatabase(String database) {
        String checkDbBlackList = PropertiesUtil.getCdcCheckDbBlackList();
        if (StringUtils.isNotBlank(checkDbBlackList)) {
            String[] blackList = StringUtils.split(
                StringUtils.lowerCase(checkDbBlackList), ";");
            for (String db : blackList) {
                if (StringUtils.equalsIgnoreCase(database, db)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean filterTable(Pair<String, String> tablePair) {
        String database = tablePair.getKey();
        String table = tablePair.getValue();
        String fullTable = StringUtils.lowerCase(database + "." + table);

        String checkTableBlackList = PropertiesUtil.getCdcCheckTableBlackList();
        if (StringUtils.isNotBlank(checkTableBlackList)) {
            String[] patterns = StringUtils.split(
                StringUtils.lowerCase(checkTableBlackList), ";");
            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(fullTable);
                if (matcher.find()) {
                    log.info("table is filtered, table name is {}, "
                        + "with pattern {}", fullTable, patternStr);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 校验远端和目标端指定表的列名是否相同
     *
     * @param tb 待校验表
     * @return 列名一致则返回true;否则返回false
     */
    private boolean checkColumns(String db, String tb) throws SQLException {
        boolean result;
        if (usingBinlogX) {
            if (StreamHashUtil.getHashLevel(db, tb) != HashLevel.RECORD) {
                int streamSeq = StreamHashUtil.getHashStreamSeq(db, tb);
                result = checkColumnsHelper(db, tb, getSyncDbByStreamSeq(streamSeq));
            } else {
                result = checkColumnsHelper(db, tb, SYNC_FIRST_DS);
                result &= checkColumnsHelper(db, tb, SYNC_SECOND_DS);
                result &= checkColumnsHelper(db, tb, SYNC_THIRD_DS);
            }
        } else {
            result = checkColumnsHelper(db, tb, SYNC_DS);
        }
        return result;
    }

    private int getSyncDbByStreamSeq(int streamSeq) {
        if (streamSeq == 0) {
            return SYNC_FIRST_DS;
        } else if (streamSeq == 1) {
            return SYNC_SECOND_DS;
        } else if (streamSeq == 2) {
            return SYNC_THIRD_DS;
        } else {
            throw new PolardbxException("invalid stream seq " + streamSeq);
        }
    }

    /**
     * 检查同一张表在源端和目标段的列是否一致
     */
    private boolean checkColumnsHelper(String db, String tb, int destDs)
        throws SQLException {
        List<String> srcColumns = getSrcColumnList(db, tb);
        srcColumns = srcColumns.stream().map(String::toLowerCase)
            .collect(Collectors.toList());
        List<String> dstColumns = getDstColumnsList(destDs, db, tb);
        dstColumns = dstColumns.stream().map(String::toLowerCase)
            .collect(Collectors.toList());
        return ListUtils.isEqualList(srcColumns, dstColumns);
    }

    /**
     * 校验远端和目标端指定表中数据是否相同
     * 将表中所有数据拼接成一个字符串，计算字符串的哈希值，比较哈希值来确定数据是否相同
     *
     * @param db db name
     * @param table table name
     * @return 数据是否一致
     */
    private boolean checkRows(String db, String table) throws Exception {
        List<String> srcCheckSum = calculateSrcCheckSum(db, table);
        List<String> dstCheckSum = calculateDstCheckSum(db, table);
        DetailReport report = threadLocalReport.get();
        report.setSrcChecksum(srcCheckSum);
        report.setDstChecksum(dstCheckSum);
        return ListUtils.isEqualList(srcCheckSum, dstCheckSum);
    }

    /**
     * 计算polarx中给定表的哈希值，如果是多流模式，为每个流计算一个哈希值
     * 特殊情况:
     * 1.表为空,splitTable已经处理这种情况
     * 2.表没有主键,默认所有的数据都映射到第一个流中,
     * 这种情况下不用再对polarx做splitTable处理,
     * 计算checksum的SQL后面不需要where in条件.
     * 如果表为空,下游mysql计算checksum的结果为"0",
     * 所以此函数返回结果包括一个checksum和两个"0".
     *
     * @param db 待校验表所在的库
     * @param table 待校验表的表名
     * @return 表中数据的哈希值
     */
    private List<String> calculateSrcCheckSum(String db, String table)
        throws Exception {
        List<String> result = new ArrayList<>();
        DetailReport report = threadLocalReport.get();
        if (usingBinlogX && StreamHashUtil.getHashLevel(db, table) == HashLevel.RECORD) {
            List<String> hashKeys = getSrcPrimaryKeys(db, table);
            if (hashKeys.isEmpty()) {
                // 无主键表使用隐藏主键进行hash
                hashKeys.add("_drds_implicit_id_");
            }
            List<String> inList = splitTable(db, table, hashKeys);
            List<String> columns = getSrcColumnList(db, table);
            try (Connection conn = getPolardbxConnection()) {
                for (String in : inList) {
                    if ("NULL".equals(in)) {
                        result.add("0");
                        report.addSrcChecksumSQL("0");
                    } else {
                        String calculateSql = buildCheckSumWithInSql(db, table, hashKeys, columns, in);
                        report.addSrcChecksumSQL(calculateSql);
                        String checksum = calculateCheckSumHelper(conn, calculateSql);
                        result.add(checksum);
                    }
                }
            }
        } else {
            List<String> columns = getSrcColumnList(db, table);
            String calculateSql = buildCheckSumSql(db, table, columns);
            report.addSrcChecksumSQL(calculateSql);
            try (Connection conn = getPolardbxConnection()) {
                String checksum = calculateCheckSumHelper(conn, calculateSql);
                result.add(checksum);
            }
        }
        return result;
    }

    /**
     * 计算下游mysql中给定表中所有数据的哈希值
     *
     * @param db 待校验表所在的库
     * @param table 待校验表
     * @return 哈希值，如果是多流场景，下游有三个mysql，对每个mysql计算出一个哈希值
     */
    private List<String> calculateDstCheckSum(String db, String table)
        throws Exception {
        List<String> result = new ArrayList<>();
        DetailReport report = threadLocalReport.get();

        if (usingBinlogX) {
            if (StreamHashUtil.getHashLevel(db, table) != HashLevel.RECORD) {
                int streamSeq = StreamHashUtil.getHashStreamSeq(db, table);
                List<String> columns = getDstColumnsList(getSyncDbByStreamSeq(streamSeq), db, table);
                String calculateSql = buildCheckSumSql(db, table, columns);
                report.addDstChecksumSQL(calculateSql);
                try (Connection conn = getDruidConnection(getSyncDbByStreamSeq(streamSeq))) {
                    String checksum = calculateCheckSumHelper(conn, calculateSql);
                    result.add(checksum);
                }
            } else {
                List<String> columns = getDstColumnsList(SYNC_FIRST_DS, db, table);
                String calculateSql = buildCheckSumSql(db, table, columns);
                report.addDstChecksumSQL(calculateSql);
                try (Connection conn = getCdcSyncDbConnectionFirst()) {
                    String checksum = calculateCheckSumHelper(conn, calculateSql);
                    result.add(checksum);
                }
                try (Connection conn = getCdcSyncDbConnectionSecond()) {
                    String checksum = calculateCheckSumHelper(conn, calculateSql);
                    result.add(checksum);
                }
                try (Connection conn = getCdcSyncDbConnectionThird()) {
                    String checksum = calculateCheckSumHelper(conn, calculateSql);
                    result.add(checksum);
                }
            }
        } else {
            List<String> columns = getDstColumnsList(SYNC_DS, db, table);
            String calculateSql = buildCheckSumSql(db, table, columns);
            report.addSrcChecksumSQL(calculateSql);
            try (Connection conn = getPolardbxConnection()) {
                String checksum = calculateCheckSumHelper(conn, calculateSql);
                result.add(checksum);
            }
        }

        return result;
    }

    private String calculateCheckSumHelper(Connection conn, String calculateSql)
        throws SQLException {
        try (ResultSet rs = JdbcUtil.executeQuerySuccess(conn, calculateSql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return null;
    }

    /**
     * 多流场景，需要将polarx中单表分割成三个子表，对应下游的三个mysql，分别计算哈希值
     * 所以需要对计算哈希值的SQL设置 where in 条件，in后面是该流中所有行的主键集合
     * 这个函数的作用就是得到in后面需要拼接的字符串
     * 特殊情况:
     * 1.映射结束后某个流中没有数据 则针对该流返回一个"NULL"
     * 2.表为空,三个流中都没有数据,则根据1,返回三个"NULL"
     *
     * @param db 待校验表所在的库
     * @param table 待校验表
     * @return 三个字符串，每个字符串对应一个流，字符串中是该流中所有行的主键
     */
    private List<String> splitTable(String db, String table, List<String> hashKeys) throws Exception {
        List<String> result = new ArrayList<>();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
        List<StringBuilder> pksLists = new ArrayList<>();
        for (int i = 0; i < STREAM_NUM; i++) {
            pksLists.add(new StringBuilder());
        }

        String hashKeysStr = getEscapedColumns(hashKeys);
        String selectSql =
            String.format("SELECT %s FROM `%s`.`%s` ORDER by %s", hashKeysStr, escape(db), escape(table), hashKeysStr);
        Map<String, String> name2Type = getSrcColumnTypeMap(db, table);
        Map<String, String> name2Charset = getSrcColumnCharsetMap(db, table);
        String defaultCharset = getDefaultCharset(db, table);

        try (Connection conn = getPolardbxConnection();
            ResultSet rs = JdbcUtil.executeQuerySuccess(conn, selectSql)) {
            while (rs.next()) {
                baos.reset();
                StringBuilder hashKeysValues = new StringBuilder("(");
                for (String name : hashKeys) {
                    String type = name2Type.get(name);
                    // 隐藏主键类型bigint(20)
                    if (type == null) {
                        type = "bigint(20)";
                    }
                    boolean unsigned = StringUtils.containsIgnoreCase(type, "unsigned");
                    String charset = name2Charset.get(name);
                    if (charset == null) {
                        charset = defaultCharset;
                    }
                    String data = rs.getString(name);
                    hashKeysValues.append("'").append(data).append("'").append(",");
                    Field field = MakeFieldFactory.makeField(
                        type, data, charset, false, unsigned);
                    baos.write(field.encode());
                }

                byte[] bytes = baos.toByteArray();
                int streamId = bytes2stream(bytes);
                hashKeysValues.setCharAt(hashKeysValues.length() - 1, ')');
                pksLists.get(streamId).append(hashKeysValues).append(",");
            }
        }

        for (int i = 0; i < STREAM_NUM; i++) {
            StringBuilder sb = pksLists.get(i);
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            } else {
                sb.append("NULL");
            }
            result.add(sb.toString());
        }

        return result;
    }

    private boolean hasNoPrimaryKeys(String db, String table) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            List<String> pks = JdbcUtil.getPrimaryKeyNames(conn, db, table);
            return CollectionUtils.isEmpty(pks);
        }
    }

    private List<String> getDatabaseList(int ds) throws SQLException {
        try (Connection conn = getDruidConnection(ds)) {
            return JdbcUtil.showDatabases(conn);
        }
    }

    private List<String> getTableList(String database, int ds) throws SQLException {
        try (Connection conn = getDruidConnection(ds)) {
            return JdbcUtil.showTables(conn, database);
        }
    }

    private Connection getDruidConnection(int n) {
        Connection conn = null;
        switch (n) {
        case 0:
            conn = getPolardbxConnection();
            break;
        case 1:
            conn = getCdcSyncDbConnection();
            break;
        case 2:
            conn = getCdcSyncDbConnectionFirst();
            break;
        case 3:
            conn = getCdcSyncDbConnectionSecond();
            break;
        case 4:
            conn = getCdcSyncDbConnectionThird();
            break;
        default:
            log.error("mysql number is {} not expected", n);
        }
        return conn;
    }

    private List<String> getSrcColumnList(String db, String tb) throws SQLException {
        return getColumnsListHelper(SOURCE_DS, db, tb);
    }

    private List<String> getDstColumnsList(int ds, String db, String tb) throws SQLException {
        return getColumnsListHelper(ds, db, tb);
    }

    private List<String> getColumnsListHelper(int ds, String db, String tb) throws SQLException {
        try (Connection conn = getDruidConnection(ds)) {
            return JdbcUtil.getColumnNamesByDesc(conn, db, tb);
        }
    }

    private List<String> getSrcPrimaryKeys(String db, String tb) throws SQLException {
        return getPrimaryKeysHelper(SOURCE_DS, db, tb);
    }

    private List<String> getDstPrimaryKeys(int ds, String db, String tb) throws SQLException {
        return getPrimaryKeysHelper(ds, db, tb);
    }

    private List<String> getPrimaryKeysHelper(int ds, String db, String tb) throws SQLException {
        try (Connection conn = getDruidConnection(ds)) {
            return JdbcUtil.getPrimaryKeyNames(conn, db, tb);
        }
    }

    private Map<String, String> getSrcColumnTypeMap(
        String db, String tb) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.getColumnTypesByDesc(conn, db, tb);
        }
    }

    private Map<String, String> getSrcColumnCharsetMap(String db, String tb) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            return JdbcUtil.getColumnCharsetMap(conn, db, tb);
        }
    }

    private String getDefaultCharset(String db, String tb) throws SQLException {
        try (Connection conn = getPolardbxConnection()) {
            String defaultCharacter = JdbcUtil.getTableCharset(conn, db, tb);
            defaultCharacter = defaultCharacter == null
                ? JdbcUtil.getDatabaseCharset(conn, db) : defaultCharacter;
            return defaultCharacter == null ? "utf8mb4" : defaultCharacter;
        }
    }

    private int bytes2stream(byte[] bytes) {
        return Math.abs(Arrays.hashCode(bytes) % STREAM_NUM);
    }

    private String buildCheckSumSql(String db, String table, List<String> columns) {
        String concatStr = buildConcatString(columns);
        return String.format(CALCULATE_CHECKSUM_SQL_FORMAT,
            concatStr, escape(db), escape(table));
    }

    private String buildConcatString(List<String> columns) {
        StringBuilder concatWsSb = new StringBuilder();
        concatWsSb.append("',', ");
        for (String column : columns) {
            if (needConvertToByte()) {
                concatWsSb.append(String.format("convert(`%s` using byte), ", escape(column)));
            } else {
                concatWsSb.append(String.format("`%s`, ", escape(column)));
            }
        }

        StringBuilder concatSb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", escape(columns.get(i))));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", escape(columns.get(i))));
            }
        }

        concatWsSb.append(concatSb);
        return concatWsSb.toString();
    }

    private boolean needConvertToByte() {
        // todo @yudong
        return false;
    }

    private String buildCheckSumWithInSql(String db, String table, List<String> pks, List<String> columns, String in) {
        String pksStr = getEscapedColumns(pks);
        String concatStr = buildConcatString(columns);
        return String.format(CALCULATE_CHECKSUM_WITH_IN_SQL_FORMAT,
            concatStr, escape(db), escape(table), pksStr, in);
    }

    /**
     * 将列名用``包裹，防止列名为数字时，select语句出错
     * 列名1 -> `列名1`, `列名2` -> ```列名2```
     */
    private String getEscapedColumns(List<String> columns) {
        List<String> escapedColumns = columns.stream().map(c -> '`' + escape(c) + '`').collect(Collectors.toList());
        return String.join(",", escapedColumns);
    }

    @Data
    private static class DetailReport {
        private boolean success;
        private String table;
        private String reason;
        private List<String> srcChecksum = new ArrayList<>();
        private List<String> dstChecksum = new ArrayList<>();
        private List<String> srcChecksumSQL = new ArrayList<>();
        private List<String> dstChecksumSQL = new ArrayList<>();

        public void addSrcChecksumSQL(String sql) {
            srcChecksumSQL.add(sql);
        }

        public void addDstChecksumSQL(String sql) {
            dstChecksumSQL.add(sql);
        }

        @Override
        public String toString() {
            return String.format("table: %s\n"
                    + "failed reason:%s\n"
                    + "srcChecksum:%s\n"
                    + "dstChecksum:%s\n"
                    + "src checksum SQL:%s\n"
                    + "dst checksum SQL:%s\n",
                table, reason, srcChecksum, dstChecksum,
                srcChecksumSQL.toString(), dstChecksumSQL.toString());
        }
    }

    @Data
    private static class SummaryReport {
        private int totalTableCount;
        private int failedTableCount = 0;
        List<String> failedTables = new ArrayList<>();
        List<DetailReport> failedTableReports = new ArrayList<>();

        public void addFailedTableCount() {
            failedTableCount++;
        }

        public void addFailedReport(DetailReport report) {
            failedTableReports.add(report);
        }

        public void addFailedTables(String table) {
            failedTables.add(table);
        }
    }
}
