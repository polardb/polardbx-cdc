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
package com.aliyun.polardbx.binlog.api;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BinlogProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BinlogProcessor.class);

    public static boolean test = false;

    /**
     * 首先过滤掉ignoreHost的列表，接着优先选取preferHost， 如果preferHost在ignore中，则忽略，如果不存在，则选取列表中binlog begin最靠前，end最靠后的个数最多的。
     */
    public static List<BinlogFile> process(final List<BinlogFile> items, final Set<Long> ignoreHostSet,
                                           Long preferHostId, Long startTime, Long serverId) {
        logger.info("filter binlog event size : " + items.size() + " ignore host : " + JSON.toJSONString(ignoreHostSet)
            + " , prefHost : " + preferHostId + ", time: " + startTime + " , serverId : " + serverId);

        Map<Long, HostInsance> hostInstanceMap = filterAndPrepare(items, ignoreHostSet);

        logger.info("after filter instance map size : " + hostInstanceMap.size());

        HostInsance finalInstance = null;
        if (ignoreHostSet.contains(preferHostId)) {
            preferHostId = null;
        }

        if (preferHostId != null && preferHostId > 0) {
            finalInstance = hostInstanceMap.get(preferHostId);
        } else {

            if (serverId != null) {
                finalInstance = findByServerId(hostInstanceMap, serverId, startTime);
            }

            if (finalInstance == null) {
                logger.info("serverId not match extractorServerId , require serverId is " + serverId);
                finalInstance = findByBigestTimeRegion(hostInstanceMap, startTime);
            }

        }

        if (test) {
            finalInstance.sortList().stream().forEach(b -> {
                b.setIntranetDownloadLink(b.getDownloadLink());
            });
        }

        return finalInstance == null ? Collections.EMPTY_LIST : finalInstance.sortList();
    }

    private static HostInsance findByBigestTimeRegion(Map<Long, HostInsance> hostInstanceMap, Long startTime) {
        HostInsance finalInstance = null;
        for (HostInsance hostInsance : hostInstanceMap.values()) {
            logger.info("host begin : " + hostInsance.getBegin() + ", check time : " + startTime);
            if (startTime == null || startTime == -1 || hostInsance.getBegin() <= startTime) {
                if (finalInstance == null) {
                    finalInstance = hostInsance;
                }
                // 没有serverId的情况下，优先选取endTime最大的
                if (finalInstance.getEnd() < hostInsance.getEnd()) {
                    finalInstance = hostInsance;
                }
            }
        }
        return finalInstance;
    }

    private static HostInsance findByServerId(Map<Long, HostInsance> hostInstanceMap, Long serverId, Long startTime) {
        HostInsance finalInstance = null;
        for (HostInsance hostInsance : hostInstanceMap.values()) {
            if (startTime == null || startTime == -1 || hostInsance.getBegin() <= startTime) {
                while (CollectionUtils.isNotEmpty(hostInsance.getBinlogFiles())) {
                    try {
                        if (serverId.equals(extractServerId(hostInsance.getBinlogFiles().get(0)))) {
                            logger.info("detected server id match extractor server id : " + serverId);
                            finalInstance = hostInsance;
                            break;
                        }
                        break;
                    } catch (Exception e) {
                        if (e.getCause() instanceof FileNotFoundException) {
                            hostInsance.getBinlogFiles().remove(0);
                        } else {
                            throw e;
                        }
                    }
                }
            }
            if (finalInstance != null) {
                break;
            }
        }
        return finalInstance;
    }

    private static Long extractServerId(BinlogFile binlogFile) {
        String url = binlogFile.getIntranetDownloadLink();
        InputStream is = null;
        HttpURLConnection connection = null;
        Long serverId = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            is = connection.getInputStream();
            byte[] buf = new byte[20];
            is.read(buf);
            int position = 9;
            serverId = ((long) (0xff & buf[position++])) | ((long) (0xff & buf[position++]) << 8)
                | ((long) (0xff & buf[position++]) << 16) | ((long) (0xff & buf[position++]) << 24);
        } catch (Exception e) {
            throw new PolardbxException("connect to url failed!" + binlogFile, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        logger
            .info("extractor remove binlog instanceId  : " + binlogFile.getInstanceID() + ", server Id : " + serverId
                + " : " + binlogFile.getDownloadLink());
        return serverId;
    }

    private static Map<Long, HostInsance> filterAndPrepare(final List<BinlogFile> items,
                                                           final Set<Long> ignoreHostSet) {
        final Map<Long, HostInsance> hostInstanceMap = Maps.newHashMap();
        items.stream().filter(b -> {
            Long id = b.getInstanceID();
            if (ignoreHostSet.contains(id)) {
                return false;
            }
            return true;
        }).forEach(b -> {
            Long id = b.getInstanceID();
            HostInsance hostInsance = hostInstanceMap.get(id);
            if (hostInsance == null) {
                hostInsance = new HostInsance();
                hostInstanceMap.put(id, hostInsance);
            }
            try {
                hostInsance.addBinlog(b);
            } catch (ParseException e) {
                throw new PolardbxException(e);
            }
        });
        return hostInstanceMap;
    }
}
