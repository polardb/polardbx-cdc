/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.random;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.math.RandomUtils;

public class RandomUtil {
    public static String randomIdentifier() {
        return RandomStringUtils.randomAlphanumeric(nextPositiveInt(8, 16));
    }

    public static int nextInt(int length) {
        return RandomUtils.nextInt(length);
    }

    public static long nextLong() {
        return RandomUtils.nextLong();
    }

    public static int nextPositiveInt(int min, int n) {
        int res = RandomUtils.nextInt(n);
        return Math.max(res, min);
    }

    public static boolean randomBoolean() {
        return RandomUtils.nextBoolean();
    }
}
