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
package com.aliyun.polardbx.binlog.canal.exception;

/**
 * @author chengjin.lyf on 2020/7/14 3:58 下午
 * @since 1.0.25
 */
public class TableIdNotFoundException extends Exception {

    public TableIdNotFoundException() {
        super();
    }

    public TableIdNotFoundException(String message) {
        super(message);
    }

    public TableIdNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public TableIdNotFoundException(Throwable cause) {
        super(cause);
    }

    protected TableIdNotFoundException(String message, Throwable cause, boolean enableSuppression,
                                       boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
