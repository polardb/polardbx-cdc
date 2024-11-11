/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDownloader;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.daemon.rest.resources.response.BinlogListResponse;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.google.common.collect.Maps;
import com.sun.jersey.spi.resource.Singleton;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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

    @GET
    @Path("/download")
    public Map<String, String> download(@QueryParam("instanceId") String storageInstanceId,
                                        @QueryParam("distPath") String distPath) {
        Map<String, String> linkMap = Maps.newHashMap();
        HashMap<String, BinlogFile> ossBinlogFileMap = new HashMap<>();
        LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
        BinlogDownloader downloader = BinlogDownloader.getInstance();
        String rdsBinlogTestDir =
            DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR) + File.separator
                + "__test__";
        downloader.init(rdsBinlogTestDir, 3);
        downloader.start();

        long recallInterval = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RECALL_DAYS_LIMIT);

        String uid = DynamicApplicationConfig.getString(ConfigKeys.RDS_UID);
        String bid = DynamicApplicationConfig.getString(ConfigKeys.RDS_BID);

        long end = System.currentTimeMillis();
        // 搜索最近10天
        long begin = end - TimeUnit.DAYS.toMillis(recallInterval);
        int counts = 0;
        int pageNumber = 1;
        Long _preId = null;
        Set<Long> _ignoreHostIdSet = new HashSet<>();
        try {
            do {
                DescribeBinlogFilesResult result = RdsApi
                    .describeBinlogFiles(storageInstanceId, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                        RdsApi.formatUTCTZ(new Date(end)),
                        1000,
                        pageNumber++);
                List<BinlogFile> binlogFileList =
                    BinlogProcessor.process(result.getItems(), _ignoreHostIdSet, _preId, begin, null);
                for (BinlogFile binlogFile : binlogFileList) {
                    _ignoreHostIdSet.add(binlogFile.getInstanceID());
                    _preId = binlogFile.getInstanceID();
                    String binlogFileName = binlogFile.getLogname();
                    binlogFile.initRegionTime();
                    ossBinlogFileMap.put(binlogFileName, binlogFile);
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                            "add binlog ： " + binlogFileName + " [" + binlogFile.getLogBeginTime() + " , " + binlogFile
                                .getLogEndTime() + " ] ");
                    }

                    binlogFileQueue.add(binlogFile);
                }
                counts += result.getItemsNumbers();
                if (result.getTotalRecords() <= counts) {
                    break;
                }
            } while (true);

        } catch (Exception e) {
            throw new PositionNotFoundException(e);
        }
        Collections.sort(binlogFileQueue);
        logger.info("fetch binlog size : " + ossBinlogFileMap.size());
        for (BinlogFile bf : binlogFileQueue) {
            if (logger.isDebugEnabled()) {
                logger.debug("add download binlog : " + bf.getLogname());
            }
            try {
                BinlogDownloader.getInstance().addDownloadTask(storageInstanceId,
                    new DownloadTask(storageInstanceId, bf.getIntranetDownloadLink(),
                        distPath + File.separator + bf.getLogname()));
            } catch (ExecutionException e) {
                throw new PolardbxException(e);
            }
        }
        return linkMap;
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
        int totalCount = binlogOssRecordMapperExtend
            .count(begin, end, groupName);

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
