/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.utils;

public enum CharsetMaxLenEnum {
    BIG5(2), DEC8(1), CP850(1), HP8(1), KOI8R(1), LATIN1(1), LATIN2(1), SWE7(1), ASCII(1), UJIS(3), SJIS(2), HEBREW(1),
    TIS620(
        1), EUCKR(2), KOI8U(1), GB2312(2), GREEK(1), CP1250(1), GBK(2), LATIN5(1), ARMSCII8(1), UTF8(3), UCS2(2), CP866(
        1), KEYBCS2(1), MACCE(1), MACROMAN(1), CP852(1), LATIN7(1), UTF8MB4(4), CP1251(1), UTF16(4), UTF16LE(4), CP1256(
        1), CP1257(1), UTF32(4), BINARY(1), GEOSTD8(1), CP932(2), EUCJPMS(3), GB18030(4),
    ;
    private int maxLen;

    CharsetMaxLenEnum(int maxLen) {
        this.maxLen = maxLen;
    }

    public static CharsetMaxLenEnum find(String name) {
        name = name.toUpperCase();
        for (CharsetMaxLenEnum charsetMaxLenEnum : values()) {
            if (charsetMaxLenEnum.name().equals(name)) {
                return charsetMaxLenEnum;
            }
        }
        return null;
    }

    public int getMaxLen() {
        return maxLen;
    }
}
