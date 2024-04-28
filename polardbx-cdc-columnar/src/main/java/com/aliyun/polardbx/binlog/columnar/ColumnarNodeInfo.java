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
package com.aliyun.polardbx.binlog.columnar;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@EqualsAndHashCode
public class ColumnarNodeInfo {
    public static final ColumnarNodeInfo EMPTY = new ColumnarNodeInfo(null, null, null);

    private final String ip;
    private final String port;
    private final String name;

    public static ColumnarNodeInfo build(String ipPort, String leader) {
        Preconditions.checkNotNull(ipPort);
        final String[] split = ipPort.split(":");
        return new ColumnarNodeInfo(split[0], split[1], leader);
    }
}
