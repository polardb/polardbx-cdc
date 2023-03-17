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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.system.PolarxCommandType;
import com.aliyun.polardbx.binlog.cdc.meta.MetaType;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import org.apache.commons.lang.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class TableMetaHistoryDbHelper {

    private static final Logger log = LoggerFactory.getLogger(TableMetaHistoryWatcher.class);

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
        List<BinlogLogicMetaHistory> recentSnapshotMetaHistoryList =
            // 查找2个
            logicMetaHistoryMapper.select(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.type,
                SqlBuilder.isEqualTo(MetaType.SNAPSHOT.getValue()))
                .orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso.descending()).limit(2));

        if (!CollectionUtils.isEmpty(recentSnapshotMetaHistoryList) && recentSnapshotMetaHistoryList.size() == 2) {
            BinlogLogicMetaHistory recentBinlogLogicMetaHistory = recentSnapshotMetaHistoryList.get(1);

            List<BinlogOssRecord> binlogOssRecordList = binlogOssRecordMapperExtend.selectMaxTso();
            String maxTSO = null;
            for (BinlogOssRecord record : binlogOssRecordList) {
                if (record == null || StringUtils.isBlank(record.getLastTso())) {
                    maxTSO = null;
                    break;
                } else {
                    maxTSO = CommonUtils.min(maxTSO, record.getLastTso());
                }
            }
            if (StringUtils.isBlank(maxTSO)) {
                return null;
            }
            if (recentBinlogLogicMetaHistory.getTso().compareTo(maxTSO) > 0) {
                return null;
            }
            int deleteCount = 0;
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.META_DDL_RECORD_LOGIC_SOFTDELETE_ENABLE)) {
                deleteCount = logicMetaHistoryMapperExt.softClean(recentBinlogLogicMetaHistory.getTso());
            } else {
                deleteCount = logicMetaHistoryMapper.delete(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.tso,
                    SqlBuilder.isLessThan(recentBinlogLogicMetaHistory.getTso())));
            }
            log.warn("clean logic meta rows count " + deleteCount);
            return recentBinlogLogicMetaHistory.getTso();
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
            // build snap 时会保障发生时， 不会有ddl正在运行或者将要运行， 这里可以安全的清理
            int deleteCount = phyDdlHistoryMapper
                .delete(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso, SqlBuilder.isLessThan(tso)));
            log.info("delete phy ddl count : " + deleteCount);
        }
    }

    private long getRegionDDLCount() {
        Optional<BinlogLogicMetaHistory> snapshotLogicMetaOptional = logicMetaHistoryMapper
            .selectOne(s -> s
                .where(BinlogLogicMetaHistoryDynamicSqlSupport.type, SqlBuilder.isEqualTo(MetaType.SNAPSHOT.getValue()))
                .orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso.descending())
                .limit(1));
        long phyCount;
        //只计算大于最近一次snap后产生的ddl个数
        if (snapshotLogicMetaOptional.isPresent()) {
            BinlogLogicMetaHistory binlogLogicMetaHistory = snapshotLogicMetaOptional.get();
            phyCount = phyDdlHistoryMapper
                .count(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso,
                    SqlBuilder.isGreaterThan(binlogLogicMetaHistory.getTso())));
        } else {
            phyCount = phyDdlHistoryMapper
                .count(s -> s);
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
                log.warn("binlog polarx command status is error, msg : " + command.getCmdReply());
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.META_SNAP_REBUILD_ERROR_WARNNIN, command.getCmdReply());
                return false;
            }
            List<BinlogLogicMetaHistory> selectedList = logicMetaHistoryMapper.select(s -> s
                .where(BinlogLogicMetaHistoryDynamicSqlSupport.instructionId,
                    SqlBuilder.isEqualTo(command.getCmdId())));
            if (selectedList.isEmpty()) {
                log.warn("last build meta command not finish! cmdId : " + command.getCmdId());
                return false;
            }

        }
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
        if (log.isDebugEnabled()) {
            log.debug("last phy count : " + phyCount);
        }
        // 按照各自的ddl数量判断
        int limit = DynamicApplicationConfig.getInt(ConfigKeys.META_DDL_RECORD_TABLE_META_COUNT_LIMIT);
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
