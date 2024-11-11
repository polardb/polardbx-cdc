/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation.fullvalid.task;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.RplFullValidDiffMapper;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RplFullValidSubTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidDiff;
import com.aliyun.polardbx.binlog.domain.po.RplFullValidSubTask;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidDiffStatus;
import com.aliyun.polardbx.rpl.validation.fullvalid.ReplicaFullValidUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.tuple.MutablePair;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;

/**
 * TODO: check schema
 *
 * @author yudong
 * @since 2023/10/25 16:41
 **/
public class ReplicaFullValidCheckSchemaTask extends ReplicaFullValidSubTask {

    private static final Logger log = LoggerFactory.getLogger("fullValidLogger");
    private final long taskId;
    private final ReplicaFullValidSubTaskContext context;
    private final TaskConfig config;

    private final DataSource srcDataSource;
    private final DataSource dstDataSource;
    private static final RplFullValidSubTaskMapper subTaskMapper =
        SpringContextHolder.getObject(RplFullValidSubTaskMapper.class);
    private static final RplFullValidDiffMapper diffMapper =
        SpringContextHolder.getObject(RplFullValidDiffMapper.class);

    @Override
    public void run() {

        log.info("check schema type: {}", config.getSubType());

        // IMPORTANT: 这里需要recheck一下subtask的状态是否还是ready，
        boolean switchSucc =
            ReplicaFullValidTaskManager.switchSubTaskState(context.getSubTaskId(), ReplicaFullValidTaskState.READY,
                ReplicaFullValidTaskState.RUNNING);
        if (!switchSucc) {
            return;
        }

        try {
            switch (ReplicaFullValidCheckSchemaType.valueOf(config.getSubType())) {
            case DATABASE:
                checkDatabase();
                break;
            case TABLE:
                checkTable();
                break;
            case INDEX:
                checkIndex();
                break;
            case PROCEDURE:
                checkProcedure();
                break;
            case UDF:
                checkUDF();
                break;
            case VIEW:
                checkView();
                break;
            case SEQUENCE:
                checkSequence();
                break;
            default:
                log.error("Unknown check schema type: {}", config.getSubType());
                break;
            }

            ReplicaFullValidTaskManager.switchSubTaskState(context.getSubTaskId(),
                ReplicaFullValidTaskState.RUNNING,
                ReplicaFullValidTaskState.FINISHED);
        } catch (SQLException e) {
            log.error("Encounter SQLException when check schema", e);
        }
    }

    public ReplicaFullValidCheckSchemaTask(ReplicaFullValidSubTaskContext context) {
        this.context = context;
        this.taskId = context.getSubTaskId();
        Optional<RplFullValidSubTask> subTask = subTaskMapper.selectOne(
            s -> s.where(RplFullValidSubTaskDynamicSqlSupport.id, SqlBuilder.isEqualTo(context.getSubTaskId())));
        if (!subTask.isPresent()) {
            throw new RuntimeException("Failed to find sub task:" + taskId);
        }
        this.config = JSON.parseObject(subTask.get().getTaskConfig(), TaskConfig.class);
        this.srcDataSource = context.getSrcDbMetaCache().getDefaultDataSource();
        this.dstDataSource = context.getDstDbMetaCache().getDefaultDataSource();
    }

    public static RplFullValidSubTask generateTaskMeta(long fsmId, long taskId, TaskConfig config) {
        RplFullValidSubTask res = new RplFullValidSubTask();
        res.setStateMachineId(fsmId);
        res.setTaskId(taskId);
        res.setTaskType(ReplicaFullValidCheckSchemaTask.class.getName());
        res.setTaskStage(ReplicaFullValidTaskStage.SCHEMA_CHECK.toString());
        res.setTaskState(ReplicaFullValidTaskState.READY.toString());
        res.setTaskConfig(JSON.toJSONString(config));
        return res;
    }

    @Data
    @ToString
    @AllArgsConstructor
    public static class TaskConfig {
        String subType;
    }

    public void checkDatabase() throws SQLException {
        String sql = "select schema_name from information_schema.SCHEMATA";
        checkInner(sql);
    }

    public void checkTable() throws SQLException {
        String sql = "select table_schema,table_name from information_schema.tables"
            + " where table_schema != 'information_schema'";
        checkInner(sql);
    }

    public void checkProcedure() throws SQLException {
        // PROCEDURE and UDF(not Java UDF)
        String sql = "select ROUTINE_SCHEMA,ROUTINE_NAME,ROUTINE_TYPE from information_schema.Routines";
        checkInner(sql);
    }

    public void checkUDF() throws SQLException {
        // only Java UDF
        String sql = "select FUNCTION_NAME,CLASS_NAME from information_schema.Java_functions";
        checkInner(sql);
    }

    public void checkView() throws SQLException {
        // select TABLE_SCHEMA,TABLE_NAME from information_schema.views
        String sql = "select TABLE_SCHEMA,TABLE_NAME from information_schema.views";
        checkInner(sql);
    }

    public void checkSequence() throws SQLException {
        // select schema_name,name,type from information_schema.sequences
        String sql = "select schema_name,name,type from information_schema.sequences";
        checkInner(sql);
    }

    public void checkIndex() throws SQLException {
        // Information_schema.statistics没有gsi,目前用户没有实例级别获取gsi的方式，只能从metadb取
        // raw sql: SELECT table_schema,table_name,index_name,column_name,index_location FROM metadb.indexes
        // 要求replica账号有select metadb权限
        // table name / index name需要unwrap一下随机后缀
        String sql = "SELECT\n"
            + "        table_schema,\n"
            + "        CASE WHEN CHAR_LENGTH(table_name) > 6 AND RIGHT(table_name, 6) = '_$' THEN\n"
            + "            LEFT(table_name, CHAR_LENGTH(table_name) - 6)\n"
            + "        ELSE\n"
            + "            table_name\n"
            + "        END AS unwrapped_table_name,\n"
            + "        CASE WHEN CHAR_LENGTH(index_name) > 6 AND RIGHT(index_name, 6) = '_$' THEN\n"
            + "            LEFT(index_name, CHAR_LENGTH(index_name) - 6)\n"
            + "        ELSE\n"
            + "            table_name\n"
            + "        END AS unwrapped_table_name,\n"
            + "        column_name\n"
            + "            FROM\n"
            + "        metadb.indexes";
        checkInner(sql);
    }

    public void checkInner(String sql) throws SQLException {
        log.info("Check schema begin for task : {}, type: {}", taskId, config.getSubType());
        MutablePair<Set<String>, Set<String>> diffRows = new MutablePair<>();
        try (Connection srcConn = srcDataSource.getConnection();
            Connection dstConn = dstDataSource.getConnection()) {
            if (!ReplicaFullValidUtil.checkAndGetDiffRows(srcConn, dstConn, sql, diffRows)) {
                for (String diffValue : diffRows.getLeft()) {
                    RplFullValidDiff diff = new RplFullValidDiff();
                    diff.setTaskId(context.getFullValidTaskId());
                    diff.setSrcKeyName(config.getSubType());
                    diff.setDstKeyName(config.getSubType());
                    diff.setStatus(ReplicaFullValidDiffStatus.FOUND.name());
                    diff.setSrcKeyVal(sql + " : " + diffValue);
                    diff.setErrorType("SchemaSrc");
                    diffMapper.insertSelective(diff);
                }
                for (String diffValue : diffRows.getRight()) {
                    RplFullValidDiff diff = new RplFullValidDiff();
                    diff.setTaskId(context.getFullValidTaskId());
                    diff.setSrcKeyName(config.getSubType());
                    diff.setDstKeyName(config.getSubType());
                    diff.setStatus(ReplicaFullValidDiffStatus.FOUND.name());
                    diff.setDstKeyVal(sql + " : " + diffValue);
                    diff.setErrorType("SchemaDst");
                    diffMapper.insertSelective(diff);
                }
                log.warn("Find diff schema rows: src {}, dst {}", diffRows.getLeft(), diffRows.getRight());
            }
        }
        log.info("Check schema end for task : {}, type: {}", taskId, config.getSubType());
    }
}
