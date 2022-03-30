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

package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/15 16:51
 * @since 5.0.0.0
 */
@Data
public class ColumnValue {

    private final Object value;
    private final ColumnInfo column;

    public ColumnValue(Object value, ColumnInfo column) {
        this.value = value;
        this.column = column;
    }

    @Override
    public String toString() {
        if (column != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("column:");
            sb.append(column.getName());
            if (value != null) {
                sb.append(",value:");
                sb.append(String.valueOf(value));
            } else {
                sb.append(",value:null");
            }
            return sb.toString();
        } else {
            return null;
        }
    }
}
