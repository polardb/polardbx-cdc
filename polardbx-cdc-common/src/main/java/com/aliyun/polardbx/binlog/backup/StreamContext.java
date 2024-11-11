/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.domain.TaskType;
import lombok.Data;

import java.util.List;

/**
 * @author yudong
 * @since 2023/5/4 15:25
 **/
@Data
public class StreamContext {
    private final String group;
    private final List<String> streamList;
    private final String clusterId;
    private final String taskName;
    private final TaskType taskType;
    private final long version;
}
