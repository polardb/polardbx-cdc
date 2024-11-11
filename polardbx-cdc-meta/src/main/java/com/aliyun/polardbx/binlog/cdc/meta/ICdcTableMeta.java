/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.canal.core.ddl.tsdb.TableMetaTSDB;

/**
 * Created by ziyang.lb
 */
public interface ICdcTableMeta extends TableMetaTSDB {

    /**
     * Apply镜像元数据
     */
    void applySnapshot(String snapshotTso);

    /**
     * Apply历史变更
     */
    void applyHistory(String snapshotTso, String rollbackTso);

}
