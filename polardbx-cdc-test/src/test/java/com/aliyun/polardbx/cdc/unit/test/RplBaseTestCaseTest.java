/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.unit.test;

import com.alibaba.polardbx.core.cj.MysqlType;
import com.alibaba.polardbx.core.cj.NativeSession;
import com.alibaba.polardbx.core.cj.jdbc.JdbcConnection;
import com.alibaba.polardbx.core.cj.jdbc.JdbcPropertySetImpl;
import com.alibaba.polardbx.core.cj.jdbc.result.ResultSetImpl;
import com.alibaba.polardbx.core.cj.protocol.a.result.ByteArrayRow;
import com.alibaba.polardbx.core.cj.protocol.a.result.ResultsetRowsStatic;
import com.alibaba.polardbx.core.cj.result.DefaultColumnDefinition;
import com.alibaba.polardbx.core.cj.result.Field;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.cdc.qatest.base.ResultSetComparator;
import com.aliyun.polardbx.cdc.qatest.base.RplBaseTestCase;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class RplBaseTestCaseTest extends BaseTest {

    @Test
    public void testFloatBoundariesUseBigDecimalOnBothSides() throws Exception {
        BigDecimal[] values = {new BigDecimal("3.4028235E38"), new BigDecimal("-3.4028235E38"),
            new BigDecimal("1.4E-45"), new BigDecimal("1.25"), BigDecimal.ZERO, null};
        for (int type : new int[] {Types.REAL, Types.FLOAT}) {
            for (BigDecimal value : values) {
                ResultSet source = mockRow(new String[] {"value"}, new int[] {type}, new Object[] {value});
                ResultSet target = mockRow(new String[] {"value"}, new int[] {type}, new Object[] {value});
                Pair<List<Map<String, Object>>, List<Map<String, Object>>> data =
                    readDetails(source, target, null, null);
                Assert.assertEquals(value, data.getLeft().get(0).get("value"));
                Assert.assertEquals(value, data.getRight().get(0).get("value"));
                Assert.assertEquals(0, new ResultSetComparator().compare(data.getLeft(), data.getRight()));
                Mockito.verify(source).getBigDecimal(1);
                Mockito.verify(target).getBigDecimal(1);
                Mockito.verify(source, Mockito.never()).getObject(1);
                Mockito.verify(target, Mockito.never()).getObject(1);
            }
        }
    }

    @Test
    public void testConnectorFloatTextLimits() throws Exception {
        String[] values = {"3.4028235E38", "-3.4028235E38", "1.4E-45", "0", null};
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> data =
            readDetails(connectorRow(values), connectorRow(values), null, null);
        for (int i = 0; i < values.length; i++) {
            Object value = data.getLeft().get(0).get("value" + i);
            if (values[i] == null) {
                Assert.assertNull(value);
            } else {
                Assert.assertTrue(value instanceof BigDecimal);
                Assert.assertEquals(0, new BigDecimal(values[i]).compareTo((BigDecimal) value));
            }
        }
        Assert.assertEquals(0, new ResultSetComparator().compare(data.getLeft(), data.getRight()));
    }

    @Test
    public void testDefaultMappingAndEnumNormalizationArePreserved() throws Exception {
        String[] names = {"id", "double_value", "decimal_value", "text_value", "binary_value", "time_value",
            "nullable_value", "_ENUM_"};
        int[] types = {Types.BIGINT, Types.DOUBLE, Types.DECIMAL, Types.VARCHAR, Types.VARBINARY, Types.TIMESTAMP,
            Types.VARCHAR, Types.VARCHAR};
        Object[] values = {new BigInteger("18446744073709551615"), Double.MAX_VALUE, new BigDecimal("123.4500"),
            "text", new byte[] {0, 1, -1}, Timestamp.valueOf("2026-09-17 12:00:00"), null, "A"};
        ResultSet source = mockRow(names, types, values);
        ResultSet target = mockRow(names, types, values);
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> data = readDetails(source, target, 42L, "alias");
        for (int i = 0; i < names.length - 1; i++) {
            Assert.assertSame(values[i], data.getLeft().get(0).get(names[i]));
            Assert.assertSame(values[i], data.getRight().get(0).get(names[i]));
        }
        Assert.assertEquals("a", data.getLeft().get(0).get("_enum_"));
        Assert.assertEquals("a", data.getRight().get(0).get("_enum_"));
        Mockito.verify(source, Mockito.never()).getBigDecimal(Mockito.anyInt());
        Mockito.verify(target, Mockito.never()).getBigDecimal(Mockito.anyInt());
        Assert.assertEquals(0, new ResultSetComparator().compare(data.getLeft(), data.getRight()));
    }

    @Test
    public void testFloatDifferencesAndNullMismatchesRemainVisible() throws Exception {
        for (BigDecimal targetValue : new BigDecimal[] {new BigDecimal("-3.4028235E38"), null}) {
            ResultSet source = mockRow(new String[] {"value"}, new int[] {Types.REAL},
                new Object[] {new BigDecimal("3.4028235E38")});
            ResultSet target = mockRow(new String[] {"value"}, new int[] {Types.FLOAT},
                new Object[] {targetValue});
            Pair<List<Map<String, Object>>, List<Map<String, Object>>> data = readDetails(source, target, null, null);
            ResultSetComparator comparator = new ResultSetComparator();
            Assert.assertEquals(-1, comparator.compare(data.getLeft(), data.getRight()));
            Assert.assertTrue(comparator.getDiffColumns().containsKey("value"));
        }
    }

    @Test
    public void testEmptyTables() throws Exception {
        ResultSet source = Mockito.mock(ResultSet.class);
        ResultSet target = Mockito.mock(ResultSet.class);
        Pair<List<Map<String, Object>>, List<Map<String, Object>>> data = readDetails(source, target, null, null);
        Assert.assertTrue(data.getLeft().isEmpty());
        Assert.assertTrue(data.getRight().isEmpty());
    }

    private Pair<List<Map<String, Object>>, List<Map<String, Object>>> readDetails(
        ResultSet source, ResultSet target, Object key, String alias) throws Exception {
        JdbcTemplate srcTemplate = jdbcTemplate(source, "source", key);
        JdbcTemplate dstTemplate = jdbcTemplate(target, alias == null ? "source" : alias, key);
        return new TestCase(srcTemplate).getTableDetail("test_db", "source", alias, dstTemplate, key);
    }

    private static class TestCase extends RplBaseTestCase {
        private TestCase(JdbcTemplate source) {
            polardbxJdbcTemplate = source;
        }
    }

    private JdbcTemplate jdbcTemplate(ResultSet resultSet, String tableName, Object key) throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);
        String sql = "select * from `test_db`.`" + tableName + "`"
            + (key == null ? "" : " where id = '" + key + "'") + " order by id asc";
        Mockito.when(statement.executeQuery(sql)).thenReturn(resultSet);
        return new JdbcTemplate(dataSource);
    }

    private ResultSet mockRow(String[] names, int[] types, Object[] values) throws Exception {
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        ResultSetMetaData metadata = Mockito.mock(ResultSetMetaData.class);
        Mockito.when(resultSet.getMetaData()).thenReturn(metadata);
        Mockito.when(resultSet.next()).thenReturn(true, false);
        Mockito.when(metadata.getColumnCount()).thenReturn(names.length);
        for (int i = 0; i < names.length; i++) {
            Mockito.when(metadata.getColumnLabel(i + 1)).thenReturn(names[i]);
            Mockito.when(metadata.getColumnType(i + 1)).thenReturn(types[i]);
            if (types[i] == Types.REAL || types[i] == Types.FLOAT) {
                Mockito.when(resultSet.getBigDecimal(i + 1)).thenReturn((BigDecimal) values[i]);
            } else {
                Mockito.when(resultSet.getObject(i + 1)).thenReturn(values[i]);
            }
        }
        return resultSet;
    }

    private ResultSet connectorRow(String... values) throws Exception {
        Field[] fields = new Field[values.length];
        byte[][] bytes = new byte[values.length][];
        for (int i = 0; i < values.length; i++) {
            fields[i] = new Field("source", "value" + i, 33, "UTF-8", MysqlType.FLOAT, 12);
            bytes[i] = values[i] == null ? null : values[i].getBytes(StandardCharsets.US_ASCII);
        }
        DefaultColumnDefinition metadata = new DefaultColumnDefinition(fields);
        ByteArrayRow row = new ByteArrayRow(bytes, null);
        row.setMetadata(metadata);
        NativeSession session = Mockito.mock(NativeSession.class, Mockito.RETURNS_DEEP_STUBS);
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Mockito.when(session.getServerSession().getDefaultTimeZone()).thenReturn(utc);
        Mockito.when(session.getServerSession().getSessionTimeZone()).thenReturn(utc);
        Mockito.when(session.getProtocol().getServerSession().getDefaultTimeZone()).thenReturn(utc);
        Mockito.when(session.getProtocol().getServerSession().getSessionTimeZone()).thenReturn(utc);
        JdbcConnection connection = Mockito.mock(JdbcConnection.class);
        Mockito.when(connection.getSession()).thenReturn(session);
        Mockito.when(connection.getPropertySet()).thenReturn(new JdbcPropertySetImpl());
        Mockito.when(connection.getConnectionMutex()).thenReturn(new Object());
        return new ResultSetImpl(new ResultsetRowsStatic(Collections.singletonList(row), metadata), connection, null);
    }
}
