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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ServerConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.rest.resources.check.ClusterStatusChecker;
import com.aliyun.polardbx.binlog.dao.InstConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.InstConfigMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.po.InstConfig;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Path("/")
@Singleton
public class RootResource {
    private final static Gson GSON = new GsonBuilder().create();
    private static final Logger logger = LoggerFactory.getLogger(RootResource.class);

    @GET
    @Path("/")
    public String serverInfo() {
        logger.info("receive a request for root.");
        return "";
    }

    @GET
    @Path("/getCursors")
    public List<Map<String, Object>> getCursors() {
        logger.info("receive a request for getting cursors.");

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        List<NodeInfo> nodeInfoList = nodeInfoMapper.select(s -> s
            .where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        return nodeInfoList.stream()
            .map(n -> {
                Map<String, Object> map = new HashMap<>();
                Cursor cursor = StringUtils.isNotBlank(n.getLatestCursor()) ?
                    JSONObject.parseObject(n.getLatestCursor(), Cursor.class) : null;
                map.put("fileName", cursor != null ? cursor.getFileName() : "");
                map.put("filePosition", cursor != null ? cursor.getFilePosition() : "");
                map.put("containerId", n.getContainerId());
                return map;
            }).collect(Collectors.toList());
    }

    @GET
    @Path("/status")
    public String status() {
        logger.info("receive a request for status.");
        ClusterStatusChecker checker = new ClusterStatusChecker();
        return checker.check();
    }

    @GET
    @Path("/checkSupportBinlogX")
    public boolean checkSupportBinlogX() {
        // 先检查server是否支持
        InstConfigMapper instConfigMapper = SpringContextHolder.getObject(InstConfigMapper.class);
        Optional<InstConfig> instConfigOptional =
            instConfigMapper.selectOne(s -> s.where(InstConfigDynamicSqlSupport.paramKey, SqlBuilder
                .isEqualTo(ConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT)));
        return instConfigOptional.isPresent() && Boolean
            .parseBoolean(instConfigOptional.get().getParamVal());
    }

    /**
     * retVal.put("ServerId" , "");
     * retVal.put("CheckSumSwitch", "");
     * retVal.put("BinlogPersistTime", "");
     * retVal.put("BinlogSize", "");
     * retVal.put("VersionSupportMultiCdc", "");
     */
    @GET
    @Path("/basicInfo")
    public Map<String, String> queryBasicInfo() {
        Map<String, String> retVal = Maps.newHashMap();
        boolean supportBinlogX = checkSupportBinlogX();
        long serverID = ServerConfigUtil.getGlobalNumberVar("SERVER_ID");
        int binlogSize = DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_FILE_SIZE);
        retVal.put("ServerId", serverID + "");
        retVal.put("CheckSumSwitch", "ON");
        retVal.put("BinlogPersistTime",
            DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_BACKUP_FILE_EXPIRE_DAYS) + "");
        retVal.put("BinlogSize", binlogSize + "");
        retVal.put("VersionSupportMultiCdc", supportBinlogX + "");
        return retVal;
    }
}
