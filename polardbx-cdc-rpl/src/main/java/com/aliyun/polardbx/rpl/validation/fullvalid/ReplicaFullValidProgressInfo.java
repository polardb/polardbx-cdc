/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid;

import lombok.Data;

/**
 * @author yudong
 * @since 2023/11/10 17:12
 **/
@Data
public class ReplicaFullValidProgressInfo {
    String dbName;
    String tbName;
    String stage;
    String status;
    String summary;
}
