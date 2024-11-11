/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author shicai.xsc 2021/4/30 19:26
 * @since 5.0.0.0
 */
@Data
public class MergeDmlSqlContext extends SqlContext {
    protected boolean succeed;
    protected List<DefaultRowChange> originRowChanges;

    public MergeDmlSqlContext(String sql, String dstSchema, String dstTable, List<Serializable> params) {
        super(sql, dstSchema, dstTable, params);
    }
}
