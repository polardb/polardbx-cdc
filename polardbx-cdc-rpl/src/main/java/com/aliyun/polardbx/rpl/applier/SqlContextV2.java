/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.applier;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author shicai.xsc 2020/12/1 22:06
 * @since 5.0.0.0
 */
@Data
public class SqlContextV2 {

    protected String sql;
    protected String dstSchema;
    protected String dstTable;
    protected String fullTable;
    protected Boolean succeed = false;
    protected List<List<Serializable>> paramsList;

    public SqlContextV2(String sql, String dstSchema, String dstTable, List<List<Serializable>> paramsList) {
        this.sql = sql;
        this.dstSchema = dstSchema;
        this.dstTable = dstTable;
        this.paramsList = paramsList;
        this.fullTable = dstSchema + "." + dstTable;
    }
}
