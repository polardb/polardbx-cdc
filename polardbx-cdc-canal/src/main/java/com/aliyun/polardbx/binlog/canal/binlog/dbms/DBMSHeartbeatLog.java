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
import java.util.List;
import java.util.Objects;

/**
 * Defines a SQL statement from query-log that must be replicated.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DBMSHeartbeatLog extends DBMSEvent {
    private static final long serialVersionUID = -2075483176427758922L;

    protected DBMSAction action;

    private String logIdent;

    public DBMSHeartbeatLog(String logIdent) {
        this.logIdent = logIdent;
    }

    /**
     * Return the database update action.
     */
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
        return null;
    }

    @Override
    public void setOptionValue(String name, Serializable value) {

    }

    public String getLogIdent() {
        return logIdent;
    }

    public void setLogIdent(String logIdent) {
        this.logIdent = logIdent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DBMSHeartbeatLog that = (DBMSHeartbeatLog) o;
        return action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(action);
    }
}
