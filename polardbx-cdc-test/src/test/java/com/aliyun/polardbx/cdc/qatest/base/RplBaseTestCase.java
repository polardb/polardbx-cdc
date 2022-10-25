/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.usingBinlogX;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class RplBaseTestCase extends BaseTestCase {
    protected static final String TOKEN_DB = "cdc_token_db";

    protected static final String TOKEN_DB_CREATE_SQL = "CREATE DATABASE IF NOT EXISTS `" + TOKEN_DB + "`";

    protected static final String TOKEN_TABLE_PREFIX = "t_token_";

    protected static final String TOKEN_TABLE_CREATE_SQL =
        "create table if not exists " + TOKEN_DB + ".`%s` (id bigint not null,primary key(`id`))";

    protected Connection polardbxConnection;
    protected Connection cdcSyncDbConnection;
    protected Connection cdcSyncDbConnectionSecond;

    protected JdbcTemplate polardbxJdbcTemplate;
    protected JdbcTemplate cdcSyncDbJdbcTemplate;
    protected JdbcTemplate cdcSyncDbSecondJdbcTemplate;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        prepareCdcTokenDB();
    }

    @Before
    public void before() {
        this.polardbxConnection = getPolardbxConnection();
        this.polardbxJdbcTemplate = new JdbcTemplate(ConnectionManager.getInstance().getPolardbxDataSource());

        this.cdcSyncDbConnection = getCdcSyncDbConnection();
        this.cdcSyncDbJdbcTemplate = new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSource());

        if (usingBinlogX) {
            this.cdcSyncDbConnectionSecond = getCdcSyncDbConnectionSecond();
            this.cdcSyncDbSecondJdbcTemplate =
                new JdbcTemplate(ConnectionManager.getInstance().getCdcSyncDbDataSourceSecond());
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
        //send token
        String uuid = UUID.randomUUID().toString();
        String tableName = TOKEN_TABLE_PREFIX + uuid;
        JdbcUtil.executeSuccess(polardbxConnection, String.format(TOKEN_TABLE_CREATE_SQL, tableName));

        //wait token
        loopWait(tableName, cdcSyncDbConnection);
        if (usingBinlogX) {
            loopWait(tableName, cdcSyncDbConnectionSecond);
        }

        //execute callback
        check(checkParameter);
    }

    public void loopWait(String token, Connection connection) {
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                Statement statement = connection.createStatement();
                statement.executeQuery("show create table `" + TOKEN_DB + "`.`" + token + "`");
                break;
            } catch (Throwable ignored) {
            }
            if (System.currentTimeMillis() - startTime > 1000 * 120) {
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
            //TODO
        } else {
            if (parameter.isDirectCompareDetail()) {
                compareDetail(parameter.getDbName(), parameter.getTbName(), cdcSyncDbJdbcTemplate);
            } else {
                try {
                    compareChecksum(parameter.getDbName(), parameter.getTbName(), cdcSyncDbConnection);
                } catch (Throwable t) {
                    compareDetail(parameter.getDbName(), parameter.getTbName(), cdcSyncDbJdbcTemplate);
                }
            }
        }
    }

    public void compareDetail(String dbName, String tableName, JdbcTemplate dstJdbcTemplate) {
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> pair =
            getTableDetail(dbName, tableName, dstJdbcTemplate);
        Assert.assertEquals("src<" + pair.getLeft() + "> and dst<" + pair.getRight() + "> data show equals",
            0, new ResultSetComparator().compare(pair.getLeft(), pair.getRight()));
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
                                                                                     JdbcTemplate dstJdbcTemplate) {
        StringBuilder builder = new StringBuilder();

        builder.append("select * from `" + dbName + "`")
            .append(".")
            .append("`").append(tableName).append("`")
            .append("order by id asc");

        log.info("check sql {}", builder.toString());
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
}
