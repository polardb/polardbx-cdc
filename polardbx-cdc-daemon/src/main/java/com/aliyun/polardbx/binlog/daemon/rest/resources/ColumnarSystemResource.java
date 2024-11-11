/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.columnar.version.ColumnarVersions;
import com.aliyun.polardbx.binlog.daemon.rest.ann.Leader;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Slf4j
@Path("/columnar/system")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class ColumnarSystemResource {
    @GET
    @Path("/getVersion")
    @Leader
    public String getVersion() {
        return ColumnarVersions.getVersionsByHttp();
    }
}
