/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.rest.resources.response.BinlogListResponse;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.google.common.collect.Maps;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/backup")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class BinlogBackupResource {
    private static final Logger logger = LoggerFactory.getLogger(SystemControlResource.class);

    @POST
    @Path("/generateLinks")
    public Map<String, String> generateLink(List<String> fileNames) {
        Map<String, String> linkMap = Maps.newHashMap();
        String clusterType = DynamicApplicationConfig.getClusterType();
        ClusterType clusterTypeEnum = ClusterType.valueOf(clusterType);
        if (ClusterType.BINLOG == clusterTypeEnum) {
            generateGlobalBinlog(fileNames, linkMap);
        } else if (ClusterType.BINLOG_X == clusterTypeEnum) {
            generateBinlogXBinlog(fileNames, linkMap);
        }

        return linkMap;
    }

    private void generateBinlogXBinlog(List<String> fileNames, Map<String, String> linkMap) {
        String groupName = DynamicApplicationConfig.getString(ConfigKeys.BINLOGX_STREAM_GROUP_NAME);
        try {
            for (String f : fileNames) {
                String subStreamBinlogName = f.substring(groupName.length() + 1);
                String streamName = StringUtils.substringBefore(subStreamBinlogName, "_binlog.");
                String partName = MessageFormat.format("{0}/{1}_{2}/", groupName, groupName, streamName);
                String link = RemoteBinlogProxy.getInstance().prepareDownLink(partName + f,
                    DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LINK_PRESERVE_SECONDS));
                linkMap.put(f, link);
            }
        } catch (Exception e) {
            logger.error("generate link failed! ", e);
        }
    }

    private void generateGlobalBinlog(List<String> fileNames, Map<String, String> linkMap) {
        try {
            for (String f : fileNames) {
                String link = RemoteBinlogProxy.getInstance().prepareDownLink(f,
                    DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LINK_PRESERVE_SECONDS));
                linkMap.put(f, link);
            }
        } catch (Exception e) {
            logger.error("generate link failed! ", e);
        }
    }

    @POST
    @Path("/binlog/list")
    public BinlogListResponse listBinlog(Map<String, String> params) {
        String begin = params.get("begin");
        String end = params.get("end");
        Integer countPerSize = Integer.valueOf(params.get("countPerSize"));
        Integer pageNum = Integer.valueOf(params.get("pageNum"));
        BinlogListResponse response = new BinlogListResponse();

        String groupName;
        if (DynamicApplicationConfig.getClusterType().equals(ClusterType.BINLOG.name())) {
            groupName = CommonConstants.GROUP_NAME_GLOBAL;
        } else {
            groupName = DynamicApplicationConfig.getString(ConfigKeys.BINLOGX_STREAM_GROUP_NAME);
        }

        BinlogOssRecordMapperExtend binlogOssRecordMapperExtend =
            SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class);
        int totalCount = binlogOssRecordMapperExtend.count(begin, end, groupName);

        final boolean backupOn = RemoteBinlogProxy.getInstance().isBackupOn();
        List<BinlogListResponse.BinlogInfo> binlogInfoList =
            binlogOssRecordMapperExtend.selectList(begin, end, groupName, (pageNum - 1) * countPerSize, countPerSize)
                .stream().map((r) -> {
                    BinlogListResponse.BinlogInfo bi = new BinlogListResponse.BinlogInfo();
                    bi.setBinlogFile(r.getBinlogFile());
                    bi.setGmtCreated(r.getGmtCreated());
                    bi.setGmtModified(r.getGmtModified());
                    bi.setId(r.getId().longValue());
                    bi.setLogBegin(r.getLogBegin());
                    bi.setLogEnd(r.getLogEnd());
                    bi.setLogSize(r.getLogSize());
                    bi.setPurgeStatus(r.getPurgeStatus());
                    bi.setUploadHost(r.getUploadHost());
                    bi.setUploadStatus(r.getUploadStatus());
                    if (backupOn) {
                        bi.setDownloadLink(RemoteBinlogProxy.getInstance().prepareDownLink(r.getBinlogFile(),
                            DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LINK_PRESERVE_SECONDS)));
                    }
                    return bi;
                }).collect(Collectors.toList());

        response.setBinlogInfoList(binlogInfoList);
        response.setTotalCount(totalCount);
        return response;
    }
}
