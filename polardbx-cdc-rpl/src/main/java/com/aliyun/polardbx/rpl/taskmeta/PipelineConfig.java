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
package com.aliyun.polardbx.rpl.taskmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/11/30 15:55
 * @since 5.0.0.0
 */
@Data
public class PipelineConfig {

    private int consumerParallelCount = 32;
    private int bufferSize = 4096;
    private boolean supportXa = false;
    private int fixedTpsLimit = -1;
    private boolean useIncValidation = false;
    private boolean skipException = false;
    private boolean safeMode = false;
    private PersistConfig persistConfig = new PersistConfig();
    private int applyRetryMaxTime = 5;
    private long retryIntervalMs = 1000;
}
