/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
    public boolean init(String destination);

    /**
     * 销毁资源
     */
    public void destory();

    /**
     * 获取当前的表结构
     */
    public TableMeta find(String schema, String table);

    /**
     * 添加ddl到时间序列库中
     */
    public boolean apply(BinlogPosition position, String schema, String ddl, String extra);

    /**
     * 回滚到指定位点的表结构
     */
    public boolean rollback(BinlogPosition position);

    /**
     * 生成快照内容
     */
    public Map<String/* schema */, String> snapshot();

}
