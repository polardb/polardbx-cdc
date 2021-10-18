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

/**
 * @author shicai.xsc 2018/5/30 下午2:40
 * @since 5.0.0.0
 */
public class DBMSXATransaction {
    private String xid;

    private XATransactionType type;

    public DBMSXATransaction(String xid, XATransactionType type) {
        this.xid = xid;
        this.type = type;
    }

    public String getXid() {
        return xid;
    }

    public void setXid(String xid) {
        this.xid = xid;
    }

    public XATransactionType getType() {
        return type;
    }

    public void setType(XATransactionType type) {
        this.type = type;
    }
}

