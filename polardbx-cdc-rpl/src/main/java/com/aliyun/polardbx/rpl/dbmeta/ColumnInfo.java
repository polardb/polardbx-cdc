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
package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/11/29 21:19
 * @since 5.0.0.0
 */
@Data
public class ColumnInfo {

    private String name;
    // type value refers to
    // "Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/src.zip!/java/sql/Types.java"
    private int type;
    private String javaCharset;
    private boolean nullable;
    private boolean generated;
    private String typeName;
    private int size;

    public ColumnInfo(String name, int type, String javaCharset, boolean nullable, boolean generated, String typeName,
                      int size) {
        this.name = name;
        this.type = type;
        this.javaCharset = javaCharset;
        this.nullable = nullable;
        this.generated = generated;
        this.typeName = typeName;
        this.size = size;
    }
}
