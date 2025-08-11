/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.dbs;

import lombok.Data;

import java.util.List;

@Data
public class ArchiveLogPages {
    private Extra Extra;
    private int PageSize;
    private int PageNumber;
    private List<DbsBinlogFile> Content;
    private int TotalElements;
    private int TotalPages;

    @Data
    public class Extra {
        private Long TotalLogSize;
    }
}
