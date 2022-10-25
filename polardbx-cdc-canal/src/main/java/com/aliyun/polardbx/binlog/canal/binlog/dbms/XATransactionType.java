/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

/**
 * @author shicai.xsc 2018/5/30 下午2:43
 * @since 5.0.0.0
 */
public enum XATransactionType {
    /**
     * 关于 MySQL XA 事务，参考：
     * https://dev.mysql.com/doc/refman/8.0/en/xa-statements.html
     */
    XA_START("XA START"),
    XA_END("XA END"),
    XA_COMMIT("XA COMMIT"),
    XA_ROLLBACK("XA ROLLBACK");

    private String name;

    XATransactionType(String typeName) {
        this.name = typeName;
    }

    public String getName() {
        return name;
    }
}
