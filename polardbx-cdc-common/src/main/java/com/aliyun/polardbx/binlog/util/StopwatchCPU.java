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
package com.aliyun.polardbx.binlog.util;

/******************************************************************************
 *  Compilation:  javac StopwatchCPU.java
 *  Execution:    none
 *  Dependencies: none
 *
 *  A version of Stopwatch.java that measures CPU time on a single
 *  core or processor (instead of wall clock time).
 *
 ******************************************************************************/

import java.lang.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

/**
 * The {@code StopwatchCPU} data type is for measuring
 * the CPU time used during a programming task.
 * <p>
 * See {@link Stopwatch} for a version that measures wall-clock time
 * (the real time that elapses).
 *
 * @author Josh Hug
 * @author Robert Sedgewick
 * @author Kevin Wayne
 */

public class StopwatchCPU {
    private static final double NANOSECONDS_PER_SECOND = 1000000000;

    private final ThreadMXBean threadTimer;
    private final long start;

    /**
     * Initializes a new stopwatch.
     */
    public StopwatchCPU() {
        threadTimer = ManagementFactory.getThreadMXBean();
        start = threadTimer.getCurrentThreadCpuTime();
    }

    /**
     * Returns the elapsed CPU time (in seconds) since the stopwatch was created.
     *
     * @return elapsed CPU time (in seconds) since the stopwatch was created
     */
    public double elapsedTime() {
        long now = threadTimer.getCurrentThreadCpuTime();
        return (now - start) / NANOSECONDS_PER_SECOND;
    }
}
