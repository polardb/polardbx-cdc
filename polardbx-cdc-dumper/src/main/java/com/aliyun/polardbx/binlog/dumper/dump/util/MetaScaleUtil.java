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
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.EXPECTED_STORAGE_TSO_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_REPAIR_STORAGE_WITH_SCALE_ENABLE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.util.StorageUtil.buildExpectedStorageTso;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MetaScaleUtil {
    public static void recordStorageHistory(String tsoParam, String instructionIdParam, List<String> storageList,
                                            String streamNameParam, Supplier<Boolean> runningSupplier)
        throws InterruptedException {
        if (storageList == null || storageList.isEmpty()) {
            throw new PolardbxException("Meta scale storage list can`t be null");
        }
        waitOriginTsoReady(runningSupplier);

        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);
        StorageHistoryDetailInfoMapper historyDetailMapper = getObject(StorageHistoryDetailInfoMapper.class);
        TransactionTemplate transactionTemplate = getObject("metaTransactionTemplate");
        transactionTemplate.execute((o) -> {
            // 幂等判断
            // tso记录到binlog文件和记录到binlog_storage_history表是非原子操作，需要做幂等判断
            // instructionId也是不能重复的，正常情况下tso和instructionId是一对一的关系，但可能出现bug
            // 比如：https://yuque.antfin-inc.com/jingwei3/knddog/uxpbzq
            List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(
                s -> s.where(StorageHistoryInfoDynamicSqlSupport.tso, isEqualTo(tsoParam))
                    .and(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));
            if (storageHistoryInfos.isEmpty()) {
                StorageHistoryInfo info = null;
                try {
                    boolean repaired = tryRepairStorageList(instructionIdParam, storageList);
                    StorageContent content = new StorageContent();
                    content.setStorageInstIds(storageList);
                    content.setRepaired(repaired);

                    info = new StorageHistoryInfo();
                    info.setStatus(-1);
                    info.setTso(tsoParam);
                    info.setStorageContent(JSONObject.toJSONString(content));
                    info.setInstructionId(instructionIdParam);
                    info.setClusterId(getString(CLUSTER_ID));
                    info.setGroupName(buildStreamGroupId());
                    storageHistoryMapper.insert(info);
                    log.info("record storage history : " + JSONObject.toJSONString(info));
                } catch (DuplicateKeyException e) {
                    log.warn("storage history is already existing , {}.", JSONObject.toJSONString(info));
                }
            } else {
                log.info("storage history with tso {} or instruction id {} is already exist, ignored.", tsoParam,
                    instructionIdParam);
            }

            // 多流特殊逻辑
            if (isBinlogXStream(streamNameParam)) {
                List<StorageHistoryDetailInfo> detailInfos = historyDetailMapper.select(
                    s -> s.where(StorageHistoryDetailInfoDynamicSqlSupport.tso, isEqualTo(tsoParam))
                        .and(StorageHistoryDetailInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID)))
                        .and(StorageHistoryDetailInfoDynamicSqlSupport.streamName, isEqualTo(streamNameParam)));

                if (detailInfos.isEmpty()) {
                    try {
                        StorageHistoryDetailInfo detailInfo = new StorageHistoryDetailInfo();
                        detailInfo.setStatus(-1);
                        detailInfo.setClusterId(getString(CLUSTER_ID));
                        detailInfo.setTso(tsoParam);
                        detailInfo.setStreamName(streamNameParam);
                        detailInfo.setInstructionId(instructionIdParam);
                        historyDetailMapper.insert(detailInfo);
                    } catch (DuplicateKeyException e) {
                        log.warn("storage history detail info is already existing, tso {}, stream name {}.",
                            tsoParam, streamNameParam);
                    }
                } else {
                    log.info("storage history detail info with tso {} and stream name {} is already exist, ignored.",
                        tsoParam, streamNameParam);
                }
            }
            return null;
        });
    }

    public static void tryCommitStorageHistory(String tsoParam, String streamNameParam) {
        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);
        StorageHistoryDetailInfoMapper historyDetailMapper = getObject(StorageHistoryDetailInfoMapper.class);
        TransactionTemplate transactionTemplate = getObject("metaTransactionTemplate");

        transactionTemplate.execute((o) -> {
            // 对binlog_storage_history_info主表的status列进行更新
            List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(
                s -> s.where(StorageHistoryInfoDynamicSqlSupport.tso, isEqualTo(tsoParam))
                    .and(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));
            if (!storageHistoryInfos.isEmpty()) {
                storageHistoryInfos.forEach(i -> {
                    storageHistoryMapper.update(
                        u -> u.set(StorageHistoryInfoDynamicSqlSupport.status).equalTo(0)
                            .where(StorageHistoryInfoDynamicSqlSupport.id, isEqualTo(i.getId())));
                    log.info("commit storage history : " + JSONObject.toJSONString(i));
                });
            }

            // 多流特殊逻辑
            if (isBinlogXStream(streamNameParam)) {
                List<StorageHistoryDetailInfo> detailInfos = historyDetailMapper.select(
                    s -> s.where(StorageHistoryDetailInfoDynamicSqlSupport.tso, isEqualTo(tsoParam))
                        .and(StorageHistoryDetailInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID)))
                        .and(StorageHistoryDetailInfoDynamicSqlSupport.streamName, isEqualTo(streamNameParam)));

                if (!detailInfos.isEmpty()) {
                    detailInfos.forEach(i -> {
                        historyDetailMapper.update(
                            u -> u.set(StorageHistoryDetailInfoDynamicSqlSupport.status).equalTo(0)
                                .where(StorageHistoryDetailInfoDynamicSqlSupport.id, isEqualTo(i.getId())));
                        log.info("commit storage history detail : " + JSONObject.toJSONString(i));
                    });
                }
            }
            return null;
        });
    }

    // 兼容一下之前的设计，之前没有StorageHistory机制
    // 虽然TopologyWatcher会为之前已经生成的TaskConfig进行"增补"操作，但和这里并没有同步机制，所以安全起见做一个判断，抛异常概率其实很小
    public static void waitOriginTsoReady(Supplier<Boolean> supplier) throws InterruptedException {
        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);

        long start = System.currentTimeMillis();
        while (true) {
            List<StorageHistoryInfo> origStorageHistory = storageHistoryMapper
                .select(s -> s.where(StorageHistoryInfoDynamicSqlSupport.tso, isEqualTo(ExecutionConfig.ORIGIN_TSO))
                    .and(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));

            if (origStorageHistory.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                if ((System.currentTimeMillis() - start) > 10 * 1000) {
                    throw new PolardbxException("storage history with original tso is not ready.");
                }
                if (!supplier.get()) {
                    throw new InterruptedException("wait interrupt");
                }
            } else {
                break;
            }
        }
    }

    public static void waitTaskConfigReady(String startTso, String streamName, Supplier<Boolean> runningStatusSupplier)
        throws InterruptedException {
        //尝试进行commit补偿
        tryCommitStorageHistory(startTso, streamName);
        waitOriginTsoReady(runningStatusSupplier);
        String expectedStorageTso = isBinlogXStream(streamName) ? buildExpectedStorageTso(startTso, streamName) :
            buildExpectedStorageTso(startTso);
        log.info("expected storage tso is : " + expectedStorageTso);
        updateExpectedStorageTso(streamName, expectedStorageTso);

        BinlogTaskConfigMapper taskConfigMapper = getObject(BinlogTaskConfigMapper.class);
        long start = System.currentTimeMillis();
        while (true) {
            List<BinlogTaskConfig> taskConfigs = taskConfigMapper.select(
                    t -> t.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(getString(ConfigKeys.CLUSTER_ID)))
                        .and(BinlogTaskConfigDynamicSqlSupport.role, isIn(
                            TaskType.Final.name(), TaskType.Relay.name(), TaskType.Dispatcher.name())))
                .stream().filter(tc -> {
                    ExecutionConfig config = JSONObject.parseObject(tc.getConfig(), ExecutionConfig.class);
                    return !expectedStorageTso.equals(config.getTso());
                }).collect(Collectors.toList());

            if (!taskConfigs.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                if ((System.currentTimeMillis() - start) >= 10 * 1000) {
                    throw new PolardbxException(
                        "task config for current storage tso is not ready :" + expectedStorageTso);
                }
                if (!runningStatusSupplier.get()) {
                    throw new InterruptedException("wait interrupt");
                }
            } else {
                break;
            }
        }
    }

    public static boolean isMetaScaleTso(String tso) {
        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(
            s -> s.where(StorageHistoryInfoDynamicSqlSupport.tso, isEqualTo(tso))
                .and(StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));
        return !storageHistoryInfos.isEmpty();
    }

    public static void waitUploadComplete(String fileName, String streamName) {
        BinlogOssRecordMapper mapper = getObject(BinlogOssRecordMapper.class);
        while (true) {
            List<BinlogOssRecord> list = mapper.select(s -> s.where(BinlogOssRecordDynamicSqlSupport.uploadStatus,
                    isIn(BinlogUploadStatus.SUCCESS.getValue(), BinlogUploadStatus.IGNORE.getValue()))
                .and(BinlogOssRecordDynamicSqlSupport.binlogFile, isLessThanOrEqualTo(fileName))
                .and(BinlogOssRecordDynamicSqlSupport.streamId, isEqualTo(streamName))
                .and(BinlogOssRecordDynamicSqlSupport.clusterId, isEqualTo(getString(CLUSTER_ID))));
            if (!list.isEmpty()) {
                break;
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private static void updateExpectedStorageTso(String streamNameInput, String expectedStorageTsoInput) {
        if (isBinlogXStream(streamNameInput)) {
            XStreamMapper mapper = SpringContextHolder.getObject(XStreamMapper.class);
            mapper.update(s ->
                s.set(XStreamDynamicSqlSupport.expectedStorageTso).equalTo(expectedStorageTsoInput)
                    .where(XStreamDynamicSqlSupport.streamName, isEqualTo(streamNameInput)));
        } else {
            SystemDbConfig.upsertSystemDbConfig(EXPECTED_STORAGE_TSO_KEY, expectedStorageTsoInput);
        }
    }

    private static boolean tryRepairStorageList(String instructionIdParam, List<String> storageList) {
        if (!needTryRepairStorage()) {
            log.info("no need to repair storage.");
            return false;
        }

        log.info("start try repairing storage.");
        BinlogPolarxCommandMapper commandMapper = getObject(BinlogPolarxCommandMapper.class);
        StorageInfoMapper storageInfoMapper = getObject(StorageInfoMapper.class);
        Optional<BinlogPolarxCommand> optional = commandMapper
            .selectOne(s -> s.where(BinlogPolarxCommandDynamicSqlSupport.cmdId, isEqualTo(instructionIdParam)));

        if (optional.isPresent()) {
            BinlogPolarxCommand command = optional.get();
            if ("ADD_STORAGE".equals(command.getCmdType())) {
                log.info("storage list before trying to repair is " + storageList);
                List<StorageInfo> storageInfosInDb = storageInfoMapper.select(c ->
                    c.where(StorageInfoDynamicSqlSupport.instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                        //0:storage ready, 1:prepare offline, 2:storage offline
                        .and(StorageInfoDynamicSqlSupport.status, isNotEqualTo(2))
                        .and(StorageInfoDynamicSqlSupport.gmtCreated, isLessThanOrEqualTo(command.getGmtCreated()))
                        .orderBy(StorageInfoDynamicSqlSupport.id));
                Set<String> storageIdsInDb = storageInfosInDb.stream().collect(
                        Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1, (s1, s2) -> s1)).values().stream()
                    .map(StorageInfo::getStorageInstId)
                    .collect(Collectors.toSet());

                boolean repaired = false;
                for (String id : storageIdsInDb) {
                    if (!storageList.contains(id)) {
                        log.warn("storage inst id {} is not exist in instruction storage list.", id);
                        storageList.add(id);
                        repaired = true;
                    }
                }
                log.info("storage list after trying to repair is " + storageList);
                return repaired;
            }
        } else {
            log.error("can`t find the polarx command record for instruction-id " + instructionIdParam);
        }
        return false;
    }

    private static boolean needTryRepairStorage() {
        boolean enableRepair = DynamicApplicationConfig.getBoolean(TOPOLOGY_REPAIR_STORAGE_WITH_SCALE_ENABLE);
        if (enableRepair) {
            Set<String> needRepairVersions = Sets.newHashSet("5.4.9", "5.4.10", "5.4.11");
            JdbcTemplate polarxTemplate = getObject("polarxJdbcTemplate");
            String version = polarxTemplate.queryForObject("select version()", String.class);
            String[] versionArray = version.split("-");
            if (versionArray.length > 2 && StringUtils.equals("TDDL", versionArray[1])) {
                String kernelVersion = versionArray[2];
                return needRepairVersions.contains(kernelVersion);
            }
        }
        return false;
    }

    private static String buildStreamGroupId() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (StringUtils.equals(ClusterType.BINLOG.name(), clusterType)) {
            return GROUP_NAME_GLOBAL;
        } else {
            return getString(BINLOGX_STREAM_GROUP_NAME);
        }
    }

    public static boolean isBinlogXStream(String streamName) {
        return StringUtils.isNotBlank(streamName) && !StringUtils.equals(STREAM_NAME_GLOBAL, streamName);
    }
}
