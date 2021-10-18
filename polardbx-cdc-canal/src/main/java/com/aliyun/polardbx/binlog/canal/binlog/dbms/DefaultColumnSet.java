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

package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.util.List;

/**
 * This class creates a default SQL column set implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultColumnSet extends DBMSColumnSet {
    private static final long serialVersionUID = -3429762238191668175L;

    protected List<? extends DBMSColumn> columns;

    public DefaultColumnSet() {
    }

    /**
     * Create a new <code>DefaultColumnSet</code> object.
     */
    public DefaultColumnSet(List<? extends DBMSColumn> columns) {
        this.columns = columns;
        initColumns(columns);
    }

    /**
     * Return all columns in object.
     */
    public List<? extends DBMSColumn> getColumns() {
        return columns;
    }

    /**
     *
     */
    public void setColumns(List<? extends DBMSColumn> columns) {
        this.columns = columns;
    }
}