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

package com.aliyun.polardbx.binlog.storage.memory;

import org.apache.commons.lang.text.StrBuilder;

import java.io.PrintWriter;

public class WatchObject {

    private long createTime;
    private String trace;

    public WatchObject() {
        this.createTime = System.currentTimeMillis();
        buildStackTrace();
    }

    private void buildStackTrace() {
        RuntimeException re = new RuntimeException();
        StrBuilder strBuilder = new StrBuilder();
        re.printStackTrace(new PrintWriter(strBuilder.asWriter()));
        this.trace = strBuilder.toString();
    }

    public long createTime() {
        return createTime;
    }

    public String trace() {
        return trace;
    }

}
