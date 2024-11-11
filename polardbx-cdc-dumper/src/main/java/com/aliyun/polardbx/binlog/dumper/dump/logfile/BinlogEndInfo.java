/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author ziyang.lb
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BinlogEndInfo {
    /**
     * Binlog文件中最后一个完整Event的时间戳
     */
    private Long lastEventTimeStamp;
    /**
     * Binlog文件中最后一个完整Event的Tso
     */
    private String lastEventTso;
}
