/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.CDC_WAIT_TOKEN_TIMEOUT_MINUTES;
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

    public void loopWait(String token, Connection connection, long timeout) {
        if (timeout <= 0) {
            int waitTimeMinute = Integer.parseInt(configProp.getProperty(CDC_WAIT_TOKEN_TIMEOUT_MINUTES, "20"));
            timeout = waitTimeMinute * 60 * 1000;
        }

        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                Statement statement = connection.createStatement();
                statement.executeQuery("show create table `" + TOKEN_DB + "`.`" + token + "`");
                break;
            } catch (Throwable ignored) {
            }
            if (System.currentTimeMillis() - startTime > timeout) {
                throw new PolardbxException("loop wait timeout for table  " + token);
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }
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
            compareDetail(parameter.getDbName(), parameter.getTbName(), jdbcTemplate,
                parameter.isCompareDetailOneByOne(), contextSupplier);
        } else {
            try {
                compareChecksum(parameter.getDbName(), parameter.getTbName(), connection);
            } catch (Throwable t) {
                compareDetail(parameter.getDbName(), parameter.getTbName(), jdbcTemplate,
                    parameter.isCompareDetailOneByOne(), contextSupplier);
            }
        }
    }

    public void compareDetail(String dbName, String tableName, JdbcTemplate dstJdbcTemplate,
                              boolean compareOneByOne, Supplier<String> contextSupplier) {
        if (compareOneByOne) {
            compareDetailOneByOne(dbName, tableName, dstJdbcTemplate, contextSupplier);
        } else {
            Retryer retryer = RetryerBuilder.newBuilder().retryIfException()
                .withWaitStrategy(WaitStrategies.fixedWait(10, TimeUnit.SECONDS)).withStopStrategy(
                    StopStrategies.stopAfterAttempt(6)).build();
            try {
                retryer.call(() -> {
                    compareDetailBatch(dbName, tableName, dstJdbcTemplate);
                    return null;
                });
            } catch (Exception e) {
                throw new PolardbxException("compare detail failed", e);
            }

        }
    }

    public void compareDetailBatch(String dbName, String tableName, JdbcTemplate dstJdbcTemplate) {
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> pair =
            getTableDetail(dbName, tableName, dstJdbcTemplate, null);
        Assert.assertEquals("src<" + pair.getLeft() + "> and dst<" + pair.getRight() + "> data show equals",
            0, new ResultSetComparator().compare(pair.getLeft(), pair.getRight()));
    }

    public void compareDetailOneByOne(String dbName, String tableName, JdbcTemplate dstJdbcTemplate,
                                      Supplier<String> contextSupplier) {
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
                        retryer.call(() -> getTableDetail(dbName, tableName, dstJdbcTemplate, id));

                    ResultSetComparator comparator = new ResultSetComparator();
                    int result = comparator.compare(pair.getLeft(), pair.getRight());
                    Assert.assertEquals("id <" + id + ">, diff data is " + comparator.getDiffColumns() +
                        ", diff data types is " + comparator.getDiffColumnTypes(), 0, result);
                    successIds.put(id, "1");
                } catch (Throwable t) {
                    failIds.put(id, "1");
                    log.error("compare one record error , " + t.getMessage(), t);
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

    public void compareChecksum(String dbName, String tableName, Connection cdcSyncDbConnection) {
        try {
            Pair<String, String> pair = calcChecksum(dbName, tableName, cdcSyncDbConnection);
            log.info("src checksum is " + pair.getLeft() + ", dst checksum is " + pair.getRight());
            Assert.assertEquals("src<" + pair.getLeft() + "> and dst<" + pair.getRight() + "> data should equals",
                pair.getLeft(), pair.getRight());
        } catch (SQLException e) {
            throw new PolardbxException("SQL ERROR : ", e);
        }
    }

    public static Pair<String, Integer> masterPosition() {
        JdbcTemplate polarxJdbcTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
        final Pair<String, Integer> masterPosition = polarxJdbcTemplate.queryForObject("SHOW MASTER STATUS",
            (i, j) -> Pair.of(i.getString("FILE"), i.getInt("POSITION")));
        return masterPosition;
    }

    public Pair<String, String> calcChecksum(String dbName, String tableName, Connection dstConnection)
        throws SQLException {
        ResultSet resultSet = JdbcUtil.executeQuery("desc `" + dbName + "`.`" + tableName + "`", dstConnection);
        List<String> fields = JdbcUtil.getListByColumnName(resultSet, "Field");
        String columns = fields.stream().filter(s -> !StringUtils.equalsIgnoreCase(s, "id"))
            .collect(Collectors.joining(","));
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

        log.info("checksum sql {}", builder.toString());

        ResultSet src = JdbcUtil.executeQuery(builder.toString(), polardbxConnection);
        String s = JdbcUtil.getObject(src, "md5").toString();

        ResultSet dst = JdbcUtil.executeQuery(builder.toString(), dstConnection);
        String r = JdbcUtil.getObject(dst, "md5").toString();
        return Pair.of(s, r);
    }

    public Pair<List<Map<String, Object>>, List<Map<String, Object>>> getTableDetail(String dbName, String tableName,
                                                                                     JdbcTemplate dstJdbcTemplate,
                                                                                     Object key) {
        StringBuilder builder = new StringBuilder();

        builder.append("select * from `" + dbName + "`")
            .append(".")
            .append("`").append(tableName).append("`");
        if (key != null) {
            builder.append(" where id = '").append(key).append("'");
        }
        builder.append(" order by id asc");

        if (key == null) {
            log.info("check sql {}", builder.toString());
        }
        List<Map<String, Object>> s = polardbxJdbcTemplate.queryForList(
            builder.toString());
        s.forEach(m -> {
            if (m.containsKey("_ENUM_")) {
                Object origin = m.get("_ENUM_");
                m.put("_ENUM_", origin.toString().toLowerCase());
            }
        });
        List<Map<String, Object>> d = dstJdbcTemplate.queryForList(
            builder.toString());
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

    public interface CheckCallback {
        void doCheck(LogEvent event, LogContext context);
    }
}
