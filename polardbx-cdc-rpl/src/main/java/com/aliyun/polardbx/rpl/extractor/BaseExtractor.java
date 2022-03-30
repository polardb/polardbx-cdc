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

package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;

import lombok.Data;

/**
 * @author shicai.xsc 2021/2/18 21:41
 * @since 5.0.0.0
 */
@Data
public class BaseExtractor {

    protected BasePipeline pipeline;
    protected ExtractorConfig extractorConfig;
    protected boolean running;

    public BaseExtractor() {
    }

    public BaseExtractor(ExtractorConfig extractorConfig) {
        this.extractorConfig = extractorConfig;
    }

    public boolean init() throws Exception {
        return true;
    }

    public void start() throws Exception {
    }

    public void stop() {

    }

    public boolean isDone() {
        return false;
    }

    public boolean isRunning() {
        return running;
    }

}
