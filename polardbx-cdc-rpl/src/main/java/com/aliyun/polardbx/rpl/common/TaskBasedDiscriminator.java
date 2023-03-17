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
package com.aliyun.polardbx.rpl.common;

import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AbstractDiscriminator;
import ch.qos.logback.core.util.OptionHelper;

/**
 * @author shicai.xsc 2020/12/24 20:29
 * @since 5.0.0.0
 */
public class TaskBasedDiscriminator extends AbstractDiscriminator<ILoggingEvent> {
    private String key;
    private String defaultValue;
    private static Map<String, String> values = new HashMap<>();

    public TaskBasedDiscriminator() {
    }

    @Override
    public String getDiscriminatingValue(ILoggingEvent event) {
        return values.containsKey(key) ? values.get(key) : defaultValue;
    }

    @Override
    public void start() {
        int errors = 0;
        if (OptionHelper.isEmpty(this.key)) {
            ++errors;
            this.addError("The \"Key\" property must be set");
        }

        if (OptionHelper.isEmpty(this.defaultValue)) {
            ++errors;
            this.addError("The \"DefaultValue\" property must be set");
        }

        if (errors == 0) {
            this.started = true;
        }

    }

    public static void put(String key, String value) {
        values.put(key, value);
    }

    @Override
    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDefaultValue() {
        return this.defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}


