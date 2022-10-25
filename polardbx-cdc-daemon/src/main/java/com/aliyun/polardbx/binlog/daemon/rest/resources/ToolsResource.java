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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.XidRegion;
import com.aliyun.polardbx.binlog.daemon.rest.tools.OssSearchTools;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.HashSet;
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

}
