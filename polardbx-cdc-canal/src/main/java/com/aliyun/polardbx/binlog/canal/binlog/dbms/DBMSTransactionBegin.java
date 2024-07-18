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

/**
 * 事务开始
 *
 * @author agapple 2017年5月11日 下午6:46:22
 * @since 3.2.4
 */
public class DBMSTransactionBegin extends DBMSEvent {

    private static final long serialVersionUID = 1731391052965645136L;
    private Long threadId;

    private String tso;

    private boolean archive = false;

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

    public Long getThreadId() {
        return threadId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public String getTso() {
        return tso;
    }

    public void setTso(String tso) {
        this.tso = tso;
    }

    public boolean isArchive() {
        return archive;
    }

    public void setArchive(boolean archive) {
        this.archive = archive;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("threadId: ");
        builder.append(threadId);
        builder.append(')');
        return builder.toString();
    }

}
