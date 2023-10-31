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
package com.aliyun.polardbx.rpl.validation.repository.impl;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.dao.ValidationTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ValidationTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.validation.ValidationContext;
import com.aliyun.polardbx.rpl.validation.common.DiffStateEnum;
import com.aliyun.polardbx.rpl.validation.common.Record;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import com.aliyun.polardbx.rpl.validation.repository.ValTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author siyu.yusi
 **/
@Slf4j
public class ValTaskRepositoryImpl implements ValTaskRepository {
    private ValidationContext ctx;
    private static final ValidationTaskMapper valTaskMapper = SpringContextHolder.getObject(ValidationTaskMapper.class);
    private static final ValidationDiffMapper diffMapper = SpringContextHolder.getObject(ValidationDiffMapper.class);

    public ValTaskRepositoryImpl(ValidationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<ValidationDiff> getValDiffList(TableInfo srcTable) {
        return diffMapper.select(
            s -> s.where(ValidationDiffDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(ctx.getStateMachineId()))
                .and(ValidationDiffDynamicSqlSupport.srcPhyDb, SqlBuilder.isEqualTo(ctx.getSrcPhyDB()))
                .and(ValidationDiffDynamicSqlSupport.srcPhyTable, SqlBuilder.isEqualTo(srcTable.getName()))
                .and(ValidationDiffDynamicSqlSupport.type, SqlBuilder.isEqualTo(ctx.getType().getValue()))
                .and(ValidationDiffDynamicSqlSupport.state, SqlBuilder.isEqualTo(DiffStateEnum.INIT.getValue()))
                .and(ValidationDiffDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));
    }

    @Override
    public ValidationTask getValTaskRecord(String srcPhyTable) throws Exception {
        return valTaskMapper.selectOne(s -> s
                .where(ValidationTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(ctx.getStateMachineId()))
                .and(ValidationTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(ctx.getServiceId()))
                .and(ValidationTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(ctx.getTaskId()))
                .and(ValidationTaskDynamicSqlSupport.srcPhyDb, SqlBuilder.isEqualTo(ctx.getSrcPhyDB()))
                .and(ValidationTaskDynamicSqlSupport.srcPhyTable, SqlBuilder.isEqualTo(srcPhyTable))
                .and(ValidationTaskDynamicSqlSupport.type, SqlBuilder.isEqualTo(ctx.getType().getValue()))
                .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)))
            .orElseThrow(() -> new Exception(String.format(
                "Cannot find validation task record with stateMachineId: %s, serviceId: %s, taskId: %s, srcPhyDB: %s, srcTable: %s",
                ctx.getStateMachineId(), ctx.getServiceId(), ctx.getTaskId(), ctx.getSrcPhyDB(),
                srcPhyTable)));
    }

    @Override
    public long countValRecords(String srcTable) {
        return valTaskMapper.count(s -> s
            .where(ValidationTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(ctx.getStateMachineId()))
            .and(ValidationTaskDynamicSqlSupport.serviceId, SqlBuilder.isEqualTo(ctx.getServiceId()))
            .and(ValidationTaskDynamicSqlSupport.taskId, SqlBuilder.isEqualTo(ctx.getTaskId()))
            .and(ValidationTaskDynamicSqlSupport.srcPhyDb, SqlBuilder.isEqualTo(ctx.getSrcPhyDB()))
            .and(ValidationTaskDynamicSqlSupport.srcPhyTable, SqlBuilder.isEqualTo(srcTable))
            .and(ValidationTaskDynamicSqlSupport.type, SqlBuilder.isEqualTo(ctx.getType().getValue()))
            .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));
    }

    @Override
    public void updateValTaskState(TableInfo srcTable, ValidationStateEnum state) throws Exception {
        ValidationTask task = getValTaskRecord(srcTable.getName());
        ValidationTask newTask = new ValidationTask();
        newTask.setId(task.getId());
        newTask.setState(state.getValue());
        valTaskMapper.updateByPrimaryKeySelective(newTask);
    }

    @Override
    public List<ValidationTask> getValTaskList() {
        return valTaskMapper.select(s -> s
            .where(ValidationTaskDynamicSqlSupport.stateMachineId, SqlBuilder.isEqualTo(ctx.getStateMachineId()))
            .and(ValidationTaskDynamicSqlSupport.srcPhyDb, SqlBuilder.isEqualTo(ctx.getSrcPhyDB()))
            .and(ValidationTaskDynamicSqlSupport.type, SqlBuilder.isEqualTo(ctx.getType().getValue()))
            .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false)));
    }

    @Override
    public void createValTasks(ValidationTypeEnum type) {
        TaskContext context = TaskContext.getInstance();
        for (TableInfo table : ctx.getSrcPhyTableList()) {
            log.warn(
                "insert validation_task record. StateMachineId: {}, ServiceId: {}, TaskId: {}, SrcPhyDb: {}, SrcPhyTable: {}",
                ctx.getStateMachineId(), ctx.getServiceId(),
                ctx.getTaskId(),
                ctx.getSrcPhyDB(), table.getName());
            if (countValRecords(table.getName()) > 0) {
                log.warn(
                    "Found duplicate validation_task record. StateMachineId: {}, ServiceId: {}, TaskId: {}, SrcPhyDb: {}, SrcPhyTable: {}",
                    ctx.getStateMachineId(), ctx.getServiceId(),
                    ctx.getTaskId(),
                    ctx.getSrcPhyDB(), table.getName());
                continue;
            }
            ValidationTask task = new ValidationTask();
            task.setExternalId(
                Instant.now().toEpochMilli() + String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000)));
            task.setStateMachineId(Long.toString(context.getStateMachineId()));
            task.setServiceId(Long.toString(context.getServiceId()));
            task.setTaskId(Long.toString(context.getTaskId()));
            task.setType(type.getValue());
            task.setState(ValidationStateEnum.INIT.getValue());
            task.setSrcPhyDb(ctx.getSrcPhyDB());
            task.setSrcLogicalTable(ctx.getMappingTable().get(table.getName()).getName());
            task.setSrcPhyTable(table.getName());
            task.setDstLogicalDb(ctx.getDstLogicalDB());
            task.setDstLogicalTable(ctx.getMappingTable().get(table.getName()).getName());
            valTaskMapper.insertSelective(task);
        }
    }

    @Override
    public void persistDiffRows(TableInfo srcTable, List<Record> keyRowValList) throws Exception {
        ValidationTask task = getValTaskRecord(srcTable.getName());
        log.info("Persisting diff rows. Src Table: {}, Rows number: {}", srcTable.getName(), keyRowValList.size());
        List<ValidationDiff> diffList = new ArrayList<>();
        for (int i = 0; i < keyRowValList.size(); i++) {
            Record r = keyRowValList.get(i);
            ValidationDiff diff = new ValidationDiff();
            diff.setStateMachineId(ctx.getStateMachineId());
            diff.setServiceId(ctx.getServiceId());
            diff.setTaskId(ctx.getTaskId());
            diff.setValidationTaskId(task.getExternalId());
            diff.setSrcPhyDb(ctx.getSrcPhyDB());
            diff.setSrcPhyTable(srcTable.getName());
            // diff.setSrcPhyKeyCol(JSONObject.toJSONString(r.getColumnList()));
            diff.setSrcKeyColVal(JSONObject.toJSONString(r.getValList()));
            diff.setDstLogicalTable(task.getDstLogicalTable());
            // diff.setDstLogicalKeyCol(JSONObject.toJSONString(r.getColumnList()));
            diff.setDstKeyColVal(JSONObject.toJSONString(r.getValList()));
            diff.setDeleted(false);
            diff.setType(task.getType());
            diff.setState(DiffStateEnum.INIT.getValue());
            diff.setCreateTime(Date.from(Instant.now()));
            diff.setUpdateTime(Date.from(Instant.now()));
            diffList.add(diff);
            try {
                if (diffList.size() >= 1000 || i == keyRowValList.size() - 1) {
                    log.info("Try inserting batch records. Records size: {}", diffList.size());
                    diffMapper.insertMultiple(diffList);
                    diffList = new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("Bulk insert into validation_diff table exception. Try insert records one by one", e);
                diffList.forEach(diffRecord -> {
                    log.info("insert one by one for diff record: {}", diffRecord.getSrcPhyDb() + "."
                        + diffRecord.getSrcPhyTable() + " " + diffRecord.getSrcKeyColVal());
                    diffMapper.insertSelective(diffRecord);
                });
                diffList = new ArrayList<>();
            }
        }
    }

    @Override
    public ValidationTask getValTaskByRefId(String refId) throws SQLException {
        return valTaskMapper.selectOne(
            s -> s.where(ValidationTaskDynamicSqlSupport.externalId, SqlBuilder.isEqualTo(refId))
                .and(ValidationTaskDynamicSqlSupport.deleted, SqlBuilder.isEqualTo(false))).orElseThrow(
            () -> new SQLException(String.format("Cannot find validationTask with refId: %s", refId))
        );
    }
}
