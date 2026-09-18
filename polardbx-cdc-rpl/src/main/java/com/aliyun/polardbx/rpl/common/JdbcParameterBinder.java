/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class JdbcParameterBinder {
    private JdbcParameterBinder() {
    }

    public static void bind(PreparedStatement statement, int index, Object value) throws SQLException {
        // Connector 2.2.9 infers signed BIGINT for BigInteger and truncates through longValue().
        // DECIMAL preserves the exact value for both writes and unsigned-key predicates.
        if (value instanceof BigInteger) {
            statement.setBigDecimal(index, new BigDecimal((BigInteger) value));
        } else {
            statement.setObject(index, value);
        }
    }
}
