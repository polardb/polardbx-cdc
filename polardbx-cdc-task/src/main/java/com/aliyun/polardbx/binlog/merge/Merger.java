/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

/**
 * Created by ziyang.lb
 **/
public interface Merger {

    void start();

    void stop();

    void addMergeSource(MergeSource mergeSource);

    void addHeartBeatWindowAware(HeartBeatWindowAware windowAware);
}
