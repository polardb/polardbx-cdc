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

import java.io.Serializable;
import java.util.List;

/**
 * 事务开始
 *
 * @author agapple 2017年5月11日 下午6:46:22
 * @since 3.2.4
 */
public class DBMSTransactionEnd extends DBMSEvent {

    private static final long serialVersionUID = -5072768643669142562L;

    private Long transactionId;

    @Override
    public DBMSAction getAction() {
        return DBMSAction.OTHER;
    }

    @Override
    public String getSchema() {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public void setSchema(String schema) {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public List<? extends DBMSOption> getOptions() {
        throw new IllegalArgumentException("not support");
    }

    @Override
    public void setOptionValue(String name, Serializable value) {
        throw new IllegalArgumentException("not support");
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("xid: ");
        builder.append(transactionId);
        builder.append(')');
        return builder.toString();
    }

}
