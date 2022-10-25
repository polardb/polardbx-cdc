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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDownloader;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.download.BinlogProcessor;
import com.aliyun.polardbx.binlog.download.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.download.RdsApi;
import com.aliyun.polardbx.binlog.download.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import com.sun.jersey.spi.resource.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.File;
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

@Path("/backup")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class BinlogBackupResource {
    private static final Logger logger = LoggerFactory.getLogger(SystemControlResource.class);

    @POST
    @Path("/generateLinks")
    public Map<String, String> generateLink(List<String> fileNames) {
        Map<String, String> linkMap = Maps.newHashMap();

        try {
            for (String f : fileNames) {
                String link = RemoteBinlogProxy.getInstance().prepareDownLink(f,
                    DynamicApplicationConfig.getLong(ConfigKeys.BINLOG_DOWNLOAD_LINK_AVAILABLE_INTERVAL));
                linkMap.put(f, link);
            }
        } catch (Exception e) {
            logger.error("generate link failed! ", e);
            return Maps.newHashMap();
        }

        return linkMap;
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
            DynamicApplicationConfig.getString(ConfigKeys.TASK_RDSBINLOG_DOWNLOAD_DIR) + File.separator + "__test__";
        downloader.init(rdsBinlogTestDir, 3);
        downloader.start();

        long recallInterval = DynamicApplicationConfig.getInt(ConfigKeys.TASK_RDSBINLOG_DOWNLOAD_RECALLDAYS);

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
}
