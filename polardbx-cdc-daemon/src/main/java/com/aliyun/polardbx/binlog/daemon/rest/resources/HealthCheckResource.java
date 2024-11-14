/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import com.sun.jersey.spi.resource.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_BID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_UID;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

@Path("/health")
@Singleton
public class HealthCheckResource {

    private static Logger logger = LoggerFactory.getLogger(HealthCheckResource.class);

    @GET
    @Path("/check")
    @Produces("text/plain;charset=utf-8")
    public String check() {
        Map<String, String> resultMap = Maps.newHashMap();
        resultMap.put("rdsapi", checkRdsOssDownload());
        return JSON.toJSONString(resultMap);
    }

    private String checkRdsOssDownload() {
        try {
            JdbcTemplate cnTemplate = getObject("polarxJdbcTemplate");
            List<Map<String, Object>> dataList = cnTemplate.queryForList("SHOW STORAGE limit 1");
            if (dataList.size() != 1) {
                throw new PolardbxException("storageInstId expect size 1 , but query meta db size " + dataList.size());
            }

            String storageInstId = (String) dataList.get(0).get("STORAGE_INST_ID");
            String uid = DynamicApplicationConfig.getString(RDS_UID);
            String bid = DynamicApplicationConfig.getString(RDS_BID);
            long end = System.currentTimeMillis();
            long begin = end - TimeUnit.DAYS.toMillis(10);
            DescribeBinlogFilesResult result = RdsApi
                .describeBinlogFiles(storageInstId, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                    RdsApi.formatUTCTZ(new Date(end)),
                    1000,
                    1);
            logger.info("rds oss download GetSize(" + result.getItems().size() + ")");
            logger.info(JSON.toJSONString(result));
            return "Success";
        } catch (Exception e) {
            logger.error("check rds oss download failed", e);
            return "failed(" + e.getMessage() + ")";
        }

    }
}
