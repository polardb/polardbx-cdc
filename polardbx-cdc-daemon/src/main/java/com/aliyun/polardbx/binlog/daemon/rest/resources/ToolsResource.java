/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.XidRegion;
import com.aliyun.polardbx.binlog.daemon.rest.tools.OssSearchTools;
import com.aliyun.polardbx.binlog.transmit.relay.HashConfig;
import com.google.common.collect.Lists;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/tools")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
public class ToolsResource {

    @POST
    @Path("/searchXid")
    @Produces(MediaType.TEXT_PLAIN)
    public String regionSearchXid(XidRegion region) throws Exception {
        String storageInstanceId = region.getInstId();
        String xid = region.getXid();
        String beginTime = region.getBeginTime();
        log.info("request region : " + JSON.toJSONString(region));
        Set<Integer> integerSet = new HashSet<>();
        integerSet.add(LogEvent.QUERY_EVENT);
        Map<String, String> findMap = new HashMap<>();

        OssSearchTools tools = new OssSearchTools(storageInstanceId, beginTime);
        tools.doSearch(integerSet, (binlogFile, event) -> {
            String findXid = LogEventUtil.getXid(event);
            if (StringUtils.equals(xid, findXid)) {
                findMap.put(binlogFile.getLogname() + ":" + event.getLogPos(), ((QueryLogEvent) event).getQuery());
            }
        });

        String resultData = JSON.toJSONString(findMap);
        return resultData;

    }

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
