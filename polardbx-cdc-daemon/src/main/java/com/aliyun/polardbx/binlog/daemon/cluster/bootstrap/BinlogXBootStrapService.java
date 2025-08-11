/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.CnInstConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XStreamGroupMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStreamGroup;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.CnInstConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_AUTO_INIT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.getAllStorageInfo;
import static com.aliyun.polardbx.binlog.service.XStreamService.buildAndSaveXStream;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogXBootStrapService extends AbstractBinlogBootstrapService {

    @Override
    protected void beforeInitCommon() {
        log.info("Init Binlog-X-Stream Job!");
        if (!CnInstConfigUtil.getBoolean(ENABLE_CDC_META_BUILD_SNAPSHOT)) {
            throw new PolardbxException("cn version not support binlog x");
        }
    }

    @Override
    protected void beforeStartCommon() {
        tryInitStreamConfig();
    }

    @Override
    protected String clusterType() {
        return ClusterType.BINLOG_X.name();
    }

    private void tryInitStreamConfig() {
        boolean autoInit = getBoolean(BINLOGX_AUTO_INIT);
        if (autoInit) {
            XStreamGroupMapper xStreamGroupMapper = SpringContextHolder.getObject(XStreamGroupMapper.class);
            TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
            transactionTemplate.execute(t -> {
                int flag = 0;
                try {
                    String streamGroupName = getString(BINLOGX_STREAM_GROUP_NAME);
                    XStreamGroup xStreamGroup = new XStreamGroup();
                    xStreamGroup.setGroupName(streamGroupName);
                    xStreamGroup.setGroupDesc("Binlog-X " + streamGroupName);
                    flag = xStreamGroupMapper.insertSelective(xStreamGroup);
                } catch (DuplicateKeyException e) {
                    //do nothing
                }

                if (flag > 0) {
                    log.info("init stream group success!");
                    int streamCount = getInt(BINLOGX_STREAM_COUNT);
                    List<StorageInfo> storageInfoList = new ArrayList<>();
                    if (HashLevel.DATANODE == HashLevel.getCurrentHashLevel()) {
                        storageInfoList = getAllStorageInfo();
                        streamCount = storageInfoList.size();
                    }

                    for (int i = 0; i < streamCount; i++) {
                        buildAndSaveXStream(storageInfoList.isEmpty() ? null : storageInfoList.get(i), i, "");
                    }
                }
                return null;
            });

        }
    }
}
