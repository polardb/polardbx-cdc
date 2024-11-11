/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.task;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.rpl.RplTaskRunner;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.RplServiceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author shicai.xsc 2021/1/13 14:47
 * @since 5.0.0.0
 */
@Ignore
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class StateMachineTest {

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void setupService_Test() {
        Map<String, String> params = new HashMap<>();
        params.put(RplConstants.MASTER_HOST, "");
        params.put(RplConstants.MASTER_PORT, "");
        params.put(RplConstants.MASTER_USER, "");
        params.put(RplConstants.MASTER_PASSWORD, "");
        params.put(RplConstants.MASTER_LOG_FILE, "");
        params.put(RplConstants.MASTER_LOG_POS, "");
        params.put(RplConstants.CHANNEL, "public");

        RplServiceManager.changeMaster(params);
    }

    @Test
    public void startTask_Test() throws Exception {
        RplTaskRunner runner = new RplTaskRunner(8);
        runner.start();
    }

    @Test
    public void insertData_Test2() throws Exception {
        DataSource ds1 = createDataSource("rpl");
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(8, new NamedThreadFactory("test"));
        List<String> tables = Arrays.asList("aaaaa");
        for (String table : tables) {
            executorService.submit(() -> {
                int i = 1500000;
                for (; i < 100000000; i++) {
                    StringBuilder sb = new StringBuilder();

                    String values = "(%d, '%d', '%d'),";
                    int j = 0;
                    for (; j < 1000; j++) {
                        sb.append(String.format(values, i + j, i + j, i + j));
                    }
                    i += j + 1;

                    String sql = String.format("insert into %s values %s;", table, sb);
                    sql = sql.substring(0, sql.length() - 2);
                    executeSql(ds1, sql);

                    long stopTime = System.currentTimeMillis();
                    System.out.println("cost time: " + (stopTime - startTime) / 1000);
                    System.out.println("count: " + i);
                }
            });
        }

        Thread.sleep(1000000);
    }

    @Test
    public void insertData_Test() throws Exception {
        DataSource ds1 = createDataSource("c");
        long startTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newFixedThreadPool(8, new NamedThreadFactory("test"));
        List<String> tables = Arrays
            .asList("bbb");
        for (String table : tables) {
            executorService.submit(() -> {
                int i = 300000;
                for (; i < 100000000; i++) {
                    StringBuilder sb = new StringBuilder();

                    String values = "(%d, '%d', '%d'),";
                    int j = 0;
                    for (; j < 1000; j++) {
                        sb.append(String.format(values, i + j, i + j, i + j, i + j));
                    }
                    i += j + 1;

                    String sql = String.format("insert into %s values %s;", table, sb);
                    sql = sql.substring(0, sql.length() - 2);
                    executeSql(ds1, sql);

                    long stopTime = System.currentTimeMillis();
                    System.out.println("cost time: " + (stopTime - startTime) / 1000);
                    System.out.println("count: " + i);
                }
            });
        }

        Thread.sleep(1000000);
    }

    @Test
    public void ggg() throws Exception {
        ApplierConfig applierConfig = new ApplierConfig();
        System.out.println(JSON.toJSONString(applierConfig));

        Serializable a = 100;
        Serializable b = "aaaa";
        System.out.println(a.hashCode());
        System.out.println(b.hashCode());
    }

    private void executeSql(DataSource dataSource, String sql) {
        try {
            Connection conn = null;
            PreparedStatement stmt = null;
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.executeUpdate();
            DataSourceUtil.closeQuery(null, stmt, conn);
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }
    }

    private DataSource createDataSource(String schema) throws Exception {
        DataSource dataSource = DataSourceUtil.createDruidMySqlDataSource(false,
            "",
            3307,
            "",
            "",
            "",
            "",
            1,
            16,
            true,
            null,
            null);
        return dataSource;
    }

    @Test
    public void test() throws Exception {
        String s = "中文";
        byte[] gbkBytes = s.getBytes("GBK");
        byte[] utf8Bytes = s.getBytes("UTF-8");

        byte[] latin1Bytes = s.getBytes("ISO8859_1");
        System.out.println("latin1 encode, decode: ");
        System.out.println(new String(utf8Bytes, "UTF-8"));

        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "", 3306, "", "root", "", "", 1, 1, true, null, null);

        // 目标库 col_charset_gbk_utf8，编码 utf8mb4
        // 目标表 a (id int, value mediumtext)

        String sql = "INSERT INTO dgbk.col_charset_gbk_utf8 (id, value) VALUES (?, ?)";
        List<Serializable> params = new ArrayList<>();

        // 1. 直接插入 gbkBytes
        // 报错 java.sql.SQLException: Incorrect string value: '\xD6\xD0\xCE\xC4' for
        // column 'value' at row 1
        params.clear();
        params.add(1);
        params.add(gbkBytes);
        execUpdate(dataSource, sql, params);

        // 2. 直接插入 utf8Bytes
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(2);
        params.add(utf8Bytes);
        execUpdate(dataSource, sql, params);

        // 3. 插入 gbkBytes decode String
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(3);
        params.add(new String(gbkBytes, "GBK"));
        execUpdate(dataSource, sql, params);

        // 4. 插入 utf8Bytes decode String
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(4);
        params.add(new String(utf8Bytes, "UTF-8"));
        execUpdate(dataSource, sql, params);
    }

    public boolean execUpdate(DataSource dataSource, String sql, List<Serializable> params) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);

            // set value
            int i = 1;
            for (Serializable dataValue : params) {
                stmt.setObject(i, dataValue);
                i++;
            }

            // execute
            stmt.executeUpdate();
            return true;
        } catch (Throwable e) {
            log.error("failed in execUpdate: " + sql, e);
            return false;
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
    }

    @Test
    public void testMysql() throws Exception {
        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "127.0.0.1", 3306, "rpl", "root", "123456", "", 1, 16, true, null, null);

        String tableName = "checkout_order";
        String[] columnNames = {"id", "checkout_order_no", "payer_id", "gmt_create", "gmt_modified", "version"};
        String[] placeHolders = new String[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            placeHolders[i] = "?";
        }

        String sql = String.format("insert into `%s` (%s) values(%s)",
            tableName,
            StringUtils.join(columnNames, ","),
            StringUtils.join(placeHolders, ","));

        PreparedStatement ps = null;
        try {
            ps = dataSource.getConnection().prepareStatement(sql);
            ps.setObject(1, "1", Types.BIGINT);
            ps.setObject(2, "1", Types.VARCHAR);
            ps.setObject(3, "1", Types.BIGINT);
            ps.setObject(4, "2021-03-12 10:59:33", Types.TIMESTAMP);
            ps.setObject(5, "2021-03-12 10:59:33", Types.TIMESTAMP);
            ps.setObject(6, "1");
            ps.executeUpdate();
        } catch (Throwable e) {
            throw e;
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }

    @Test
    public void testMysql2() throws Exception {
        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "127.0.0.1", 3306, "rpl", "root", "123456", "", 1, 16, true, null, null);

        String tableName = "checkout_order";
        PreparedStatement ps = dataSource.getConnection().prepareStatement("show full columns from ?");
        ps.setString(1, tableName);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String columnCollation = rs.getString("Collation");
            System.out.println("111111111: " + columnCollation);
        }
    }

    @Test
    public void testMysql3() throws Exception {
        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "127.0.0.1", 3306, "rpl", "root", "123456", "", 1, 16, true, null, null);

        String col = "%" + "100" + "%";
        String sql = "SELECT * FROM flink_test_dst WHERE name LIKE ?";
        PreparedStatement ps = dataSource.getConnection().prepareStatement(sql);
        ps.setObject(1, col);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String id = rs.getString("id");
            System.out.println("id: " + id);
        }
    }

    @Test
    public void testMysql4() throws Exception {
        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "127.0.0.1", 3306, "rpl", "root", "123456", "", 1, 16, true, null, null);

        PreparedStatement stmt = dataSource.getConnection()
            .prepareStatement("SELECT count(1) FROM flink_test_dst WHERE name LIKE ?");
        stmt.setString(1, "%" + "100" + "%");
        ResultSet res = stmt.executeQuery();
        if (res.next()) {
            System.out.println(res.getInt(1));
        }
    }

    @Test
    public void testMysql5() throws Exception {
        DataSource dataSource = DataSourceUtil
            .createDruidMySqlDataSource(false, "127.0.0.1", 3306, "rpl", "root", "123456", "", 1, 16, true, null, null);

        PreparedStatement ps = dataSource.getConnection()
            .prepareStatement("select * from INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA=? and TABLE_NAME=?");
        ps.setString(1, "rpl");
        ps.setString(2, "checkout_order");
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            // String columnCollation = rs.getString("Collation");
            String columnCollation = rs.getString("COLLATION_NAME");
            System.out.println(columnCollation);
        }
    }
}
