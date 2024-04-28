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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DBMSEvent represents DB event from the source DB, which Contains DBMS updates information that
 * must be replicated. We replicate 2 kinds of event - query log {@link DefaultQueryLog DefaultQueryLog} and
 * row change {@link DefaultRowChange DefaultRowChange} now.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSEvent implements Serializable {
    private static final long serialVersionUID = 6951115875657148365L;
    protected transient Map<String, DBMSOption> optionDict;
    // ms
    private long sourceTimeStamp;
    // ms
    private long extractTimeStamp;
    private long eventSize;
    private String position;
    private String rtso;

    /**
     * Build option information as needed.
     */
    protected void buildOptions(List<? extends DBMSOption> options) {
        HashMap<String, DBMSOption> optionDict = new HashMap<String, DBMSOption>(
            options.size(), 1.0f); // load factor 1.0 for fixed hash map
        for (DBMSOption option : options) {
            optionDict.put(option.getName(), option);
        }
        this.optionDict = optionDict;
    }

    /**
     * Put option information to dict.
     */
    public DBMSOption putOption(DBMSOption option) {
        if (optionDict == null) {
            buildOptions(getOptions());
        }
        return optionDict.put(option.getName(), option);
    }

    /**
     * Return the database update action.
     */
    public abstract DBMSAction getAction();

    /**
     * Return the schema of update event.
     */
    public abstract String getSchema();

    /**
     * Change the schema of update event.
     */
    public abstract void setSchema(String schema);

    /**
     * Returns the session options.
     *
     * @return session options.
     */
    public abstract List<? extends DBMSOption> getOptions();

    /**
     * Returns a session option.
     *
     * @param name The session option name.
     * @return session option.
     */
    public DBMSOption getOption(String name) {
        if (optionDict == null) {
            buildOptions(getOptions());
        }
        return optionDict.get(name);
    }

    /**
     * Returns a session option value.
     *
     * @param name The session option name.
     * @return session option value.
     */
    public Serializable getOptionValue(String name) {
        if (optionDict == null) {
            buildOptions(getOptions());
        }
        DBMSOption option = optionDict.get(name);
        return (option != null) ? option.getValue() : null;
    }

    /**
     * Change the session option.
     *
     * @param name The session option name.
     * @param value session option value.
     */
    public abstract void setOptionValue(String name, Serializable value);

    public long getSourceTimeStamp() {
        return sourceTimeStamp;
    }

    public void setSourceTimeStamp(long sourceTimeStamp) {
        this.sourceTimeStamp = sourceTimeStamp;
    }

    public long getExtractTimeStamp() {
        return extractTimeStamp;
    }

    public void setExtractTimeStamp(Long extractTimeStamp) {
        this.extractTimeStamp = extractTimeStamp;
    }

    public long getEventSize() {
        return eventSize;
    }

    public void setEventSize(long eventSize) {
        this.eventSize = eventSize;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPosition() {
        return position;
    }

    public String getRtso() {
        return rtso;
    }

    public void setRtso(String rtso) {
        this.rtso = rtso;
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("schema: ");
        builder.append(this.getSchema());
        builder.append(", action: ");
        builder.append(this.getAction());
        for (DBMSOption option : this.getOptions()) {
            builder.append(",\n    option: ");
            builder.append(option);
        }
        builder.append(')');
        return builder.toString();
    }
}
