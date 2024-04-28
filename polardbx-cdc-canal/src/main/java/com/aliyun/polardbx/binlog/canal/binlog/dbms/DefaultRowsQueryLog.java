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
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a SQL statement from query-log that must be replicated. Default implementation of {@link DefaultQueryLog
 * DefaultQueryLog}
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultRowsQueryLog extends DBMSEvent {

    private static final long serialVersionUID = -3393475008857141186L;
    protected String rowsQuery;
    private DBMSAction action;

    public DefaultRowsQueryLog(String rowsQuery, DBMSAction action) {
        this.rowsQuery = rowsQuery;
        this.action = action;
    }

    public String getRowsQuery() {
        return rowsQuery;
    }

    @Override
    public DBMSAction getAction() {
        return action;
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public void setSchema(String schema) {

    }

    @Override
    public List<? extends DBMSOption> getOptions() {
        return new ArrayList<>();
    }

    @Override
    public void setOptionValue(String name, Serializable value) {
    }
}
