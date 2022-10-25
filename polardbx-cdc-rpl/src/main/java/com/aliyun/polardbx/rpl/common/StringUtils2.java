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
package com.aliyun.polardbx.rpl.common;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * @author sunxi'en
 * @since 1.0.0
 */
public class StringUtils2 extends StringUtils {

    /**
     * @return String
     */
    public static String substringPrefix(String str, String prefix) {
        if (str != null && prefix != null) {
            if (str.startsWith(prefix)) {
                str = str.substring(prefix.length(), str.length());
            }
        }
        return str;
    }

    /**
     * @return String
     */
    public static String substringSuffix(String str, String suffix) {
        if (str != null && suffix != null) {
            if (str.endsWith(suffix)) {
                str = str.substring(0, str.length() - suffix.length());
            }
        }
        return str;
    }

    /**
     * @return String
     */
    public static String safeToString(Object object) {
        return safeToString(object, EMPTY);
    }

    /**
     * @return String
     */
    public static String safeToString(Object object, String nullStr) {
        if (object == null || (object instanceof String && EMPTY.equals(object.toString()))) {
            return nullStr;
        } else if (object.getClass().isArray()) {
            return ArrayUtils.toString(object);
        } else {
            return object.toString();
        }
    }

    /**
     * @return String
     */
    public static String substringBetween2(String str, String open, String close) {
        if (str == null || open == null || close == null) {
            return str;
        }
        int start = str.indexOf(open);
        if (start != INDEX_NOT_FOUND) {
            int end = str.indexOf(close, start + open.length());
            if (end != INDEX_NOT_FOUND) {
                return str.substring(start + open.length(), end);
            }
        }
        return str;
    }

    /**
     * refer to org.apache.commons.lang3.StringUtils
     */
    public static String replace(final String text, final String searchString, final String replacement) {
        return replace(text, searchString, replacement, -1);
    }

    public static String replace(final String text, final String searchString, final String replacement, int max) {
        if (isEmpty(text) || isEmpty(searchString) || replacement == null || max == 0) {
            return text;
        }
        int start = 0;
        int end = text.indexOf(searchString, start);
        if (end == INDEX_NOT_FOUND) {
            return text;
        }
        final int replLength = searchString.length();
        int increase = replacement.length() - replLength;
        increase = increase < 0 ? 0 : increase;
        increase *= max < 0 ? 16 : max > 64 ? 64 : max;
        final StringBuilder buf = new StringBuilder(text.length() + increase);
        while (end != INDEX_NOT_FOUND) {
            buf.append(text.substring(start, end)).append(replacement);
            start = end + replLength;
            if (--max == 0) {
                break;
            }
            end = text.indexOf(searchString, start);
        }
        buf.append(text.substring(start));
        return buf.toString();
    }

    /**
     * 返回两个字符串后缀匹配的长度
     */
    public static int suffixMatchLengthIgnoreCase(String str1, String str2) {
        if (StringUtils.isBlank(str1) || StringUtils.isBlank(str2)) {
            return 0;
        } else {
            str1 = str1.toLowerCase();
            str2 = str2.toLowerCase();
            for (int i = str1.length() - 1, j = str2.length() - 1; i >= 0 && j >= 0; i--, j--) {
                if (str1.charAt(i) != str2.charAt(j)) {
                    return (str1.length() - 1 - i);
                }
            }
            return Math.min(str1.length(), str2.length());
        }
    }
}