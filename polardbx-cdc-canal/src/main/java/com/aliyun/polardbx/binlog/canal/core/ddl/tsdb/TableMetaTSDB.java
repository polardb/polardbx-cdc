/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

import java.util.Map;

/**
 * 表结构的时间序列存储
 *
 * @author agapple 2017年7月27日 下午4:06:30
 * @since 3.2.5
 */
public interface TableMetaTSDB {

    /**
     * 初始化
     */
    boolean init(String destination);

    /**
     * 销毁资源
     */
    void destroy();

    /**
     * 获取当前的表结构
     */
    TableMeta find(String schema, String table);

    /**
     * 添加ddl到时间序列库中
     */
    boolean apply(BinlogPosition position, String schema, String ddl, String extra);

    /**
     * 回滚到指定位点的表结构
     */
    boolean rollback(BinlogPosition position);

    /**
     * 生成快照内容
     */
    Map<String/* schema */, String> snapshot();

    BinlogPosition INIT_POSITION = BinlogPosition.parseFromString("0:0#-2.-1");
}
