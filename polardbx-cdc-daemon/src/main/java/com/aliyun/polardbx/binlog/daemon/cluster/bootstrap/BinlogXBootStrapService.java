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
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.CnInstConfigUtil;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.XStreamGroupMapper;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.domain.po.XStreamGroup;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import static com.aliyun.polardbx.binlog.CnInstConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_AUTO_INIT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;

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
        boolean autoInit = DynamicApplicationConfig.getBoolean(BINLOGX_AUTO_INIT);
        int streamCount = DynamicApplicationConfig.getInt(BINLOGX_STREAM_COUNT);
        if (autoInit) {
            XStreamGroupMapper xStreamGroupMapper = SpringContextHolder.getObject(XStreamGroupMapper.class);
            XStreamMapper xStreamMapper = SpringContextHolder.getObject(XStreamMapper.class);
            String streamGroupName = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);

            try {
                XStreamGroup xStreamGroup = new XStreamGroup();
                xStreamGroup.setGroupName(streamGroupName);
                xStreamGroup.setGroupDesc("Binlog-X " + streamGroupName);
                xStreamGroupMapper.insertSelective(xStreamGroup);
            } catch (DuplicateKeyException e) {
                //do nothing
            }

            TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
            transactionTemplate.execute(t -> {
                for (int i = 0; i < streamCount; i++) {
                    try {
                        String streamName = streamGroupName + "_stream_" + i;
                        XStream xStream = new XStream();
                        xStream.setGroupName(streamGroupName);
                        xStream.setStreamName(streamName);
                        xStream.setExpectedStorageTso("");
                        xStream.setStreamDesc("Binlog-X " + streamName);
                        xStreamMapper.insertSelective(xStream);
                    } catch (DuplicateKeyException e) {
                        //do nothing
                    }
                }
                return null;
            });

        }
    }
}
