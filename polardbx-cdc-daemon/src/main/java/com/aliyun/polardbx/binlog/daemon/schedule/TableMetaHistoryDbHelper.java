/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.PolarxCommandType;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author yanfenglin
 */
@Component
@Slf4j
public class TableMetaHistoryDbHelper {

    @Resource
    private BinlogLogicMetaHistoryMapper logicMetaHistoryMapper;

    @Resource
    private BinlogLogicMetaHistoryMapperExtend logicMetaHistoryMapperExt;

    @Resource
    private BinlogPhyDdlHistoryMapper phyDdlHistoryMapper;

    @Resource
    private BinlogPolarxCommandMapper polarxCommandMapper;

    @Resource
    private BinlogOssRecordMapperExtend binlogOssRecordMapperExtend;

    private int buildMetaSnapshotRetryTimes = 0;

    @Transactional(rollbackFor = Throwable.class)
    public void tryClean() {
        if (log.isDebugEnabled()) {
            log.debug("begin to manage logic table meta data");
        }
        String tso = processLogicMeta();
        tryCleanPhyDDL(tso);
        if (log.isDebugEnabled()) {
            log.debug("success manage logic table meta data");
        }
    }

    /**
     * 统一清理LogicDDL
     * 需要判断是否所有 系统不会丢失元数据
     */
    private String cleanLogicHistory() {
        if (log.isDebugEnabled()) {
            log.debug("begin to clean logic history");
        }
        // 查找2个
        List<String> lastest2SnapshotTsoList = logicMetaHistoryMapperExt.getLatest2SnapshotTso();

        if (!CollectionUtils.isEmpty(lastest2SnapshotTsoList) && lastest2SnapshotTsoList.size() == 2) {
            // 倒数第二个tso
            String secondlyRecentTso = lastest2SnapshotTsoList.get(1);

            List<BinlogOssRecord> binlogOssRecordList = binlogOssRecordMapperExtend.selectMaxTso();
            String maxTSO = null;
            for (BinlogOssRecord record : binlogOssRecordList) {
                if (record == null || StringUtils.isBlank(record.getLastTso())) {
                    // 可能是某个集群reset了,不能清理
                    return null;
                } else {
                    maxTSO = CommonUtils.min(maxTSO, record.getLastTso());
                }
            }
            if (StringUtils.isBlank(maxTSO)) {
                // 没有binlog,不能清理
                return null;
            }
            if (secondlyRecentTso.compareTo(maxTSO) > 0) {
                // secondly recent snap tso > maxTso  ，不能清理，保障最近一个文件有snap可用
                return null;
            }
            int deleteCount = 0;
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED)) {
                deleteCount = logicMetaHistoryMapperExt.softClean(secondlyRecentTso);
            } else {
                deleteCount = logicMetaHistoryMapper.delete(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.tso,
                    SqlBuilder.isLessThan(secondlyRecentTso)));
            }
            log.warn("clean logic meta rows count " + deleteCount);
            return secondlyRecentTso;
        }
        return null;
    }

    private String processLogicMeta() {
        String tso = cleanLogicHistory();
        trySetRebuildTableMetaSnapFlag();
        return tso;
    }

    private void tryCleanPhyDDL(String tso) {
        if (StringUtils.isNotBlank(tso)) {
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED)) {
                log.warn("skip delete phy ddl count for tso {}, because soft delete is enabled.", tso);
            } else {
                // build snap 时会保障发生时， 不会有ddl正在运行或者将要运行， 这里可以安全的清理
                int deleteCount = phyDdlHistoryMapper
                    .delete(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThan(tso)));
                log.info("delete phy ddl count : " + deleteCount);
            }
        }
    }

    private long getRegionDDLCount() {
        String latestSnapshotTso = logicMetaHistoryMapperExt.getLatestSnapshotTso();
        long phyCount;
        //只计算大于最近一次snap后产生的ddl个数
        if (StringUtils.isNotBlank(latestSnapshotTso)) {
            phyCount = phyDdlHistoryMapper.count(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso,
                SqlBuilder.isGreaterThan(latestSnapshotTso)));
        } else {
            phyCount = phyDdlHistoryMapper.count(s -> s);
        }
        return phyCount;
    }

    private boolean testLastCommandFinish() {
        Optional<BinlogPolarxCommand> commandOptional = polarxCommandMapper
            .selectOne(s -> s.where(BinlogPolarxCommandDynamicSqlSupport.cmdType, SqlBuilder.isEqualTo(
                    PolarxCommandType.BUILD_META_SNAPSHOT.name()))
                .orderBy(BinlogPolarxCommandDynamicSqlSupport.gmtCreated.descending()).limit(1));
        if (commandOptional.isPresent()) {
            BinlogPolarxCommand command = commandOptional.get();
            if (command.getCmdStatus() == 0) {
                log.warn("binlog polarx command status is running!");
                return false;
            }

            if (command.getCmdStatus() == 2) {
                log.error("binlog polarx command status is error, reply:{}, retry times:{}", command.getCmdReply(),
                    buildMetaSnapshotRetryTimes);
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.META_SNAP_REBUILD_ERROR_WARNNIN, command.getCmdReply());

                // 更改cmd status为0，尝试重新构建
                if (buildMetaSnapshotRetryTimes < DynamicApplicationConfig.getLong(
                    ConfigKeys.META_BUILD_SNAPSHOT_RETRY_TIMES)) {
                    buildMetaSnapshotRetryTimes++;
                    BinlogPolarxCommand updateCommand = new BinlogPolarxCommand();
                    updateCommand.setId(command.getId());
                    updateCommand.setCmdStatus(0L);
                    polarxCommandMapper.updateByPrimaryKeySelective(updateCommand);
                }
                return false;
            }

            long snapshotCount = logicMetaHistoryMapper.count(s -> s
                .where(BinlogLogicMetaHistoryDynamicSqlSupport.instructionId,
                    SqlBuilder.isEqualTo(command.getCmdId())));
            if (snapshotCount == 0) {
                log.warn("last build meta command not finish! cmdId : " + command.getCmdId());
                return false;
            }

        }

        buildMetaSnapshotRetryTimes = 0;

        return true;

    }

    private void pushNewSnapCommand() {
        final String commandId = UUID.randomUUID().toString();
        BinlogPolarxCommand polarxCommand = new BinlogPolarxCommand();
        polarxCommand.setCmdStatus(0L);
        polarxCommand.setCmdType(PolarxCommandType.BUILD_META_SNAPSHOT.name());
        polarxCommand.setCmdId(commandId);
        polarxCommandMapper.insert(polarxCommand);
    }

    /**
     * 避免多次重复 设置snapshot， 要求两个指令之间时间间隔至少超过
     * 设置前 需要到 logic表查询当前command是否已经记录，如果记录，则可以设置下一次的snap， 否则不能重复设置
     */
    private void trySetRebuildTableMetaSnapFlag() {
        if (log.isDebugEnabled()) {
            log.debug("try to set rebuild snap command!");
        }
        // 查找最近snap后产生的ddl数量
        long phyCount = getRegionDDLCount();
        log.info("last phy count: " + phyCount);
        // 按照各自的ddl数量判断
        int limit = DynamicApplicationConfig.getInt(ConfigKeys.META_BUILD_FULL_SNAPSHOT_THRESHOLD);
        if (phyCount > limit) {
            // 检测上一次指令是否执行结束
            if (!testLastCommandFinish()) {
                return;
            }
            // 执行新的build指令
            pushNewSnapCommand();
            log.info("success set build meta snap command success!");
        }
    }
}
