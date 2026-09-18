/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * @author zm
 */
public class CharsetCacheTest extends BaseTest {

    @Test
    public void testLookupWithValidCharset() {
        // Test with a valid charset name
        Charset charset = CharsetCache.lookup("UTF-8");
        Assert.assertNotNull(charset);
        Assert.assertEquals("UTF-8", charset.name());

        // Test that the same charset is returned from cache
        Charset cachedCharset = CharsetCache.lookup("UTF-8");
        Assert.assertSame(charset, cachedCharset);
    }

    @Test
    public void testLookupWithDifferentCharsets() {
        // Test with different valid charsets
        Charset utf8Charset = CharsetCache.lookup("UTF-8");
        Charset asciiCharset = CharsetCache.lookup("US-ASCII");
        Charset isoCharset = CharsetCache.lookup("ISO-8859-1");

        Assert.assertNotNull(utf8Charset);
        Assert.assertNotNull(asciiCharset);
        Assert.assertNotNull(isoCharset);

        Assert.assertEquals("UTF-8", utf8Charset.name());
        Assert.assertEquals("US-ASCII", asciiCharset.name());
        Assert.assertEquals("ISO-8859-1", isoCharset.name());
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testLookupWithInvalidCharset() {
        // Test with an invalid charset name
        CharsetCache.lookup("invalid-charset-name");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLookupWithEmptyString() {
        // Test with empty string
        CharsetCache.lookup("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLookupWithNull() {
        // Test with null
        CharsetCache.lookup(null);
    }

    @Test
    public void testIsSupportedWithValidCharset() {
        // Test with valid charset names
        Assert.assertTrue(CharsetCache.isSupported("UTF-8"));
        Assert.assertTrue(CharsetCache.isSupported("US-ASCII"));
        Assert.assertTrue(CharsetCache.isSupported("ISO-8859-1"));
    }

    @Test
    public void testIsSupportedWithInvalidCharset() {
        // Test with invalid charset names
        Assert.assertFalse(CharsetCache.isSupported("invalid-charset-name"));
        Assert.assertFalse(CharsetCache.isSupported("non-existent-charset"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSupportedWithEmptyString() {
        // Test with empty string
        CharsetCache.isSupported("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsSupportedWithNull() {
        // Test with null
        CharsetCache.isSupported(null);
    }
}