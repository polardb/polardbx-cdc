/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import java.security.SecureRandom;

/**
 * @author shicai.xsc 2021/4/19 16:40
 * @since 5.0.0.0
 */
public class PasswordUtil {

    private static final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String lower = upper.toLowerCase();

    private static final String digits = "0123456789";

    private static final String special = "@#$%^&+=";

    public static String getRandomPassword() {
        String password = getRandomString(upper, 3) + getRandomString(lower, 3) + getRandomString(digits, 3)
            + getRandomString(special, 1);
        return password;
    }

    private static String getRandomString(String src, int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(src.charAt(random.nextInt(src.length())));
        }
        return sb.toString();
    }
}
