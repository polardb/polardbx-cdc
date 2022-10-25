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
package com.aliyun.polardbx.rpl.task;

import com.aliyun.polardbx.rpl.TestBase;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author shicai.xsc 2021/1/13 14:47
 * @since 5.0.0.0
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@Slf4j
public class CharsetWriteTest extends TestBase {

    @Before
    public void before() throws Exception {
        initConnection();
    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void insertData_Test2() throws Exception {
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
                    executeSql(srcDs, sql);

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
                    executeSql(srcDs, sql);

                    long stopTime = System.currentTimeMillis();
                    System.out.println("cost time: " + (stopTime - startTime) / 1000);
                    System.out.println("count: " + i);
                }
            });
        }

        Thread.sleep(1000000);
    }

    private void executeSql(DataSource dataSource, String sql) {
        try {
            Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.executeUpdate();
            DataSourceUtil.closeQuery(null, stmt, conn);
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void test() throws Exception {
        String s = "中文";
        byte[] gbkBytes = s.getBytes("GBK");
        byte[] utf8Bytes = s.getBytes("UTF-8");

        byte[] latin1Bytes = s.getBytes("ISO8859_1");
        System.out.println("latin1 encode, decode: ");
        System.out.println(new String(utf8Bytes, "UTF-8"));

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
        execUpdate(dstDs, sql, params);

        // 2. 直接插入 utf8Bytes
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(2);
        params.add(utf8Bytes);
        execUpdate(dstDs, sql, params);

        // 3. 插入 gbkBytes decode String
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(3);
        params.add(new String(gbkBytes, "GBK"));
        execUpdate(dstDs, sql, params);

        // 4. 插入 utf8Bytes decode String
        // 执行成功，通过 mysql 控制台查询 (建立连接后需设置 set names utf8mb4;)，结果 value 为 "中文"，说明插入正确
        params.clear();
        params.add(4);
        params.add(new String(utf8Bytes, "UTF-8"));
        execUpdate(dstDs, sql, params);
    }

    @Test
    public void testMysql() throws Exception {
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
            ps = srcDs.getConnection().prepareStatement(sql);
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
        String tableName = "checkout_order";
        PreparedStatement ps = srcDs.getConnection().prepareStatement("show full columns from ?");
        ps.setString(1, tableName);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String columnCollation = rs.getString("Collation");
            System.out.println("111111111: " + columnCollation);
        }
    }

    @Test
    public void testMysql3() throws Exception {
        PreparedStatement ps = srcDs.getConnection()
            .prepareStatement("select * from INFORMATION_SCHEMA.COLUMNS where TABLE_SCHEMA=? and TABLE_NAME=?");
        ps.setString(1, "rpl");
        ps.setString(2, "checkout_order");
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String columnCollation = rs.getString("COLLATION_NAME");
            System.out.println(columnCollation);
        }
    }
}
