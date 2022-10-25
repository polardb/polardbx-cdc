/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.rpl.taskmeta;

import java.util.List;

import com.aliyun.polardbx.rpl.common.RplConstants;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/8 14:32
 * @since 5.0.0.0
 */
@Data
public class FullExtractorConfig extends ExtractorConfig {
    private int fetchBatchSize;
    private int parallelCount;

    public void mergeFrom(FullExtractorConfig other) {
        this.fetchBatchSize = other.fetchBatchSize;
        this.parallelCount = other.parallelCount;
    }
}
