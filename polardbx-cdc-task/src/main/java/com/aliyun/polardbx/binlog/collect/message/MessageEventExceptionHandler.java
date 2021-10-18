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

package com.aliyun.polardbx.binlog.collect.message;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.ExceptionHandler;

/**
 *
 **/
public class MessageEventExceptionHandler implements ExceptionHandler<Object> {

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event) {
        // 异常上抛，否则processEvents的逻辑会默认会mark为成功执行，有丢数据风险
        throw new PolardbxException(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex) {
    }

    @Override
    public void handleOnShutdownException(final Throwable ex) {
    }
}
