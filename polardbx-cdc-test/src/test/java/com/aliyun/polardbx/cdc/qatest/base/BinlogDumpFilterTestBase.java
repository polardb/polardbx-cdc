/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * @author zm
 * 用于测试Dumper端主动过滤功能
 * 注意，此处会永久关闭binlog压缩（已经到复检了，没有必要再开压缩了）
 */
@Slf4j
public class BinlogDumpFilterTestBase extends RplBaseTestCase {
    private static final String QUERY_LAB_EVENT = "SELECT * FROM binlog_lab_event where event_type = %s";
    private static final String DB_NAME = "zm_dump_filter_db";
    private static final String IGNORE_TABLE_NAME = "zm_ignore_tb";
    private static final String NORMAL_TABLE_NAME = "zm_normal_tb";
    private static final String TEST_USER_NAME = "zm";
    private static final String TEST_USER_PASS = "zmPass";
    private static final String SET_GLOBAL_SQL = "set global `%s`='%s'";
    private static final String CREATE_DATABASE_SQL = "create database if not exists %s mode = 'auto'";
    private static final String INSERT_SQL = "insert into `%s`.`%s` (`value`,`date`) values ('%s', '%s')";
    private static final String CLEAN_UP_SQL = "ALTER TABLE `%s`.`%s` CLEANUP EXPIRED DATA";
    private static final String SHOW_MASTER_STATUS_SQL = "show master status";
    private static final String DROP_TABLE_SQL = "DROP TABLE IF EXISTS `%s`.`%s`";
    private static final String QUERY_INST_CONFIG_SQL = "select * from metaDB.inst_config where `param_key` = '%s'";
    private static final String SELECT_TABLE_SQL = "select * from `%s`.`%s`";
    private static final String CREATE_USER_SQL = "CREATE USER if not exists '%s'@'%%' IDENTIFIED BY '%s'";
    private static final String GRANT_CLIENT_SQL = "GRANT REPLICATION CLIENT ON *.* to '%s'@'%%'";
    private static final String GRANT_SLAVE_SQL = "GRANT REPLICATION SLAVE ON *.* to '%s'@'%%'";
    private static final String CREATE_ARCHIVE_TABLE_SQL =
        "CREATE TABLE if not exists `%s`.`%s` ( \n"
            + "  `id` int(32) NOT NULL AUTO_INCREMENT,\n"
            + "  `value` longtext,\n"
            + "  `date` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT \n"
            + "  CHARSET = utf8mb4 \n"
            + "  TTL = TTL_DEFINITION ( \n"
            + "    TTL_EXPR = `date` EXPIRE AFTER 1 DAY TIMEZONE '+08:00' \n"
            + "    TTL_ENABLE = 'ON'\n"
            + "    TTL_CLEANUP = 'ON'\n"
            + "  ) \n"
            + "PARTITION BY KEY(`id`)\n"
            + "PARTITIONS 8;";
    private static final String CREATE_IGNORE_TABLE_SQL =
        "CREATE TABLE if not exists `%s`.`%s` ( \n"
            + "  `id` int(32) NOT NULL AUTO_INCREMENT,\n"
            + "  `value` varchar(128),\n"
            + "  `date` datetime DEFAULT CURRENT_TIMESTAMP,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE = InnoDB DEFAULT \n"
            + "  CHARSET = utf8mb4 \n"
            + "PARTITION BY KEY(`id`)\n"
            + "PARTITIONS 8;";
    private static final String SET_CDC_GLOBAL_SQL = "set cdc global %s=%s";
    private static boolean testEnabled = true;

    @BeforeClass
    public static void setUp() {
        try (Connection c = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            c.createStatement()
                .execute(String.format("set `%s`='%s'", ConfigKeys.BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED, "true"));
        } catch (Exception e) {
            String errMsg = ExceptionUtils.getStackTrace(e);
            testEnabled = false;
            if (!errMsg.contains("Unknown system variable")) {
                Assert.fail("unexpected error: " + errMsg);
            }
        }
        if (usingBinlogX) {
            // 单流多流如果并发执行测试，会有并发问题，如set global.
            testEnabled = false;
        }
    }

    @Before
    @Override
    public void before() {
        try (Connection c = getPolardbxConnection()) {
            super.before();
            // 已经到了复检，可以关闭压缩了，且压缩后的事件也不支持在源端进行过滤，
            // 如果支持过滤就是 解压-> 过滤 -> 压缩 三步很蠢
            c.createStatement()
                .execute(
                    String.format(SET_CDC_GLOBAL_SQL, ConfigKeys.DAEMON_AUTO_SET_COMPRESSION_TEST, "false"));
            c.createStatement()
                .execute(String.format(SET_CDC_GLOBAL_SQL, ConfigKeys.BINLOG_TRANSACTION_COMPRESSION, "false"));
            sendTokenAndWait(CheckParameter.builder().build());
            log.info("compression unset!");
        } catch (Exception e) {
            String errMsg = ExceptionUtils.getStackTrace(e);
            testEnabled = false;
            Assert.fail("unexpected error: " + errMsg);
        }
    }

    @SneakyThrows
    public void testSessionArchiveBinlogDumpFilter() {
        if (!testEnabled) {
            return;
        }
        // 创建TTL表并触发清理
        String tableName = "archive_" + UUID.randomUUID().toString().replace("-", "");
        String file = "";
        long pos = 0;
        // 实验室环境下游是replica时，会有50%的设置global参数，概率过滤所有的TTL表删除事件
        boolean globalArchiveEnabled = false;
        // 是否配置了为polardbx_root用户过滤rows query
        boolean globalPdxFilterEnabled = false;
        try (Connection c = getPolardbxConnection()) {
            ResultSet rs = c.createStatement()
                .executeQuery(String.format(QUERY_INST_CONFIG_SQL, ConfigKeys.BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED));
            if (rs.next()) {
                globalArchiveEnabled = rs.getBoolean("param_val");
            }
            rs = c.createStatement()
                .executeQuery(String.format(QUERY_INST_CONFIG_SQL, "BINLOG_DUMP_FILTER_USER_CONFIG"));
            if (rs.next()) {
                String configJson = rs.getString("param_val");
                Map<String, Map<String, String>> map = JSON.parseObject(configJson, Map.class);
                String pdxFilterEnabledStr = map.getOrDefault("polardbx_root", new HashMap<>())
                    .get(ConfigKeys.BINLOG_DUMP_ROWS_QUERY_IGNORE_ENABLED);
                if (!StringUtils.isEmpty(pdxFilterEnabledStr)) {
                    globalPdxFilterEnabled = Boolean.parseBoolean(pdxFilterEnabledStr);
                }
            }
            c.createStatement()
                .execute(String.format("set `%s`='%s'", "TTL_DEBUG_CURRENT_DATETIME", "2025-09-22 00:00:00"));
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            log.info("create table sql: {}", String.format(CREATE_ARCHIVE_TABLE_SQL, DB_NAME, tableName));
            c.createStatement().execute(String.format(CREATE_ARCHIVE_TABLE_SQL, DB_NAME, tableName));
            String value = RandomStringUtils.randomAlphabetic(70000);
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, tableName, value, "2020-01-28 13:56:19"));
            rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            c.createStatement().execute(String.format(CLEAN_UP_SQL, DB_NAME, tableName));
            rs = c.createStatement().executeQuery(String.format(SELECT_TABLE_SQL, DB_NAME, tableName));
            if (rs.next()) {
                Assert.fail("find record after clean up, id = " + rs.getInt("id"));
            }
            c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, tableName));
        }
        Assert.assertFalse("show master status no file!", StringUtils.isEmpty(file));

        Map<String, String> params = new HashMap<>(1);
        params.put(ConfigKeys.BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED, "true");
        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();

        try (Connection metaConnection = getMetaConnection()) {
            // TASK archive ignored
            ResultSet rs =
                JdbcUtil.executeQuery(
                    String.format(QUERY_LAB_EVENT, LabEventType.TASK_FILTER_ARCHIVE_ENABLED.ordinal()),
                    metaConnection);
            if (rs.next()) {
                globalArchiveEnabled = true;
            }
        }

        int fileSeq = BinlogFileUtil.getBinlogSequence(file);
        AtomicBoolean timeOut = new AtomicBoolean(false);
        if (!globalArchiveEnabled && !globalPdxFilterEnabled) {
            // 不加参数dump应该能读到ARCHIVE的事件
            LogPosition archivePosition = new LogPosition("", 0);
            log.info("start dump from {}:{}", file, pos);
            mysqlConn.dump(file, pos, null, (event, logPosition) -> {
                if (event instanceof RowsQueryLogEvent) {
                    RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) event;
                    if (rowsQueryLogEvent.getRowsQuery().contains("ARCHIVE")) {
                        archivePosition.setFileName(logPosition.getFileName());
                        archivePosition.setPosition(logPosition.getPosition());
                        log.info("find archive event at {}", archivePosition);
                        return false;
                    }
                }
                if (event instanceof RotateLogEvent) {
                    int seq = BinlogFileUtil.getBinlogSequence(logPosition.getFileName());
                    // 超过5个binlog文件还没找到archive语句，链路存在问题
                    if (seq - fileSeq > 5) {
                        timeOut.set(true);
                        return false;
                    }
                }
                return true;
            });
            Assert.assertFalse("can not find archive event without BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED",
                StringUtils.isEmpty(archivePosition.getFileName()));
            Assert.assertFalse("can not find archive event in 5 binlog files start from " + file, timeOut.get());
        }

        // 加参数则读不到ARCHIVE事件
        mysqlConn.connect();
        AtomicBoolean findArchive = new AtomicBoolean(false);
        timeOut.set(false);
        mysqlConn.dump(file, pos, null, (event, logPosition) -> {
            if (event instanceof RowsQueryLogEvent) {
                RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) event;
                if (rowsQueryLogEvent.getRowsQuery().contains("ARCHIVE")) {
                    log.error("find archive event at {}:{}", logPosition.getFileName(), logPosition.getPosition());
                    findArchive.set(true);
                    return false;
                }
            }
            if (event instanceof QueryLogEvent) {
                QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                String query = queryLogEvent.getQuery();
                if (!query.equalsIgnoreCase("begin")) {
                    log.info(queryLogEvent.getQuery());
                }
                return !queryLogEvent.getQuery().contains(String.format(DROP_TABLE_SQL, DB_NAME, tableName));
            }
            if (event instanceof RotateLogEvent) {
                int seq = BinlogFileUtil.getBinlogSequence(logPosition.getFileName());
                // 超过5个binlog文件还没找到删表语句，链路存在问题
                if (seq - fileSeq > 5) {
                    timeOut.set(true);
                    return false;
                }
            }
            return true;
        }, params);
        mysqlConn.disconnect();
        Assert.assertFalse("find archive event with BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED", findArchive.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());
    }

    /**
     * 测试在开启全局的binlog_dump_archive_ignore_enabled参数下，目标表不会将TTL表的数据进行删除
     */
    @SneakyThrows
    public void testGlobalArchiveBinlogDumpFilter() {
        if (!testEnabled) {
            return;
        }
        String tableName = "archive_" + UUID.randomUUID().toString().replace("-", "");
        boolean globalArchiveEnabled = false;
        String value = RandomStringUtils.randomAlphabetic(70000);
        try (Connection c = getPolardbxConnection()) {
            // 创建TTL表并触发清理
            ResultSet rs = c.createStatement()
                .executeQuery(String.format(QUERY_INST_CONFIG_SQL, ConfigKeys.BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED));
            if (rs.next()) {
                globalArchiveEnabled = rs.getBoolean("param_val");
            }
            if (globalArchiveEnabled) {
                c.createStatement()
                    .execute(String.format("set `%s`='%s'", "TTL_DEBUG_CURRENT_DATETIME", "2025-09-22 00:00:00"));
                c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
                log.info("create table sql: {}", String.format(CREATE_ARCHIVE_TABLE_SQL, DB_NAME, tableName));
                c.createStatement().execute(String.format(CREATE_ARCHIVE_TABLE_SQL, DB_NAME, tableName));
                c.createStatement()
                    .execute(String.format(INSERT_SQL, DB_NAME, tableName, value, "2020-01-28 13:56:19"));
                c.createStatement().execute(String.format(CLEAN_UP_SQL, DB_NAME, tableName));
                rs = c.createStatement().executeQuery(String.format(SELECT_TABLE_SQL, DB_NAME, tableName));
                if (rs.next()) {
                    Assert.fail("find record after clean up, id = " + rs.getInt("id"));
                }
                c.createStatement()
                    .execute(String.format(INSERT_SQL, DB_NAME, tableName, value, "2022-01-28 13:56:19"));
            }
        }
        if (globalArchiveEnabled) {
            sendTokenAndWait(CheckParameter.builder().build());
            try (Connection c = getCdcSyncDbConnection()) {
                ResultSet rs = c.createStatement().executeQuery(String.format(SELECT_TABLE_SQL, DB_NAME,
                    tableName));
                int recordCount = 0;
                while (rs.next()) {
                    String valueTarget = rs.getString("value");
                    Assert.assertEquals(valueTarget, value);
                    recordCount++;
                }
                // 由于开启archive过滤，所以删除数据的动作不会同步到目标端
                Assert.assertEquals(2, recordCount);
            }
            try (Connection c = getPolardbxConnection()) {
                c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, tableName));
            }
        }
    }

    /**
     * 测试黑名单表过滤功能
     */
    @SneakyThrows
    public void testTableBinlogDumpFilter() {
        if (!testEnabled) {
            return;
        }
        String value = RandomStringUtils.randomAlphabetic(108);
        try (Connection c = getPolardbxConnection()) {
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            log.info("create ignore table sql: {}", String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, IGNORE_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, IGNORE_TABLE_NAME));
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, IGNORE_TABLE_NAME, value, "2020-01-28 13:56:19"));
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, IGNORE_TABLE_NAME, value, "2022-01-28 13:56:19"));
        }
        sendTokenAndWait(CheckParameter.builder().build());
        try (Connection c = getCdcSyncDbConnection()) {
            ResultSet rs =
                c.createStatement().executeQuery(String.format(SELECT_TABLE_SQL, DB_NAME, IGNORE_TABLE_NAME));
            int recordCount = 0;
            while (rs.next()) {
                recordCount++;
            }
            Assert.assertEquals("ignore table should has no data!", 0, recordCount);
        }
    }

    /**
     * 测试白名单表过滤功能
     */
    @SneakyThrows
    public void testWhiteTableBinlogDumpFilter() {
        if (!testEnabled) {
            return;
        }
        String file = "";
        // 如果该表被删，表示本次测试结束。
        String endTokenTableName = "zm_" + UUID.randomUUID();
        long pos = 0;
        String value = RandomStringUtils.randomAlphabetic(108);
        try (Connection c = getPolardbxConnection()) {
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            log.info("create ignore table sql: {}", String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, IGNORE_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, IGNORE_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, NORMAL_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, endTokenTableName));
            ResultSet rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, IGNORE_TABLE_NAME, value, "2020-01-28 13:56:19"));
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, NORMAL_TABLE_NAME, value, "2022-01-28 13:56:19"));
            c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, endTokenTableName));
        }

        Map<String, String> params = new HashMap<>(1);
        params.put("BINLOG_DUMP_DO_TABLE", DB_NAME + "." + NORMAL_TABLE_NAME);
        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();
        AtomicBoolean findTableEvent = new AtomicBoolean(false);
        AtomicBoolean timeOut = new AtomicBoolean(false);
        // 能够找到allow的table event
        tryFindTableEvent(file, pos, mysqlConn, findTableEvent, timeOut, params, DB_NAME + "." + NORMAL_TABLE_NAME,
            endTokenTableName);
        mysqlConn.disconnect();
        Assert.assertTrue("can not find table event with table_allow", findTableEvent.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());

        mysqlConn.connect();
        findTableEvent.set(false);
        timeOut.set(false);
        // 找不到除了allow 以外的table event
        tryFindTableEvent(file, pos, mysqlConn, findTableEvent, timeOut, params, DB_NAME + "." + IGNORE_TABLE_NAME,
            endTokenTableName);
        Assert.assertFalse("find table event with table_allow ", findTableEvent.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());
        mysqlConn.disconnect();

        params.put("BINLOG_DUMP_DO_TABLE", DB_NAME + "." + NORMAL_TABLE_NAME + "," + DB_NAME + "." + IGNORE_TABLE_NAME);
        mysqlConn.connect();
        findTableEvent.set(false);
        timeOut.set(false);
        // 在params将表加入白名单后应该能收到该表的event
        tryFindTableEvent(file, pos, mysqlConn, findTableEvent, timeOut, params, DB_NAME + "." + IGNORE_TABLE_NAME,
            endTokenTableName);
        Assert.assertTrue("can not find table event with table_allow ", findTableEvent.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());
        mysqlConn.disconnect();
    }

    /**
     * 测试rows query event过滤功能
     */
    @SneakyThrows
    public void testSessionRowQueryFilter() {
        if (!testEnabled) {
            return;
        }
        String file = "";
        String endTokenTableName = "zm_" + UUID.randomUUID();
        long pos = 0;
        String value = RandomStringUtils.randomAlphabetic(64);
        try (Connection c = getPolardbxConnection()) {
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, NORMAL_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, endTokenTableName));
            ResultSet rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            if (StringUtils.isEmpty(file)) {
                Assert.fail("SHOW MASTER STATUS WITH EMPTY FILE");
            }
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, NORMAL_TABLE_NAME, value, "2020-01-28 13:56:19"));
            c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, endTokenTableName));
        }

        Map<String, String> params = new HashMap<>(1);
        params.put(ConfigKeys.BINLOG_DUMP_ROWS_QUERY_IGNORE_ENABLED, "true");
        MysqlConnection mysqlConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        mysqlConn.connect();
        AtomicBoolean findRowsQuery = new AtomicBoolean(false);
        AtomicBoolean timeOut = new AtomicBoolean(false);

        tryFindRowsQueryEvent(file, pos, mysqlConn, findRowsQuery, timeOut, params, endTokenTableName);

        mysqlConn.disconnect();
        Assert.assertFalse("find rows query event with BINLOG_DUMP_ARCHIVE_IGNORE_ENABLED", findRowsQuery.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());
    }

    /**
     * 测试用户名参数过滤功能
     */
    @SneakyThrows
    public void testUserParamsFilter() {
        if (!testEnabled) {
            return;
        }
        String file = "";
        long pos = 0;
        String value = RandomStringUtils.randomAlphabetic(64);
        String endTokenTableName = "zm_" + UUID.randomUUID();

        Map<String, String> testUserConfig = new HashMap<>();
        testUserConfig.put(ConfigKeys.BINLOG_DUMP_ROWS_QUERY_IGNORE_ENABLED, "true");
        Map<String, Map<String, String>> userConfigs = new HashMap<>();
        userConfigs.put(TEST_USER_NAME, testUserConfig);
        String userConfigsJson = JSON.toJSONString(userConfigs);
        String rawConfigsJson = "{\"zm\":{}}";
        boolean pdxFilterEnabled = false;

        try (Connection c = getPolardbxConnection()) {
            ResultSet rs = c.createStatement()
                .executeQuery(String.format(QUERY_INST_CONFIG_SQL, "BINLOG_DUMP_FILTER_USER_CONFIG"));
            if (rs.next()) {
                // 是否配置了为polardbx_root用户过滤rows query
                String configJson = rs.getString("param_val");
                Map<String, Map<String, String>> map = JSON.parseObject(configJson, Map.class);
                String pdxFilterEnabledStr = map.getOrDefault("polardbx_root", new HashMap<>())
                    .get(ConfigKeys.BINLOG_DUMP_ROWS_QUERY_IGNORE_ENABLED);
                rawConfigsJson = JSON.toJSONString(map);
                map.put(TEST_USER_NAME, testUserConfig);
                userConfigsJson = JSON.toJSONString(map);
                if (!StringUtils.isEmpty(pdxFilterEnabledStr)) {
                    pdxFilterEnabled = Boolean.parseBoolean(pdxFilterEnabledStr);
                }
            }
            // 创建用户
            c.createStatement().execute(String.format(CREATE_USER_SQL, TEST_USER_NAME, TEST_USER_PASS));
            c.createStatement().execute(String.format(GRANT_CLIENT_SQL, TEST_USER_NAME));
            c.createStatement().execute(String.format(GRANT_SLAVE_SQL, TEST_USER_NAME));
            // 创建库表
            c.createStatement().execute(String.format(CREATE_DATABASE_SQL, DB_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, NORMAL_TABLE_NAME));
            c.createStatement().execute(String.format(CREATE_IGNORE_TABLE_SQL, DB_NAME, endTokenTableName));
            rs = c.createStatement().executeQuery(SHOW_MASTER_STATUS_SQL);
            if (rs.next()) {
                file = rs.getString("FILE");
                pos = Long.parseLong(rs.getString("POSITION"));
            }
            if (StringUtils.isEmpty(file)) {
                Assert.fail("SHOW MASTER STATUS WITH EMPTY FILE");
            }
            c.createStatement()
                .execute(String.format(INSERT_SQL, DB_NAME, NORMAL_TABLE_NAME, value, "2020-01-28 13:56:19"));
            c.createStatement().execute(String.format(DROP_TABLE_SQL, DB_NAME, endTokenTableName));
        }

        MysqlConnection polarConn = ConnectionManager.getInstance().getPolarxConnectionOfMysql();
        polarConn.connect();
        AtomicBoolean findRowsQuery = new AtomicBoolean(false);
        AtomicBoolean timeOut = new AtomicBoolean(false);
        // polardbx_root用户默认不过滤rows query，但有可能人为的配置了polardbx_root过滤rows query
        tryFindRowsQueryEvent(file, pos, polarConn, findRowsQuery, timeOut, null, endTokenTableName);
        if (pdxFilterEnabled) {
            Assert.assertFalse("find rows query event with pdxFilterEnabled", findRowsQuery.get());
        } else {
            Assert.assertTrue("can not find rows query event with user polardbx_root", findRowsQuery.get());
        }
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());

        MysqlConnection mysqlConn = getMysqlConnection(TEST_USER_NAME, TEST_USER_PASS);
        mysqlConn.connect();
        findRowsQuery.set(false);
        timeOut.set(false);

        // 默认对所有用户都不过滤rows query
        tryFindRowsQueryEvent(file, pos, mysqlConn, findRowsQuery, timeOut, null, endTokenTableName);
        // 应该找得到rows query
        Assert.assertTrue("can not find rows query event with test user", findRowsQuery.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());

        try (Connection c = getPolardbxConnection()) {
            // 设置用户级变量，让不过滤rows query {"zm":{"binlog_dump_rows_query_ignore_enabled":"false"}}
            c.createStatement()
                .execute(String.format(SET_GLOBAL_SQL, "BINLOG_DUMP_FILTER_USER_CONFIG", userConfigsJson));
        }

        mysqlConn.connect();
        findRowsQuery.set(false);
        timeOut.set(false);
        tryFindRowsQueryEvent(file, pos, mysqlConn, findRowsQuery, timeOut, null, endTokenTableName);

        // 找不到rows query
        Assert.assertFalse("find rows query event with config test user's params true", findRowsQuery.get());
        Assert.assertFalse("can not find drop table event in 5 binlog files start from " + file, timeOut.get());

        try (Connection c = getPolardbxConnection()) {
            // 设置回默认值
            c.createStatement()
                .execute(String.format(SET_GLOBAL_SQL, "BINLOG_DUMP_FILTER_USER_CONFIG", rawConfigsJson));
        }
        mysqlConn.disconnect();

    }

    private MysqlConnection getMysqlConnection(String user, String password) {
        String address = ConnectionManager.getInstance().getPolardbxAddress();
        String port = ConnectionManager.getInstance().getPolardbxPort();
        AuthenticationInfo auth =
            new AuthenticationInfo(new InetSocketAddress(address, Integer.parseInt(port)), user, password);
        return new MysqlConnection(auth);
    }

    private void tryFindRowsQueryEvent(String file, long pos, MysqlConnection mysqlConn,
                                       AtomicBoolean findRowsQuery, AtomicBoolean timeOut, Map<String, String> params,
                                       String endToken)
        throws Exception {
        int fileSeq = BinlogFileUtil.getBinlogSequence(file);
        mysqlConn.dump(file, pos, null, (event, logPosition) -> {
            if (event instanceof RowsQueryLogEvent) {
                log.error("find rows query event at {}:{}", logPosition.getFileName(), logPosition.getPosition());
                findRowsQuery.set(true);
                return false;
            }

            if (event instanceof QueryLogEvent) {
                QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                String query = queryLogEvent.getQuery();
                if (!query.equalsIgnoreCase("begin")) {
                    log.info(queryLogEvent.getQuery());
                }
                return !queryLogEvent.getQuery().contains(String.format(DROP_TABLE_SQL, DB_NAME, endToken));
            }

            if (event instanceof RotateLogEvent) {
                int seq = BinlogFileUtil.getBinlogSequence(logPosition.getFileName());
                // 超过5个binlog文件还没找到删表语句，链路存在问题
                if (seq - fileSeq > 5) {
                    timeOut.set(true);
                    return false;
                }
            }
            return true;
        }, params);
    }

    private void tryFindTableEvent(String file, long pos, MysqlConnection mysqlConn,
                                   AtomicBoolean findTableEvent, AtomicBoolean timeOut, Map<String, String> params,
                                   String tableName, String endToken)
        throws Exception {
        int fileSeq = BinlogFileUtil.getBinlogSequence(file);
        log.info("try find {}'s table Event from {}:{}", tableName, file, pos);
        mysqlConn.dump(file, pos, null, (event, logPosition) -> {
            if (event instanceof TableMapLogEvent) {
                TableMapLogEvent tableMapLogEvent = (TableMapLogEvent) event;
                String eventTableName = tableMapLogEvent.getDbName() + "." + tableMapLogEvent.getTableName();
                if (eventTableName.equalsIgnoreCase(tableName)) {
                    log.error("find table event at {}:{}", logPosition.getFileName(), logPosition.getPosition());
                    findTableEvent.set(true);
                    return false;
                }
            }

            if (event instanceof QueryLogEvent) {
                QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                String query = queryLogEvent.getQuery();
                if (!query.equalsIgnoreCase("begin")) {
                    log.info(queryLogEvent.getQuery());
                }
                return !queryLogEvent.getQuery().contains(String.format(DROP_TABLE_SQL, DB_NAME, endToken));
            }

            if (event instanceof RotateLogEvent) {
                int seq = BinlogFileUtil.getBinlogSequence(logPosition.getFileName());
                // 超过5个binlog文件还没找到删表语句，链路存在问题
                if (seq - fileSeq > 5) {
                    timeOut.set(true);
                    return false;
                }
            }
            return true;
        }, params);
    }
}