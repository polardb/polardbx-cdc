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
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.MarkCommandEnum;
import com.aliyun.polardbx.binlog.MarkType;
import lombok.Data;

@Data
public class MarkInfo {
    private static final String SEP = "::";
    private MarkType markType;
    private String tso;
    private MarkCommandEnum command;
    private boolean valid = false;

    public MarkInfo(String queryLogInfo) {
        if (!queryLogInfo.startsWith(MarkType.CTS.name())) {
            return;
        }
        int f = queryLogInfo.indexOf(SEP);
        if (f < 0) {
            return;
        }
        markType = MarkType.valueOf(queryLogInfo.substring(0, f));
        int s = queryLogInfo.indexOf(SEP, f + 2);
        if (s > 0) {
            tso = queryLogInfo.substring(f + 2, s);
            command = MarkCommandEnum.valueOf(queryLogInfo.substring(s + 2));
        } else {
            tso = queryLogInfo.substring(f + 2);
        }
        valid = true;
    }

    public MarkInfo(MarkType markType, String tso) {
        this.markType = markType;
        this.tso = tso;
    }

    public MarkInfo(MarkType markType, String tso, MarkCommandEnum command) {
        this.markType = markType;
        this.tso = tso;
        this.command = command;
    }

    public String buildQueryLog() {
        String tso = markType + SEP + this.tso + SEP + command;
        return tso;
    }
}
