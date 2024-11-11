/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

import java.nio.charset.Charset;
import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;

public class CollationCharset {

    public static CollationCharset defaultCharset;
    public static CollationCharset utf8mb4Charset;
    public static Charset defaultJavaCharset = Charset.forName("utf8");

    static {
        // default set is utf8
        CharsetConversion.Entry utf8entry = CharsetConversion.getEntry(33);
        defaultCharset = new CollationCharset(utf8entry.mysqlCharset, utf8entry.mysqlCollation, utf8entry.charsetId);
        // default set is utf8mb4
        CharsetConversion.Entry utf8mb4entry = CharsetConversion.getEntry(45);
        utf8mb4Charset = new CollationCharset(utf8mb4entry.mysqlCharset,
            utf8mb4entry.mysqlCollation,
            utf8mb4entry.charsetId);
    }

    private String charset;
    private String collation;
    private int id;

    public CollationCharset(String charset, String collation, int id) {
        this.charset = charset;
        this.collation = collation;
        this.id = id;
    }

    public String getCharset() {
        return charset;
    }

    public String getCollation() {
        return collation;
    }

    public int getId() {
        return id;
    }
}
