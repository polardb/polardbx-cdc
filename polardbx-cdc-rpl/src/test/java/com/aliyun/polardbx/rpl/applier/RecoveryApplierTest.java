/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.sql.Types;

import static org.junit.Assert.assertEquals;

public class RecoveryApplierTest {

    private RecoveryApplier applier;

    @Before
    public void setUp() {
        applier = Mockito.mock(RecoveryApplier.class, Mockito.CALLS_REAL_METHODS);
    }

    @Test
    public void valueWrapper_null_returnsNULL() {
        assertEquals("NULL", applier.valueWrapper(null, Types.VARCHAR));
    }

    @Test
    public void valueWrapper_simpleString() {
        assertEquals("'hello'", applier.valueWrapper("hello", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_backslash() {
        assertEquals("'hello\\\\world'", applier.valueWrapper("hello\\world", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_singleQuote() {
        assertEquals("'it\\'s'", applier.valueWrapper("it's", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_doubleQuote() {
        assertEquals("'say\\\"hi\\\"'", applier.valueWrapper("say\"hi\"", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_newline() {
        assertEquals("'line1\\nline2'", applier.valueWrapper("line1\nline2", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_carriageReturn() {
        assertEquals("'line1\\rline2'", applier.valueWrapper("line1\rline2", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_tab() {
        assertEquals("'col1\\tcol2'", applier.valueWrapper("col1\tcol2", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_nullByte() {
        assertEquals("'abc\\0def'", applier.valueWrapper("abc\0def", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_ctrlZ() {
        assertEquals("'abc\\Zdef'", applier.valueWrapper("abc\u001Adef", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_backspace() {
        assertEquals("'abc\\bdef'", applier.valueWrapper("abc\bdef", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_backslashAndQuoteCombined() {
        // input: C:\Users\'admin'
        // expected: 'C:\\Users\\'admin\''
        assertEquals("'C:\\\\Users\\\\\\'admin\\''", applier.valueWrapper("C:\\Users\\'admin'", Types.VARCHAR));
    }

    @Test
    public void valueWrapper_byteArray_hex() {
        byte[] data = new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        assertEquals("0xDEADBEEF", applier.valueWrapper(data, Types.VARBINARY));
    }

    @Test
    public void valueWrapper_bitType_byteArray() {
        byte[] data = new byte[] {0x01};
        assertEquals("1", applier.valueWrapper(data, Types.BIT));
    }

    @Test
    public void valueWrapper_bitType_integer() {
        assertEquals("5", applier.valueWrapper(5, Types.BIT));
    }

    @Test
    public void valueWrapper_bitType_long() {
        assertEquals("100", applier.valueWrapper(100L, Types.BIT));
    }
}
