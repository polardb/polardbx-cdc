/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import lombok.Data;

import java.util.List;

/**
 * @author chengjin.lyf on 2019/5/7 11:34 AM
 * @since 1.0.25
 */
@Data
public class DescribeBinlogFilesResult {

    private Integer DBInstanceID;
    private String DBInstanceName;
    private String StartTime;
    private String EndTime;
    private Integer TotalRecords;
    private Integer MaxRecordsPerPage;
    private Integer PageNumbers;
    private Integer ItemsNumbers;
    private List<BinlogFile> Items;
}
