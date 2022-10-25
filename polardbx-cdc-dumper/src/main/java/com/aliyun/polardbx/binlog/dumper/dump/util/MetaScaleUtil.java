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
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_SCALE_REPAIR_STORAGE_ENABLE;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.instructionId;
import static com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport.tso;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.gmtCreated;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isLessThanOrEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class MetaScaleUtil {
    private static final Gson GSON = new GsonBuilder().create();

    public static void recordStorageHistory(String tsoParam, String instructionIdParam, List<String> storageList,
                                            Supplier<Boolean> runningSupplier)
        throws InterruptedException {
        if (storageList == null || storageList.isEmpty()) {
            throw new PolardbxException("Meta scale storage list can`t be null");
        }
        waitOriginTsoReady(runningSupplier);

        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
        transactionTemplate.execute((o) -> {
            // 幂等判断
            // tso记录到binlog文件和记录到binlog_storage_history表是非原子操作，需要做幂等判断
            // instructionId也是不能重复的，正常情况下tso和instructionId是一对一的关系，但可能出现bug
            // 比如：https://yuque.antfin-inc.com/jingwei3/knddog/uxpbzq，所以此处查询要更严谨一些，where条件也要包含instructionId
            List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(
                s -> s.where(tso, isEqualTo(tsoParam))
                    .or(instructionId, isEqualTo(instructionIdParam)));
            if (storageHistoryInfos.isEmpty()) {
                boolean repaired = tryRepairStorageList(instructionIdParam, storageList);
                StorageContent content = new StorageContent();
                content.setStorageInstIds(storageList);
                content.setRepaired(repaired);

                StorageHistoryInfo info = new StorageHistoryInfo();
                info.setStatus(0);
                info.setTso(tsoParam);
                info.setStorageContent(GSON.toJson(content));
                info.setInstructionId(instructionIdParam);
                storageHistoryMapper.insert(info);
                log.info("record storage history : " + GSON.toJson(info));
            } else {
                log.info("storage history with tso {} or instruction id {} is already exist, ignored.", tsoParam,
                    instructionIdParam);
            }
            return null;
        });
    }

    // 兼容一下之前的设计，之前没有StorageHistory机制
    // 虽然TopologyWatcher会为之前已经生成的TaskConfig进行"增补"操作，但和这里并没有同步机制，所以安全起见做一个判断，抛异常概率其实很小
    public static void waitOriginTsoReady(Supplier<Boolean> supplier) throws InterruptedException {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);

        long start = System.currentTimeMillis();
        while (true) {
            List<StorageHistoryInfo> origStorageHistory =
                storageHistoryMapper.select(s -> s.where(tso, isEqualTo(TaskConfig.ORIGIN_TSO)));

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

    private static boolean tryRepairStorageList(String instructionIdParam, List<String> storageList) {
        if (!needTryRepairStorage()) {
            log.info("no need to repair storage.");
            return false;
        }

        log.info("start try repairing storage.");
        BinlogPolarxCommandMapper commandMapper = SpringContextHolder.getObject(BinlogPolarxCommandMapper.class);
        StorageInfoMapper storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        Optional<BinlogPolarxCommand> optional = commandMapper
            .selectOne(s -> s.where(BinlogPolarxCommandDynamicSqlSupport.cmdId, isEqualTo(instructionIdParam)));

        if (optional.isPresent()) {
            BinlogPolarxCommand command = optional.get();
            if ("ADD_STORAGE".equals(command.getCmdType())) {
                log.info("storage list before trying to repair is " + storageList);
                List<StorageInfo> storageInfosInDb = storageInfoMapper.select(c ->
                    c.where(instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                        .and(status, isNotEqualTo(2))//0:storage ready, 1:prepare offline, 2:storage offline
                        .and(gmtCreated, isLessThanOrEqualTo(command.getGmtCreated()))
                        .orderBy(id));
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
        boolean enableRepair = DynamicApplicationConfig.getBoolean(TOPOLOGY_SCALE_REPAIR_STORAGE_ENABLE);
        if (enableRepair) {
            Set<String> needRepairVersions = Sets.newHashSet("5.4.9", "5.4.10", "5.4.11");
            JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
            String version = polarxTemplate.queryForObject("select version()", String.class);
            String[] versionArray = version.split("-");
            if (!StringUtils.equals("TDDL", versionArray[1])) {
                throw new PolardbxException("invalid polardbx version " + version);
            }
            String kernelVersion = versionArray[2];
            return needRepairVersions.contains(kernelVersion);
        }
        return false;
    }
}
