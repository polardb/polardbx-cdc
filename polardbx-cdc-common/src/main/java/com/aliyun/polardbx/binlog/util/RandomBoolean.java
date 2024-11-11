/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import java.util.Random;

/**
 * @author yudong
 * @since 2023/9/19 15:38
 **/
public class RandomBoolean {
    private final int percentage;

    public RandomBoolean(int percentage) {
        this.percentage = percentage;
    }

    public boolean nextBoolean() {
        return new Random().nextInt(100) < percentage;
    }
}
