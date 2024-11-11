/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.sort;

import com.aliyun.polardbx.binlog.extractor.log.TransPosInfo;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * Created by ziyang.lb
 **/
@Data
@Builder
@ToString
public class SortItem {
    private TxnKey txnKey;
    private SortItemType type;
    private Transaction transaction;
    private TransPosInfo transPosInfo;
}
