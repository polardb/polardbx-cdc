/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author zm
 */
public class CharsetCache {
    private static final ConcurrentMap<String, Charset> CHARSET_CACHE = new ConcurrentHashMap<>();

    /**
     * 实际上行为和Charset.forName一致
     *
     * @return Charset
     */
    public static Charset lookup(String charsetName) {
        if (!StringUtils.isEmpty(charsetName)) {
            try {
                return CHARSET_CACHE.computeIfAbsent(charsetName, Charset::forName);
            } catch (Exception e) {
                throw new UnsupportedCharsetException(charsetName);
            }
        } else {
            throw new IllegalArgumentException("Null charset name");
        }
    }

    /**
     * 实际上行为和Charset.isSupported一致
     *
     * @return boolean
     */
    public static boolean isSupported(String charsetName) {
        if (!StringUtils.isEmpty(charsetName)) {
            try {
                Charset c = CHARSET_CACHE.computeIfAbsent(charsetName, Charset::forName);
                // 如果c是null，forName会抛出异常，这里直接返回true就行
                return true;
            } catch (Exception e) {
                return false;
            }
        } else {
            throw new IllegalArgumentException("Null charset name");
        }
    }
}
