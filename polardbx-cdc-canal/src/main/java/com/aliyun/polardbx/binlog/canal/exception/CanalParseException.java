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

package com.aliyun.polardbx.binlog.canal.exception;

import com.aliyun.polardbx.binlog.error.PolardbxException;

/**
 * @author chengjin.lyf on 2020/7/14 3:39 下午
 * @since 1.0.25
 */
public class CanalParseException extends PolardbxException {

    public CanalParseException(String message) {
        super(message);
    }

    public CanalParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CanalParseException(Throwable cause) {
        super(cause);
    }

    public CanalParseException() {
        super();
    }
}
