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

package com.aliyun.polardbx.rpl.applier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;

import lombok.Data;

/**
 * @author shicai.xsc 2021/3/17 16:30
 * @since 5.0.0.0
 */
@Data
public class Transaction {

    private Set<String> tables = new HashSet<>();
    private List<DBMSEvent> events = new ArrayList<>();
    private boolean finished = false;
    private boolean prepared = false;

    public void appendRowChange(DBMSEvent event) {
        events.add(event);
        DBMSRowChange rowChange = (DBMSRowChange) event;
        String fullTableName = rowChange.getSchema() + "." + rowChange.getTable();
        tables.add(fullTableName);
    }

    public void appendQueryLog(DBMSEvent event) {
        events.add(event);
    }
}
