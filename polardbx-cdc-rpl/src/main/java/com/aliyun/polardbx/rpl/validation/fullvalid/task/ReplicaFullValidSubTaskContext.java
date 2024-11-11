/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yudong
 * @since 2023/10/25 19:04
 **/
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ReplicaFullValidSubTaskContext {
    private DbMetaCache srcDbMetaCache;
    private DbMetaCache dstDbMetaCache;
    private long fsmId;
    private long fullValidTaskId;
    private long subTaskId;
}
