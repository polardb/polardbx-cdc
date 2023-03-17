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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FakeConnection extends OssConnection {

    public FakeConnection(String storageInstanceId, String uid, String bid, String firstSearchFile,
                          Long preferHostId, int recallInterval, Long serverId, Long requestTSO) {
        super(storageInstanceId, uid, bid, firstSearchFile, preferHostId, recallInterval, serverId, requestTSO);
    }

    @Override
    public void connect() throws IOException {
        if (!ossBinlogFileMap.isEmpty()) {
            return;
        }
        long end = System.currentTimeMillis();
        // 搜索最近10天
        long begin = end - TimeUnit.DAYS.toMillis(recallInterval);
        int counts = 0;
        int pageNumber = 1;
        Long _preId = preferHostId;
        Set<Long> _ignoreHostIdSet = new HashSet<>();
        try {
            List<BinlogFile> totalRecords = new ArrayList<>();
            do {
                DescribeBinlogFilesResult result = RdsApi
                    .describeBinlogFiles(storageInstanceId, uid, bid, RdsApi.formatUTCTZ(new Date(begin)),
                        RdsApi.formatUTCTZ(new Date(end)),
                        1000,
                        pageNumber++);
                logger.info(
                    "rds api result item size : " + result.getItems().size() + " request server id : " + serverId);
                totalRecords.addAll(result.getItems());
                counts += result.getItemsNumbers();
                if (result.getTotalRecords() <= counts) {
                    break;
                }
            } while (true);
            List<BinlogFile> binlogFileList =
                BinlogProcessor.process(totalRecords, ignoreHostIdSet, _preId, requestTSO, serverId);
            for (BinlogFile binlogFile : binlogFileList) {
                _ignoreHostIdSet.add(binlogFile.getInstanceID());
                binlogFile.initRegionTime();
                ossBinlogFileMap.put(binlogFile.getLogname(), binlogFile);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                        "add binlog ： " + binlogFile.getLogname() + " [" + binlogFile.getLogBeginTime() + " , "
                            + binlogFile.getLogEndTime() + " ] ");
                }

                binlogFileQueue.add(binlogFile);
            }
        } catch (Exception e) {
            throw new PositionNotFoundException(e);
        }
        ignoreHostIdSet = _ignoreHostIdSet;
        logger.info("fetch binlog size : " + ossBinlogFileMap.size());

    }
}
