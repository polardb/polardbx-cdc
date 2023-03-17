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
package com.aliyun.polardbx.binlog.daemon.rest.errors;

import com.aliyun.polardbx.binlog.error.PolardbxException;

import javax.ws.rs.core.Response;

public class RestException extends PolardbxException {
    private final int statusCode;
    private final int errorCode;

    public RestException(int statusCode, int errorCode, String message, Throwable t) {
        super(message, t);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public RestException(Response.Status status, int errorCode, String message, Throwable t) {
        this(status.getStatusCode(), errorCode, message, t);
    }

    public RestException(int statusCode, int errorCode, String message) {
        this(statusCode, errorCode, message, null);
    }

    public RestException(Response.Status status, int errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public RestException(int statusCode, String message, Throwable t) {
        this(statusCode, statusCode, message, t);
    }

    public RestException(Response.Status status, String message, Throwable t) {
        this(status, status.getStatusCode(), message, t);
    }

    public RestException(int statusCode, String message) {
        this(statusCode, statusCode, message, null);
    }

    public RestException(Response.Status status, String message) {
        this(status.getStatusCode(), status.getStatusCode(), message, null);
    }

    public int statusCode() {
        return statusCode;
    }

    public int errorCode() {
        return errorCode;
    }
}
