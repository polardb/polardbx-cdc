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
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * created by ziyang.lb
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SingleEventToken extends EventToken {
    private String tso;
    private long tsoTimeSecond;
    private long nextPosition;
    private Type type;
    private long serverId;
    private long xid;
    private byte[] data;
    private String rowsQuery;
    private String cts;
    private boolean forceFlush;
    private int length;
    private boolean useTokenData;
    private int offset;

    public enum Type {
        /**
         *
         */
        BEGIN,
        /**
         *
         */
        COMMIT,
        /**
         *
         */
        ROWSQUERY,
        /**
         *
         */
        DML,
        /**
         *
         */
        TSO,
        /**
         *
         */
        HEARTBEAT
    }

    public void checkLength(int length) {
        if (this.length != length) {
            throw new PolardbxException(
                "invalide length value ,this value is " + this.length + " , that value is " + length);
        }
    }
}
