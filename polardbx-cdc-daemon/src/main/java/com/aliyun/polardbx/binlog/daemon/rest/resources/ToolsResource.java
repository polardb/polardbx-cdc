/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.transmit.relay.HashConfig;
import com.google.common.collect.Lists;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/tools")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
public class ToolsResource {
    @POST
    @Path("/getHashLevel")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getHashLevel(Map<String, String> parameter) {
        String db = parameter.get("db");
        String table = parameter.get("table");
        HashConfig.clearTableStreamMapping();
        return Lists.newArrayList(HashConfig.getHashLevel(db, table).name());
    }

    @POST
    @Path("/getHashStreamSeq")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getHashStreamSeq(Map<String, String> parameter) {
        String db = parameter.get("db");
        String table = parameter.get("table");
        HashConfig.clearTableStreamMapping();
        return Lists.newArrayList(String.valueOf(HashConfig.getStreamSeq(db, table, -1)));
    }
}
