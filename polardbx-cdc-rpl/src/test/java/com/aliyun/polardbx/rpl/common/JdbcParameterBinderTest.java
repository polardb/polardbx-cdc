/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import com.alibaba.polardbx.core.cj.NativeQueryBindValue;
import com.alibaba.polardbx.core.cj.NativeQueryBindings;
import com.alibaba.polardbx.core.cj.NativeSession;
import com.alibaba.polardbx.core.cj.conf.PropertyKey;
import com.alibaba.polardbx.core.cj.jdbc.JdbcPropertySetImpl;
import com.alibaba.polardbx.core.cj.protocol.a.NumberValueEncoder;
import com.aliyun.polardbx.rpl.applier.SqlContext;
import com.aliyun.polardbx.rpl.applier.SqlContextExecutor;
import com.aliyun.polardbx.rpl.applier.SqlContextV2;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JdbcParameterBinderTest {
    private static final String[] BOUNDARIES = {
        "-9223372036854775808", "-1", "0", "9223372036854775807", "9223372036854775808", "18446744073709551615"
    };

    @Test
    public void realConnectorBindingsPreserveBigIntegerBoundaries() throws Exception {
        NativeSession session = Mockito.mock(NativeSession.class, Mockito.RETURNS_DEEP_STUBS);
        JdbcPropertySetImpl properties = new JdbcPropertySetImpl();
        properties.getStringProperty(PropertyKey.characterEncoding).setValue("UTF-8");
        Mockito.when(session.getPropertySet()).thenReturn(properties);
        Mockito.when(session.getProtocol().getValueEncoderSupplier(Mockito.any()))
            .thenAnswer(invocation -> (java.util.function.Supplier<NumberValueEncoder>) NumberValueEncoder::new);
        NativeQueryBindings bindings = new NativeQueryBindings(1, session, NativeQueryBindValue::new);
        // Inspect the real driver's encoded bytes, not just a mock expectation of the JDBC call.
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Mockito.doAnswer(invocation -> {
            bindings.setBigDecimal(invocation.getArgument(0, Integer.class) - 1, invocation.getArgument(1));
            return null;
        }).when(statement).setBigDecimal(Mockito.anyInt(), Mockito.any(BigDecimal.class));
        for (String value : BOUNDARIES) {
            JdbcParameterBinder.bind(statement, 1, new BigInteger(value));
            Assert.assertEquals(value, encoded(bindings));
        }
        Mockito.verify(statement, Mockito.never()).setObject(Mockito.anyInt(), Mockito.any());
    }

    private static String encoded(NativeQueryBindings bindings) {
        return new String(bindings.getBindValues()[0].getByteValue(), StandardCharsets.UTF_8);
    }

    @Test
    public void otherTypesAndNullKeepExistingBindingSemantics() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Object[] values = {null, -1L, 17, new BigDecimal("12.3400"), "text", new byte[] {0, -1},
            java.sql.Timestamp.valueOf("2026-09-16 12:34:56")};
        for (int i = 0; i < values.length; i++) {
            JdbcParameterBinder.bind(statement, i + 1, values[i]);
            Mockito.verify(statement).setObject(i + 1, values[i]);
        }
        Mockito.verify(statement, Mockito.never()).setBigDecimal(Mockito.anyInt(), Mockito.any());
    }

    @Test
    public void singleBatchAndWherePathsKeepExactValues() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rpl_unsigned_parameter_binding");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t (id DECIMAL(20,0) PRIMARY KEY, payload DECIMAL(20,0))");
            BigInteger first = new BigInteger(BOUNDARIES[0]);
            Assert.assertEquals(1, SqlContextExecutor.execUpdate(connection,
                new SqlContext("INSERT INTO t VALUES (?,?)", "test", "t", Arrays.asList(first, first))));
            List<List<Serializable>> rows = new ArrayList<>();
            for (int i = 1; i < BOUNDARIES.length; i++) {
                BigInteger value = new BigInteger(BOUNDARIES[i]);
                rows.add(Arrays.asList(value, value));
            }
            SqlContextExecutor.execUpdate(dataSource, new SqlContextV2("INSERT INTO t VALUES (?,?)", "test", "t", rows));
            for (String value : BOUNDARIES) {
                try (PreparedStatement query = connection.prepareStatement("SELECT payload FROM t WHERE id=?")) {
                    JdbcParameterBinder.bind(query, 1, new BigInteger(value));
                    try (ResultSet result = query.executeQuery()) {
                        Assert.assertTrue(result.next());
                        Assert.assertEquals(value, result.getBigDecimal(1).toPlainString());
                        Assert.assertFalse(result.next());
                    }
                }
            }
            BigInteger max = new BigInteger("18446744073709551615");
            Assert.assertEquals(1, SqlContextExecutor.execUpdate(connection,
                new SqlContext("UPDATE t SET payload=? WHERE id=?", "test", "t", Arrays.asList(max, first))));
            try (PreparedStatement query = connection.prepareStatement("SELECT payload FROM t WHERE id=?")) {
                JdbcParameterBinder.bind(query, 1, first);
                try (ResultSet result = query.executeQuery()) {
                    Assert.assertTrue(result.next());
                    Assert.assertEquals(max.toString(), result.getBigDecimal(1).toPlainString());
                    Assert.assertFalse(result.next());
                }
            }
            Assert.assertEquals(1, SqlContextExecutor.execUpdate(connection,
                new SqlContext("DELETE FROM t WHERE id=?", "test", "t", Collections.singletonList(max))));
            try (ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM t")) {
                Assert.assertTrue(count.next());
                Assert.assertEquals(BOUNDARIES.length - 1, count.getInt(1));
            }
        }
    }
}
