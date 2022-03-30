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
