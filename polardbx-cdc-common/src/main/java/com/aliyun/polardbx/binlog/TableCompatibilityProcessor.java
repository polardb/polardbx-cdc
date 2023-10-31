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
package com.aliyun.polardbx.binlog;

import com.alibaba.druid.util.StringUtils;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * 元数据版本兼容性处理
 * <p>
 * created by ziyang.lb
 **/
public class TableCompatibilityProcessor {

    public static void process() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (StringUtils.equals(clusterType, ClusterType.BINLOG.name())) {
            processPhyDdlHistoryTable();
            processStorageHistoryTable();
            processScheduleHistoryTable();
            processBinlogOssRecordTable();
            processBinlogNodeInfoTable();
        } else if (StringUtils.equals(clusterType, ClusterType.BINLOG_X.name())) {
            processBinlogOssRecordTable();
            processBinlogNodeInfoTable();
        }
    }

    private static void processPhyDdlHistoryTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        BinlogPhyDdlHistoryMapper mapper = getObject(BinlogPhyDdlHistoryMapper.class);
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        template.execute(t -> {
            mapper.update(s -> s.set(BinlogPhyDdlHistoryDynamicSqlSupport.clusterId).equalTo(clusterId)
                .where(BinlogPhyDdlHistoryDynamicSqlSupport.clusterId, isEqualTo("0")));
            return null;
        });
    }

    private static void processStorageHistoryTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        template.execute(t -> {
            storageHistoryMapper.update(s -> s.set(StorageHistoryInfoDynamicSqlSupport.clusterId).equalTo(clusterId)
                .where(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo("0")));
            return null;
        });
    }

    private static void processScheduleHistoryTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        BinlogScheduleHistoryMapper historyMapper = getObject(BinlogScheduleHistoryMapper.class);
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        template.execute(t -> {
            historyMapper.update(s -> s.set(BinlogScheduleHistoryDynamicSqlSupport.clusterId).equalTo(clusterId)
                .where(BinlogScheduleHistoryDynamicSqlSupport.clusterId, isEqualTo("0")));
            return null;
        });
    }

    private static void processBinlogOssRecordTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        BinlogOssRecordMapper mapper = getObject(BinlogOssRecordMapper.class);
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);

        // 更新条件加group_id进行限定，防止单流多流集群共存时更新互相覆盖
        template.execute(t -> {
            mapper.update(s -> s.set(BinlogOssRecordDynamicSqlSupport.clusterId).equalTo(clusterId)
                .where(BinlogOssRecordDynamicSqlSupport.clusterId, isEqualTo("0"))
                .and(BinlogOssRecordDynamicSqlSupport.groupId, isEqualTo(CommonUtils.getGroupName())));
            return null;
        });
    }

    private static void processBinlogNodeInfoTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        NodeInfoMapper mapper = getObject(NodeInfoMapper.class);
        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        String polarxInstId = DynamicApplicationConfig.getString(POLARX_INST_ID);
        String ct = DynamicApplicationConfig.getClusterType();
        ClusterType clusterType = ClusterType.valueOf(ct);
        String groupName;
        if (clusterType == ClusterType.BINLOG_X) {
            groupName = DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME);
        } else {
            groupName = CommonConstants.GROUP_NAME_GLOBAL;
        }
        template.execute(t -> {
            mapper.update(s -> s.set(NodeInfoDynamicSqlSupport.polarxInstId).equalTo(polarxInstId)
                .set(NodeInfoDynamicSqlSupport.groupName).equalTo(groupName)
                .where(NodeInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
            return null;
        });
    }
}
