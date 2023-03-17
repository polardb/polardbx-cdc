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
package com.aliyun.polardbx.binlog.transmit.relay;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class LockingCleaner {
    private final ReentrantLock cleanLock;

    public LockingCleaner() {
        this.cleanLock = new ReentrantLock();
    }

    public void cleanWithCallback(CleanParameter parameter, Supplier<?> supplier) {
        cleanLock.lock();
        try {
            if (StringUtils.isBlank(parameter.maxReadTso)
                || parameter.cleaningTso.compareTo(parameter.maxReadTso) >= 0) {
                log.warn("skip to clean relay data , " + parameter);
                return;
            }
            supplier.get();
        } finally {
            cleanLock.unlock();
        }
    }

    public void checkWithCallback(CheckParameter parameter, Supplier<?> supplier) throws InvalidTsoException {
        cleanLock.lock();
        try {
            if (StringUtils.isNotBlank(parameter.maxCleanTso)
                && parameter.requestTso.compareTo(parameter.maxCleanTso) < 0) {
                throw new InvalidTsoException("request tso is less than max lean tso " + parameter);
            }
            supplier.get();
        } finally {
            cleanLock.unlock();
        }
    }

    @Data
    @AllArgsConstructor
    @ToString
    public static class CleanParameter {
        private String cleaningTso;
        private String maxReadTso;
    }

    @Data
    @AllArgsConstructor
    @ToString
    public static class CheckParameter {
        private String requestTso;
        private String maxCleanTso;
    }
}
