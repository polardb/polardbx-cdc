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
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.domain.TaskType;
import lombok.Data;

import java.util.List;

/**
 * @author yudong
 * @since 2023/5/4 15:25
 **/
@Data
public class StreamContext {
    private final String group;
    private final List<String> streamList;
    private final String clusterId;
    private final String taskName;
    private final TaskType taskType;
    private final long version;
}
