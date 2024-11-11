/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.errors;

import com.aliyun.polardbx.binlog.daemon.rest.entities.ErrorMessage;
import com.sun.jersey.api.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class RestExceptionMapper implements ExceptionMapper<Exception> {
    private static final Logger log = LoggerFactory.getLogger(RestExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        log.debug("Uncaught errors in REST call: ", exception);

        if (exception instanceof RestException) {
            RestException restException = (RestException) exception;
            return Response.status(restException.statusCode())
                .entity(new ErrorMessage(restException.errorCode(), restException.getMessage()))
                .build();
        }

        if (exception instanceof NotFoundException) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorMessage(Response.Status.NOT_FOUND.getStatusCode(), exception.getMessage()))
                .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ErrorMessage(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), exception.getMessage()))
            .build();
    }
}
