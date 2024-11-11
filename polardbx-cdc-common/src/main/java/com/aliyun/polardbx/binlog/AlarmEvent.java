/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by ziyang.lb
 **/
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AlarmEvent {
    private String eventKey;
    private String eventValue;
    private Object triggerValue;
}
