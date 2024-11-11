/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

/**
 * @author chengjin.lyf on 2020/7/22 11:16 上午
 * @since 1.0.25
 */
public interface Extractor {

    void start(String startTSO);

    void stop();
}
