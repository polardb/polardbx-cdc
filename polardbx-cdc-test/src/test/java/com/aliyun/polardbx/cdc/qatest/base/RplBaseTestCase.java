/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.DirectLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.CDC_LINK_BREAKER_ENABLED;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.CDC_WAIT_TOKEN_TIMEOUT_MINUTES;
import static com.aliyun.polardbx.cdc.qatest.base.JdbcUtil.checkIfTableNotExistError;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.configProp;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getCompareDetailParallelism;
import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RplBaseTestCase extends BaseTestCase {
    protected static final String TOKEN_DB = "cdc_token_db";

    protected static final String CDC_COMMON_TEST_DB = "cdc_common_test_db";

    protected static final String TOKEN_DB_CREATE_SQL = "CREATE DATABASE IF NOT EXISTS `" + TOKEN_DB + "`";

    protected static final String TOKEN_TABLE_PREFIX = "t_token_";

    protected static final String TOKEN_TABLE_CREATE_SQL =
        "create table if not exists " + TOKEN_DB + ".`%s` (id bigint not null,primary key(`id`))";

    private static final String TTL_INFO_QUERY =
        "select * from `ttl_info` where `table_schema` = '%s' and `table_name` = '%s'";
    private static final String LAB_EVENT_QUERY =
        "select * from `binlog_lab_event` where `event_type` = %s";

    /**
     * CDC链路熔断标记：上游binlog链路中断或下游replica位点失效后，wait token必然超时，
     * 由第一个超时的case在loopWait内完成归因并打开熔断，后续case直接快速失败，
     * 避免超时时间(默认20~30分钟)按case数量累加导致实验室整体耗时膨胀
     */
    private static volatile boolean cdcLinkBroken = false;
    private static volatile String cdcLinkBrokenReason = null;

    private static final String LINK_BREAKER_LOG_PREFIX = "[CDC-LINK-BREAKER]";
    private static final long LINK_BREAKER_SAMPLE_INTERVAL_MS = 30_000;
    private static final String REASON_UPSTREAM_NOT_PROGRESSING = "UPSTREAM_NOT_PROGRESSING";
    private static final String REASON_REPLICA_POSITION_INVALID = "REPLICA_POSITION_INVALID";

    protected Connection polardbxConnection;
    protected Connection cdcSyncDbConnection;
    protected Connection cdcSyncDbConnectionFirst;
    protected Connection cdcSyncDbConnectionSecond;
    protected Connection cdcSyncDbConnectionThird;

    protected JdbcTemplate polardbxJdbcTemplate;
    protected JdbcTemplate cdcSyncDbJdbcTemplate;
    protected JdbcTemplate cdcSyncDbFirstJdbcTemplate;
    protected JdbcTemplate cdcSyncDbSecondJdbcTemplate;
    protected JdbcTemplate cdcSyncDbThirdJdbcTemplate;

    protected ExecutorService compareDetailExecutorService =
        Executors.newFixedThreadPool(getCompareDetailParallelism());

    @BeforeClass
    public static void beforeClass() throws SQLException {
        prepareCdcTokenDB();
    }

    @Before
    public void before() throws SQLException {
        this.polardbxConnection = getPolardbxConnection();
        this.polardbxJdbcTemplate = new JdbcTemplate(ConnectionManager.getInstance().getPolardbxDataSource());
        if (usingBinlogX) {
            this.cdcSyncDbConnectionFirst = getCdcSyncDbConnectionFirst();
            this.cdcSyncDbConnectionSecond = getCdcSyncDbConnectionSecond();
            this.cdcSyncDbConnectionThird = getCdcSyncDbConnectionThird();
            this.cdcSyncDbFirstJdbcTemplate =
                new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSourceFirst());
            this.cdcSyncDbSecondJdbcTemplate =
                new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSourceSecond());
            this.cdcSyncDbThirdJdbcTemplate =
                new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSourceThird());
        } else {
            this.cdcSyncDbConnection = getCdcSyncDbConnection();
            this.cdcSyncDbJdbcTemplate = new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSource());
        }
    }

    public static void prepareCdcTokenDB() throws SQLException {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeSuccess(polardbxConnection, TOKEN_DB_CREATE_SQL);
        }
    }

    public static void prepareTestDatabase(String database) throws SQLException {
        try (Connection polardbxConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeSuccess(polardbxConnection, "DROP DATABASE IF EXISTS `" + database + "`");
            log.info("/*MASTER*/DROP DATABASE IF EXISTS `" + database + "`");

            JdbcUtil.executeSuccess(polardbxConnection, "CREATE DATABASE IF NOT EXISTS `" + database + "`");
            log.info("/*MASTER*/CREATE DATABASE IF NOT EXISTS `" + database + "`");
        }
    }

    public void waitAndCheck(CheckParameter checkParameter) {
        //wait
        sendTokenAndWait(checkParameter);

        //execute callback
        check(checkParameter);
    }

    public void sendTokenAndWait(CheckParameter checkParameter) {
        // 熔断已打开时快速失败，省去建token表的开销
        checkCdcLinkBreaker("sendTokenAndWait");

        //send token
        String uuid = UUID.randomUUID().toString();
        String tableName = TOKEN_TABLE_PREFIX + uuid;
        JdbcUtil.executeSuccess(polardbxConnection, String.format(TOKEN_TABLE_CREATE_SQL, tableName));

        //wait token
        if (usingBinlogX) {
            loopWait(tableName, cdcSyncDbConnectionFirst, checkParameter.getLoopWaitTimeoutMs());
            loopWait(tableName, cdcSyncDbConnectionSecond, checkParameter.getLoopWaitTimeoutMs());
            loopWait(tableName, cdcSyncDbConnectionThird, checkParameter.getLoopWaitTimeoutMs());
        } else {
            loopWait(tableName, cdcSyncDbConnection, checkParameter.getLoopWaitTimeoutMs());
        }
    }

    @SneakyThrows
    public void loopWait(String token, Connection connection, long timeout) {
        checkCdcLinkBreaker(token);

        if (timeout <= 0) {
            int waitTimeMinute = Integer.parseInt(configProp.getProperty(CDC_WAIT_TOKEN_TIMEOUT_MINUTES, "20"));
            timeout = waitTimeMinute * 60 * 1000;
        }

        long fatalErrorCount = 0;
        long startTime = System.currentTimeMillis();
        long lastSampleTime = 0;
        List<String> masterStatusSamples = new ArrayList<>();
        List<Pair<Boolean, String>> slaveStatusSamples = new ArrayList<>();
        while (true) {
            try {
                Statement statement = connection.createStatement();
                statement.executeQuery("show create table `" + TOKEN_DB + "`.`" + token + "`");
                break;
            } catch (Throwable e) {
                if (checkIfTableNotExistError(e.getMessage()) || e.getMessage()
                    .contains("Unknown database 'cdc_token_db'")) {
                    fatalErrorCount = 0;
                } else {
                    fatalErrorCount++;
                }

                if (fatalErrorCount > 10) {
                    log.error("loop wait fatal error", e);
                    throw e;
                }
            }

            // 等token期间周期性采样上下游链路状态，超时后用于归因，不引入额外的串行等待
            if (isLinkBreakerEnabled()
                && System.currentTimeMillis() - lastSampleTime >= LINK_BREAKER_SAMPLE_INTERVAL_MS) {
                masterStatusSamples.add(sampleUpstreamMasterStatus());
                slaveStatusSamples.add(sampleDownstreamSlaveStatus(connection));
                lastSampleTime = System.currentTimeMillis();
            }

            long waitTime = System.currentTimeMillis() - startTime;
            if (waitTime > timeout) {
                String brokenReason = diagnoseBrokenLink(token, masterStatusSamples, slaveStatusSamples);
                throw new PolardbxException(String.format("loop wait timeout for table %s, wait time is %s, "
                        + "timeout value is %s%s", token, waitTime, timeout,
                    brokenReason == null ? "" : ", cdc link diagnosed as " + brokenReason));
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * CDC链路熔断检查：熔断打开后wait token必然超时，直接快速失败，避免每个case都等满超时时间
     */
    private static void checkCdcLinkBreaker(String tokenDesc) {
        if (cdcLinkBroken) {
            log.warn("{} skip waiting token [{}], circuit breaker is open, reason: {}",
                LINK_BREAKER_LOG_PREFIX, tokenDesc, cdcLinkBrokenReason);
            throw new PolardbxException(
                "cdc link is broken, fail fast without waiting token, reason: " + cdcLinkBrokenReason);
        }
    }

    private static boolean isLinkBreakerEnabled() {
        return Boolean.parseBoolean(configProp.getProperty(CDC_LINK_BREAKER_ENABLED, "true"));
    }

    /**
     * 采样上游CN的binlog位点(show master status [with 'stream'])，用于判断上游链路是否在推进。
     * 实验室有秒级心跳事务持续写入，正常情况下位点必然推进，整个等待窗口无推进即上游链路中断
     */
    private String sampleUpstreamMasterStatus() {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (Connection conn = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            StringBuilder position = new StringBuilder();
            if (usingBinlogX) {
                List<String> streams = JdbcUtil.executeQueryAndGetStringList("show binary streams", conn, 2);
                for (String stream : streams) {
                    try (ResultSet rs = JdbcUtil.executeQuery(
                        String.format("show master status with '%s'", stream), conn)) {
                        if (rs.next()) {
                            position.append(rs.getString("FILE")).append(":")
                                .append(rs.getLong("POSITION")).append("; ");
                        }
                    }
                }
            } else {
                try (ResultSet rs = JdbcUtil.executeQuery("show master status", conn)) {
                    if (rs.next()) {
                        position.append(rs.getString("FILE")).append(":").append(rs.getLong("POSITION"));
                    }
                }
            }
            return time + " | " + position;
        } catch (Throwable t) {
            return time + " | ERROR: " + t.getMessage();
        }
    }

    /**
     * 采样下游show slave status，识别replica位点失效特征(上游dumper重启/binlog重建后旧位点无法恢复)：
     * 1.原生MySQL下游：dumper侧dump失败统一发送1236(fatal)，IO线程直接停止且不会自愈，
     * Last_IO_Error为dumper透传的固定文案"binlog dump error!"，参见LogFileReader和CN侧CdcDumpStreamObserver
     * 2.PolarDB-X Replica下游：位点失效后RPL持续重试，Last_Error固定为"Dump error"，参见MysqlEventParser
     */
    private Pair<Boolean, String> sampleDownstreamSlaveStatus(Connection connection) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        try (Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("show slave status")) {
            if (!rs.next()) {
                return Pair.of(false, time + " | OK | empty result set");
            }
            List<String> columns = JdbcUtil.getColumnNameList(rs);
            String ioRunning = getColumnValueQuietly(rs, columns, "Slave_IO_Running");
            String ioErrno = getColumnValueQuietly(rs, columns, "Last_IO_Errno");
            String ioError = getColumnValueQuietly(rs, columns, "Last_IO_Error");
            String lastError = getColumnValueQuietly(rs, columns, "Last_Error");
            String sqlError = getColumnValueQuietly(rs, columns, "Last_SQL_Error");
            String masterLogFile = getColumnValueQuietly(rs, columns, "Master_Log_File");
            String execPos = getColumnValueQuietly(rs, columns, "Exec_Master_Log_Pos");

            boolean fatalIoStopped = StringUtils.equalsIgnoreCase(ioRunning, "No")
                && StringUtils.equals(ioErrno, "1236");
            boolean dumpErrorHit = StringUtils.containsIgnoreCase(lastError, "dump error")
                || StringUtils.containsIgnoreCase(ioError, "dump error");
            boolean hit = fatalIoStopped || dumpErrorHit;

            String display = String.format("%s | %s | Slave_IO_Running=%s, Last_IO_Errno=%s, Last_IO_Error=%s, "
                    + "Last_Error=%s, Last_SQL_Error=%s, Master_Log_File=%s, Exec_Master_Log_Pos=%s",
                time, hit ? "HIT" : "OK", ioRunning, ioErrno, ioError, lastError, sqlError,
                masterLogFile, execPos);
            return Pair.of(hit, display);
        } catch (Throwable t) {
            return Pair.of(false, time + " | ERROR | " + t.getMessage());
        }
    }

    private String getColumnValueQuietly(ResultSet rs, List<String> columns, String columnName) {
        try {
            for (String c : columns) {
                if (StringUtils.equalsIgnoreCase(c, columnName)) {
                    return rs.getString(c);
                }
            }
        } catch (SQLException ignored) {
        }
        return "";
    }

    /**
     * 等token超时后归因：区分上游binlog链路中断和下游replica位点失效两种确定性故障，
     * 命中则打开熔断，后续case快速失败；未命中(如单纯延迟大)不影响原有超时行为
     *
     * @return 归因结果，未命中返回null
     */
    private String diagnoseBrokenLink(String token, List<String> masterSamples,
                                      List<Pair<Boolean, String>> slaveSamples) {
        try {
            if (!isLinkBreakerEnabled() || masterSamples.size() < 2) {
                return null;
            }

            // 上游位点在整个等待窗口内无任何推进(或持续报错)，判定上游链路中断
            String firstPos = StringUtils.substringAfter(masterSamples.get(0), " | ");
            String lastPos = StringUtils.substringAfter(masterSamples.get(masterSamples.size() - 1), " | ");
            boolean allError = masterSamples.stream()
                .allMatch(s -> StringUtils.startsWith(StringUtils.substringAfter(s, " | "), "ERROR"));
            if (allError || StringUtils.equals(firstPos, lastPos)) {
                markCdcLinkBroken(REASON_UPSTREAM_NOT_PROGRESSING, token, masterSamples, slaveSamples);
                return REASON_UPSTREAM_NOT_PROGRESSING;
            }

            // 上游正常推进但token未到达，且下游最近两次采样均命中位点失效特征，判定replica断链
            if (slaveSamples.size() >= 2
                && slaveSamples.get(slaveSamples.size() - 1).getLeft()
                && slaveSamples.get(slaveSamples.size() - 2).getLeft()) {
                markCdcLinkBroken(REASON_REPLICA_POSITION_INVALID, token, masterSamples, slaveSamples);
                return REASON_REPLICA_POSITION_INVALID;
            }
        } catch (Throwable t) {
            log.warn("{} diagnose broken link failed for token {}", LINK_BREAKER_LOG_PREFIX, token, t);
        }
        return null;
    }

    private void markCdcLinkBroken(String reason, String token, List<String> masterSamples,
                                   List<Pair<Boolean, String>> slaveSamples) {
        cdcLinkBroken = true;
        cdcLinkBrokenReason = reason;
        StringBuilder detail = new StringBuilder();
        detail.append("upstream master status samples:\n");
        masterSamples.forEach(s -> detail.append("  ").append(s).append("\n"));
        detail.append("downstream slave status samples:\n");
        slaveSamples.forEach(s -> detail.append("  ").append(s.getRight()).append("\n"));
        log.error("{} circuit breaker OPEN, all subsequent wait-token will fail fast!\n"
                + "reason: {}\ntrigger case: {}\ntrigger token: {}\n{}",
            LINK_BREAKER_LOG_PREFIX, reason, getClass().getName(), token, detail);
    }

    public void check(CheckParameter parameter) {
        if (usingBinlogX) {
            HashLevel hashLevel = StreamHashUtil.getHashLevel(parameter.getDbName(), parameter.getTbName());
            if (parameter.getExpectHashLevel() != null) {
                Assert.assertEquals(parameter.getExpectHashLevel(), hashLevel);
            }
            if (hashLevel != HashLevel.RECORD) {
                int streamSeq = StreamHashUtil.getHashStreamSeq(parameter.getDbName(), parameter.getTbName());
                if (streamSeq == 0) {
                    compareOnce(parameter, cdcSyncDbFirstJdbcTemplate, cdcSyncDbConnectionFirst,
                        parameter.getContextInfoSupplier());
                } else if (streamSeq == 1) {
                    compareOnce(parameter, cdcSyncDbSecondJdbcTemplate, cdcSyncDbConnectionSecond,
                        parameter.getContextInfoSupplier());
                } else if (streamSeq == 2) {
                    compareOnce(parameter, cdcSyncDbThirdJdbcTemplate, cdcSyncDbConnectionThird,
                        parameter.getContextInfoSupplier());
                } else {
                    throw new PolardbxException("invalid stream seq " + streamSeq);
                }
            } else {
                //行级hash，放到链路复检阶段进行检测
            }
        } else {
            compareOnce(parameter, cdcSyncDbJdbcTemplate, cdcSyncDbConnection, parameter.getContextInfoSupplier());
        }
    }

    public void compareOnce(CheckParameter parameter, JdbcTemplate jdbcTemplate, Connection connection,
                            Supplier<String> contextSupplier) {
        if (parameter.isDirectCompareDetail()) {
            compareDetail(parameter.getDbName(), parameter.getTbName(), parameter.getAliasTbName(), jdbcTemplate,
                parameter.isCompareDetailOneByOne(), contextSupplier, parameter.getIgnoreColumns());
        } else {
            try {
                compareChecksum(parameter.getDbName(), parameter.getTbName(), parameter.getAliasTbName(), connection);
            } catch (Throwable t) {
                compareDetail(parameter.getDbName(), parameter.getTbName(), parameter.getAliasTbName(), jdbcTemplate,
                    parameter.isCompareDetailOneByOne(), contextSupplier, parameter.getIgnoreColumns());
            }
        }
    }

    public void compareDetail(String dbName, String tableName, String aliasTableName, JdbcTemplate dstJdbcTemplate,
                              boolean compareOneByOne, Supplier<String> contextSupplier, Set<String> ignoreColumns) {
        if (compareOneByOne) {
            compareDetailOneByOne(dbName, tableName, aliasTableName, dstJdbcTemplate, contextSupplier, ignoreColumns);
        } else {
            Retryer<Void> retryer = buildCompareDetailRetryer(10, TimeUnit.SECONDS, 6);
            try {
                retryer.call(() -> {
                    compareDetailBatch(dbName, tableName, aliasTableName, dstJdbcTemplate, ignoreColumns);
                    return null;
                });
            } catch (Exception e) {
                throw new PolardbxException("compare detail failed", e);
            }

        }
    }

    static Retryer<Void> buildCompareDetailRetryer(long waitTime, TimeUnit timeUnit, int maxAttempts) {
        return RetryerBuilder.<Void>newBuilder()
            .retryIfException()
            .retryIfExceptionOfType(AssertionError.class)
            .withWaitStrategy(WaitStrategies.fixedWait(waitTime, timeUnit))
            .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
            .build();
    }

    public void compareDetailBatch(String dbName, String tableName, String aliasTableName,
                                   JdbcTemplate dstJdbcTemplate, Set<String> ignoreColumns) {
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> pair =
            getTableDetail(dbName, tableName, aliasTableName, dstJdbcTemplate, null);
        removeIgnoreColumns(pair.getLeft(), ignoreColumns);
        removeIgnoreColumns(pair.getRight(), ignoreColumns);
        Assert.assertEquals("src<" + pair.getLeft() + "> and dst<" + pair.getRight() + ">, data should equals",
            0, new ResultSetComparator().compare(pair.getLeft(), pair.getRight()));
    }

    public void compareDetailOneByOne(String dbName, String tableName, String aliasTableName,
                                      JdbcTemplate dstJdbcTemplate,
                                      Supplier<String> contextSupplier, Set<String> ignoreColumns) {
        String sql = String.format("select id from `%s`.`%s`", dbName, tableName);
        List<Map<String, Object>> ids = polardbxJdbcTemplate.queryForList(sql);

        final ConcurrentHashMap<Object, String> successIds = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Object, String> failIds = new ConcurrentHashMap<>();
        log.info("prepare to compare detail one by one, total record size for check is " + ids.size());

        List<Future<?>> futures = new ArrayList<>();
        Retryer<Pair<List<Map<String, Object>>, List<Map<String, Object>>>> retryer =
            RetryerBuilder.<Pair<List<Map<String, Object>>, List<Map<String, Object>>>>newBuilder().retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS)).withStopStrategy(
                    StopStrategies.stopAfterAttempt(6)).build();
        for (Map<String, Object> map : ids) {
            Future<?> future = compareDetailExecutorService.submit(() -> {
                Object id = map.get("id");
                try {
                    Pair<List<Map<String, Object>>, List<Map<String, Object>>> pair =
                        retryer.call(() -> getTableDetail(dbName, tableName, aliasTableName, dstJdbcTemplate, id));

                    removeIgnoreColumns(pair.getLeft(), ignoreColumns);
                    removeIgnoreColumns(pair.getRight(), ignoreColumns);

                    ResultSetComparator comparator = new ResultSetComparator();
                    int result = comparator.compare(pair.getLeft(), pair.getRight());
                    Assert.assertEquals("id <" + id + ">, diff data is " + comparator.getDiffColumns() +
                        ", diff data types is " + comparator.getDiffColumnTypes(), 0, result);
                    successIds.put(id, "1");
                } catch (Throwable t) {
                    failIds.put(id, "1");
                    log.error("compare one record error, db name {}, table name {}, alias table name {} ",
                        dbName, tableName, aliasTableName, t);
                    if (contextSupplier != null) {
                        log.error("context info is " + contextSupplier.get());
                    }
                }
            });
            futures.add(future);
        }

        futures.forEach(i -> {
            try {
                i.get();
            } catch (Throwable t) {
                log.error("wait future error!", t);
            }
        });

        if (!failIds.isEmpty()) {
            log.error("failed ids set is " + JSON.toJSONString(failIds.keys()));
        }
        Assert.assertEquals("success ids size : " + successIds.size() + ", fail ids size : " + failIds.size(),
            ids.size(), successIds.size());
    }

    public void compareChecksum(String dbName, String tableName, String aliasTableName,
                                Connection cdcSyncDbConnection) {
        try {
            Pair<String, String> pair = calcChecksum(dbName, tableName, aliasTableName, cdcSyncDbConnection);
            log.info("src checksum is " + pair.getLeft() + ", dst checksum is " + pair.getRight());
            Assert.assertEquals(
                "src checksum <" + pair.getLeft() + "> and dst checksum <" + pair.getRight() + "> , data should equals",
                pair.getLeft(), pair.getRight());
        } catch (SQLException e) {
            log.error("calc checksum error", e);
            throw new PolardbxException("SQL ERROR : ", e);
        }
    }

    public static Pair<String, Integer> masterPosition() {
        JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        final Pair<String, Integer> masterPosition = polarxJdbcTemplate.queryForObject("SHOW MASTER STATUS",
            (i, j) -> Pair.of(i.getString("FILE"), i.getInt("POSITION")));
        return masterPosition;
    }

    public Pair<String, String> calcChecksum(String dbName, String tableName, String aliasTableName,
                                             Connection dstConnection)
        throws SQLException {
        ResultSet resultSet = JdbcUtil.executeQuery("desc `" + dbName + "`.`" + tableName + "`", dstConnection);
        List<String> fields = JdbcUtil.getListByColumnName(resultSet, "Field");
        String columns = fields.stream().filter(s -> !StringUtils.equalsIgnoreCase(s, "id"))
            .collect(Collectors.joining(","));

        ResultSet src = JdbcUtil.executeQuery(buildCheckSumSql(dbName, tableName, columns), polardbxConnection);
        String s = JdbcUtil.getObject(src, "md5").toString();

        aliasTableName = StringUtils.isNotBlank(aliasTableName) ? aliasTableName : tableName;
        ResultSet dst = JdbcUtil.executeQuery(buildCheckSumSql(dbName, aliasTableName, columns), dstConnection);
        String r = JdbcUtil.getObject(dst, "md5").toString();
        return Pair.of(s, r);
    }

    private String buildCheckSumSql(String dbName, String tableName, String columns) {
        StringBuilder builder = new StringBuilder();

        builder.append("select md5(data) md5 from ").append("(")
            .append("select group_concat(")
            .append("id,")
            .append("'->',")
            .append(columns)
            .append(") as data ")
            .append("from ( select * from ")
            .append("`" + dbName + "`.")
            .append("`" + tableName + "` ")
            .append("order by id asc")
            .append(") t) tt");

        log.info("checksum sql {}", builder);
        return builder.toString();
    }

    public Pair<List<Map<String, Object>>, List<Map<String, Object>>> getTableDetail(String dbName, String tableName,
                                                                                     String aliasTableName,
                                                                                     JdbcTemplate dstJdbcTemplate,
                                                                                     Object key) {
        StringBuilder builder = new StringBuilder();

        builder.append("select * from `").append(dbName).append("`")
            .append(".")
            .append("`").append("%s").append("`");
        if (key != null) {
            builder.append(" where id = '").append(key).append("'");
        }
        builder.append(" order by id asc");

        ColumnMapRowMapper rowMapper = new ColumnMapRowMapper() {
            @Override
            protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
                int type = rs.getMetaData().getColumnType(index);
                if (type == Types.REAL || type == Types.FLOAT) {
                    // FLOAT 极值的文本表示可能触发 JDBC 的 Float 越界检查。
                    return rs.getBigDecimal(index);
                }
                return super.getColumnValue(rs, index);
            }
        };
        String srcSql = String.format(builder.toString(), tableName);
        log.info("src check detail sql is " + srcSql);
        List<Map<String, Object>> s = polardbxJdbcTemplate.query(srcSql, rowMapper);
        s.forEach(m -> {
            if (m.containsKey("_ENUM_")) {
                Object origin = m.get("_ENUM_");
                m.put("_ENUM_", origin.toString().toLowerCase());
            }
        });

        String dstSql = String.format(builder.toString(),
            StringUtils.isNotBlank(aliasTableName) ? aliasTableName : tableName);
        log.info("dst check detail sql is " + dstSql);
        List<Map<String, Object>> d = dstJdbcTemplate.query(dstSql, rowMapper);
        d.forEach(m -> {
            if (m.containsKey("_ENUM_")) {
                Object origin = m.get("_ENUM_");
                m.put("_ENUM_", origin.toString().toLowerCase());
            }
        });
        return Pair.of(s, d);
    }

    protected static String randomTableName(String prefix, int suffixLength) {
        String suffix = RandomStringUtils.randomAlphanumeric(suffixLength).toLowerCase();
        return String.format("%s_%s", prefix, suffix);
    }

    /**
     * 从查询结果中移除需要忽略的列（如生成列）
     */
    private void removeIgnoreColumns(List<Map<String, Object>> data, Set<String> ignoreColumns) {
        if (ignoreColumns != null && !ignoreColumns.isEmpty()) {
            data.forEach(m -> ignoreColumns.forEach(m::remove));
        }
    }

    private void waitFlagActivate(boolean flag) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = String.format("select * from binlog_lab_event where event_type=%s order by id desc limit 1",
            LabEventType.HIDDEN_PK_ENABLE_SWITCH.ordinal() + "");
        long timeout = TimeUnit.MINUTES.toMillis(5);
        try (Connection conn = getMetaConnection()) {
            while (System.currentTimeMillis() - now < timeout) {
                ResultSet rs = JdbcUtil.executeQuery(sql, conn);
                while (rs.next()) {
                    String params = rs.getString("params");
                    if (params.equalsIgnoreCase(String.valueOf(flag))) {
                        return;
                    }
                }
            }
        }
        throw new PolardbxException(String.format("wait flag %s test timeout! ", flag + ""));
    }

    public void enableCdcConfig(String key, String value) throws SQLException {
        JdbcUtil.executeSuccess(polardbxConnection, String.format("set cdc global %s = %s", key, value));
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
        }
        if (key.equalsIgnoreCase(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED)) {
            waitFlagActivate(Boolean.parseBoolean(value));
        }
    }

    public BinlogPosition getMasterBinlogPosition() throws SQLException {
        ResultSet rs = JdbcUtil.executeQuery("show master status", polardbxConnection);
        Assert.assertTrue(rs.next());
        return new BinlogPosition(rs.getString(1), rs.getLong(2), -1, -1);
    }

    /**
     * 同步使用callback 来遍历 start 位置到 当前binlog的所有event
     *
     * @param start 起始位置
     * @param callback 回调方法
     */
    public void checkBinlogCallback(BinlogPosition start, CheckCallback callback) throws Exception {
        BinlogPosition endPos = getMasterBinlogPosition();
        checkBinlogCallback(start, endPos, callback);
    }

    /**
     * 同步使用callback 来遍历 start 位置到 end 的所有event
     *
     * @param start 起始位置
     * @param end 结束位置
     * @param callback 回调方法
     */
    public void checkBinlogCallback(BinlogPosition start, BinlogPosition end, CheckCallback callback)
        throws Exception {
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        log.info("search binlog between [{}:{}] to [{}:{}]", start.getFileName(), start.getPosition(),
            end.getFileName(), end.getPosition());
        Connection conn = getPolardbxDirectConnection();
        DirectLogFetcher fetcher = new DirectLogFetcher();
        try {
            JdbcUtil.executeSuccess(conn, "set @master_binlog_checksum=1");
            LogContext lc = new LogContext();
            lc.setServerCharactorSet(new ServerCharactorSet());
            lc.setLogPosition(new LogPosition(start.getFileName(), start.getPosition()));
            Field targetField = ConnectionWrap.class.getDeclaredField("connection");
            targetField.setAccessible(true);
            Connection target = (Connection) targetField.get(conn);
            fetcher.open(target, start.getFileName(), start.getPosition(), -1);
            while (fetcher.fetch()) {
                LogBuffer buffer = fetcher.buffer();
                LogEvent event = decoder.decode(buffer, lc);
                if (event == null) {
                    continue;
                }
                callback.doCheck(event, lc);
                int cmp =
                    org.apache.commons.lang3.StringUtils.compare(lc.getLogPosition().getFileName(), end.getFileName());
                if (cmp == 0
                    && event.getLogPos() >= end.getPosition() || cmp > 0) {
                    break;
                }
            }
        } finally {
            conn.close();
            fetcher.close();
        }
    }

    protected Connection getDruidConnection(int n) {
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

    protected List<String> getTableList(String database, int ds) throws SQLException {
        try (Connection conn = getDruidConnection(ds)) {
            return JdbcUtil.showTables(conn, database);
        }
    }

    /**
     * 实验室是否开启了归档表过滤参数
     *
     * @return boolean
     */
    public boolean isArchiveIgnoreEnabled() {
        try (Connection c = getMetaConnection()) {
            ResultSet rs = c.createStatement()
                .executeQuery(String.format(LAB_EVENT_QUERY, LabEventType.TASK_FILTER_ARCHIVE_ENABLED.ordinal()));
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            if (e instanceof SQLSyntaxErrorException && e.getMessage()
                .contains("Table 'polardbx_meta_db.binlog_lab_event' doesn't exist")) {
                return false;
            } else {
                throw new RuntimeException(e);
            }

        }
        return false;
    }

    /**
     * 该表是否是TTL表
     *
     * @return boolean
     */
    public boolean isArchiveTable(String database, String table) {
        try (Connection c = getMetaConnection()) {
            ResultSet rs = c.createStatement().executeQuery(String.format(TTL_INFO_QUERY, database, table));
            if (rs.next()) {
                log.info("{}.{} is ignoreArchiveFiltered", database, table);
                return true;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    public interface CheckCallback {
        void doCheck(LogEvent event, LogContext context);
    }

    public boolean dstIsReplica() throws SQLException {
        ResultSet resultSet = JdbcUtil.executeQuery("select version()", getCdcSyncDbConnection());
        if (resultSet.next()) {
            String version = resultSet.getString(1);
            return org.apache.commons.lang.StringUtils.contains(version, "TDDL");
        }
        return false;
    }
}
