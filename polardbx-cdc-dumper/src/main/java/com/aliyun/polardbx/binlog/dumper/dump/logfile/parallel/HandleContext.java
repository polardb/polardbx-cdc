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
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileGenerator;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * created by ziyang.lb
 **/
@Data
public class HandleContext {
    private LogFileGenerator logFileGenerator;
    private volatile PolardbxException exception;
    private volatile long latestSinkSequence;
    private AtomicBoolean running;
}
