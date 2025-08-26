/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BinlogProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BinlogProcessor.class);

    /**
     * 首先过滤掉ignoreHost的列表，接着优先选取preferHost， 如果preferHost在ignore中，则忽略，如果不存在，则选取列表中binlog begin最靠前，end最靠后的个数最多的。
     */
    public static List<BinlogFile> process(final List<BinlogFile> items, final Set<Long> ignoreHostSet,
                                           Long preferHostId, Long startTime, Long serverId) {
        logger.info("before filter binlog file count : {} ignore host : {} , prefHost : {}, time: {} , serverId : {}",
            items.size(), JSON.toJSONString(ignoreHostSet), preferHostId, startTime, serverId);

        Map<Long, HostInstance> hostInstanceMap = filterAndPrepare(items, ignoreHostSet);

        logger.info("after filter instance map size : " + hostInstanceMap.size());

        HostInstance finalInstance = null;
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
                logger.info("serverId not match direct dn serverId , direct dn serverId is {}", serverId);
                finalInstance = findByBiggestTimeRegion(hostInstanceMap, startTime);
            }

        }

        return finalInstance == null ? Collections.EMPTY_LIST : finalInstance.sortList();
    }

    private static HostInstance findByBiggestTimeRegion(Map<Long, HostInstance> hostInstanceMap, Long startTime) {
        HostInstance finalInstance = null;
        for (HostInstance hostInstance : hostInstanceMap.values()) {
            logger.info("host begin : {}, check time : {}", hostInstance.getBegin(), startTime);
            if (startTime == null || startTime == -1 || hostInstance.getBegin() <= startTime) {
                if (finalInstance == null) {
                    finalInstance = hostInstance;
                }
                // 没有serverId的情况下，优先选取endTime最大的
                if (finalInstance.getEnd() < hostInstance.getEnd()) {
                    finalInstance = hostInstance;
                }
            }
        }
        return finalInstance;
    }

    private static HostInstance findByServerId(Map<Long, HostInstance> hostInstanceMap, Long serverId, Long startTime) {
        HostInstance finalInstance = null;
        for (HostInstance hostInstance : hostInstanceMap.values()) {
            if (startTime == null || startTime == -1 || hostInstance.getBegin() <= startTime) {
                while (CollectionUtils.isNotEmpty(hostInstance.getBinlogFiles())) {
                    try {
                        hostInstance.prepareServerId();
                        if (serverId.equals(hostInstance.getServerId())) {
                            logger.info("detected server id match extractor server id : {}", serverId);
                            finalInstance = hostInstance;
                            break;
                        }
                        break;
                    } catch (Exception e) {
                        if (e.getCause() instanceof FileNotFoundException) {
                            hostInstance.getBinlogFiles().remove(0);
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

    private static Map<Long, HostInstance> filterAndPrepare(final List<BinlogFile> items,
                                                            final Set<Long> ignoreHostSet) {
        final Map<Long, HostInstance> hostInstanceMap = Maps.newHashMap();
        items.stream().filter(b -> {
            Long id = b.getInstanceID();
            if (ignoreHostSet.contains(id)) {
                return false;
            }
            return true;
        }).forEach(b -> {
            Long id = b.getInstanceID();
            HostInstance hostInstance = hostInstanceMap.get(id);
            if (hostInstance == null) {
                hostInstance = new HostInstance();
                hostInstanceMap.put(id, hostInstance);
            }
            try {
                hostInstance.addBinlog(b);
            } catch (ParseException e) {
                throw new PolardbxException(e);
            }
        });
        return hostInstanceMap;
    }
}
