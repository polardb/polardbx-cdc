/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.validation.common.DiffStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author siyu.yusi
 **/
@Slf4j
public class ValidationTaskRepository {
    private static final String stateMachineId = Long.toString(TaskContext.getInstance().getStateMachineId());
    private static final String serviceId = Long.toString(TaskContext.getInstance().getServiceId());
    private static final String taskId = Long.toString(TaskContext.getInstance().getTaskId());

    private static final ValidationTaskMapper valTaskMapper = SpringContextHolder.getObject(ValidationTaskMapper.class);
    private static final ValidationDiffMapper diffMapper = SpringContextHolder.getObject(ValidationDiffMapper.class);

    public static List<ValidationDiff> getValDiffListWithLimit(String srcDbName, String srcTableName) {
        return diffMapper.select(
            s -> s.where(ValidationDiffDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
                .and(ValidationDiffDynamicSqlSupport.srcLogicalDb, SqlBuilder.isEqualTo(srcDbName))
                .and(ValidationDiffDynamicSqlSupport.srcLogicalTable, SqlBuilder.isEqualTo(srcTableName))
                .and(ValidationDiffDynamicSqlSupport.state, SqlBuilder.isEqualTo(DiffStateEnum.INIT.name()))
                .and(ValidationDiffDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)).limit(10000));
    }

    public static Optional<ValidationTask> getValTaskRecord(String srcDbName, String srcTableName,
                                                            ValidationTypeEnum type) {
        return valTaskMapper.selectOne(s -> s
            .where(ValidationTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
            .and(ValidationTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(serviceId))
            .and(ValidationTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
            .and(ValidationTaskDynamicSqlSupport.srcLogicalDb, SqlBuilder.isEqualTo(srcDbName))
            .and(ValidationTaskDynamicSqlSupport.srcLogicalTable, SqlBuilder.isEqualTo(srcTableName))
            .and(ValidationTaskDynamicSqlSupport.type, SqlBuilder.isEqualTo(type.name()))
            .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));
    }

    public static long countValRecords(String srcDbName, String srcTableName, ValidationTypeEnum type) {
        return valTaskMapper.count(s -> s
            .where(ValidationTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(stateMachineId))
            .and(ValidationTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(serviceId))
            .and(ValidationTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(taskId))
            .and(ValidationTaskDynamicSqlSupport.srcPhyDb, SqlBuilder.isEqualTo(srcDbName))
            .and(ValidationTaskDynamicSqlSupport.srcPhyTable, SqlBuilder.isEqualTo(srcTableName))
            .and(ValidationTaskDynamicSqlSupport.type, SqlBuilder.isEqualTo(type.name()))
            .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));
    }

    public static void updateValTaskState(String srcDbName, String srcTableName,
                                          ValidationTypeEnum type, ValidationStateEnum state) {
        Optional<ValidationTask> valTaskRecord = getValTaskRecord(srcDbName, srcTableName, type);
        if (valTaskRecord.isPresent()) {
            updateValTaskState(valTaskRecord.get().getId(), state);
        } else {
            log.error(
                "no task exist! state machine id:{}, service id:{}, task id:{}, type:{}, src logical db:{}, src logical tb:{}",
                stateMachineId, serviceId, taskId, type, srcDbName, srcTableName);
            throw new IllegalArgumentException("no task exist! dbName:" + srcDbName + ", tbName:" + srcTableName);
        }
    }

    private static void updateValTaskState(long id, ValidationStateEnum newState) {
        ValidationTask t = new ValidationTask();
        t.setId(id);
        t.setState(newState.name());
        valTaskMapper.updateByPrimaryKeySelective(t);
    }

    public static void createValTask(String srcDbName, String dstDbName, String tableName, ValidationTypeEnum type) {
        ValidationTask task = new ValidationTask();
        task.setExternalId(
            Instant.now().toEpochMilli() + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000)));
        task.setStateMachineId(stateMachineId);
        task.setServiceId(serviceId);
        task.setTaskId(taskId);
        task.setType(type.name());
        task.setState(ValidationStateEnum.INIT.name());
        task.setSrcLogicalDb(srcDbName);
        task.setSrcLogicalTable(tableName);
        task.setDstLogicalDb(dstDbName);
        task.setDstLogicalTable(tableName);
        valTaskMapper.insertSelective(task);
    }

    public static void resetValTask(ValidationTask task) {
        // DbTaskMetaManager.deleteValidationDiffByValTaskId(task.getExternalId());
        updateValTaskState(task.getId(), ValidationStateEnum.INIT);
    }

    public ValidationTask getValTaskByRefId(String refId) throws SQLException {
        return valTaskMapper.selectOne(
            s -> s.where(ValidationTaskDynamicSqlSupport.externalId, SqlBuilder.isEqualTo(refId))
                .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false))).orElseThrow(
            () -> new SQLException(String.format("Cannot find validationTask with refId: %s", refId))
        );
    }
}
