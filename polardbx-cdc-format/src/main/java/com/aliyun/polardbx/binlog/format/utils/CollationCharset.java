/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
