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
package com.aliyun.polardbx.rpl.applier;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * @author shicai.xsc 2021/2/20 16:06
 * @since 5.0.0.0
 */
@Data
public class StatisticUnit {

    // private Map<String, Long> totalBatchConsumeMessageCount = new HashMap<>();
    private Map<String, Long> totalConsumeMessageCount = new HashMap<>();
    private Map<String, Long> avgMergeBatchSize = new HashMap<>();
    private Map<String, Long> applyRt = new HashMap<>();
    private Map<String, Long> messageTps = new HashMap<>();
    private Map<String, Long> applyTps = new HashMap<>();
    private long skipCounter = 0;
    private long skipExceptionCounter = 0;
    private long persistentMessageCounter = 0;
}
