/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import java.util.Map;

/**
 * Created by ziyang.lb
 **/
public interface Merger {

    void start();

    void stop();

    void addMergeSource(MergeSource mergeSource);

    Map<String, MergeSource> getMergeSources();

    void addHeartBeatWindowAware(HeartBeatWindowAware windowAware);
}
