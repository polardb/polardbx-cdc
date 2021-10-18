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

package com.aliyun.polardbx.binlog.canal.core.model;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSXATransaction;

/**
 * mysql对象对应的DBMSEvent
 *
 * @author agapple 2017年5月11日 下午5:32:59
 * @since 3.2.4
 */
public class MySQLDBMSEvent {

    private DBMSEvent dbMessage;
    private BinlogPosition position;
    private DBMSXATransaction xaTransaction;

    public MySQLDBMSEvent(DBMSEvent dbMessage, BinlogPosition position){
        this.dbMessage = dbMessage;
        this.position = position;
    }

    public DBMSEvent getDbMessage() {
        return dbMessage;
    }

    public void setDbMessage(DBMSEvent dbMessage) {
        this.dbMessage = dbMessage;
    }

    public BinlogPosition getPosition() {
        return position;
    }

    public void setPosition(BinlogPosition position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "MySQLDBMSEvent [position=" + position + ", dbMessage=" + dbMessage + "]";
    }

    public DBMSXATransaction getXaTransaction() {
        return xaTransaction;
    }

    public void setXaTransaction(DBMSXATransaction xaTransaction) {
        this.xaTransaction = xaTransaction;
    }
}
