/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.rest.entities.ConfigInfo;
import com.aliyun.polardbx.binlog.error.ConfigKeyNotExistException;
import com.sun.jersey.spi.resource.Singleton;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/config/v1")
@Singleton
public class DynamicConfigResource {

    @POST
    @Path("/set")
    @Produces(MediaType.TEXT_PLAIN)
    public String set(ConfigInfo configInfo) {
        DynamicApplicationConfig.setValue(configInfo.getKey(), configInfo.getValue());
        return true + "";
    }

    @GET
    @Path("/get/{key}")
    @Produces("text/plain;charset=utf-8")
    public String get(@PathParam("key") String key) {
        try {
            String result = DynamicApplicationConfig.getValue(key);
            return result == null ? "" : result;
        } catch (ConfigKeyNotExistException e) {
            return "";
        }
    }
}
