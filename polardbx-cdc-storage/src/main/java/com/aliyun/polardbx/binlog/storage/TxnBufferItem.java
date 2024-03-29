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
package com.aliyun.polardbx.binlog.storage;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
@Data
@Builder
public class TxnBufferItem {
    private String traceId;
    private String rowsQuery;
    private int eventType;
    private byte[] payload;
    private String schema;
    private String table;
    private int hashKey;
    private List<byte[]> primaryKey;

    //可选项
    private String binlogFile;
    private long binlogPosition;
    private String originTraceId;

    public int size() {
        if (payload != null) {
            return payload.length;
        }

        throw new IllegalStateException("payload is null");
    }
}
