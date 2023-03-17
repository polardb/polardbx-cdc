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

/**
 * This class creates a default SQL option implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultOption extends DBMSOption {

    private static final long serialVersionUID = -4741482473441751109L;

    protected String name;
    protected Serializable value;

    /**
     * Creates a new <code>DefaultOption</code> object
     */
    public DefaultOption(String name) {
        this.name = name;
    }

    /**
     * Creates a new <code>DefaultOption</code> object
     */
    public DefaultOption(String name, Serializable value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name value.
     *
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the option value.
     *
     * @return Returns the value.
     */
    public Serializable getValue() {
        return value;
    }

    /**
     * Change the option value.
     *
     * @param value - The updated value.
     */
    public void setValue(Serializable value) {
        this.value = value;
    }
}
