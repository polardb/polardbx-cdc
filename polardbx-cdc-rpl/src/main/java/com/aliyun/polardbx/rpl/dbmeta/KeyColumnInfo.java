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
 * @author shicai.xsc 2020/12/7 11:02
 * @since 5.0.0.0
 */
@Data
public class KeyColumnInfo {

    private String table;
    private String keyName;
    private String columnName;
    private int nonUnique;
    private int seqInIndex;

    public KeyColumnInfo(String table, String keyName, String columnName, int nonUnique, int seqInIndex) {
        this.table = table;
        this.keyName = keyName;
        this.columnName = columnName;
        this.nonUnique = nonUnique;
        this.seqInIndex = seqInIndex;
    }
}
