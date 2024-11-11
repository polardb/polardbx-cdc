/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import static com.aliyun.polardbx.binlog.dao.RplStatMetricsDynamicSqlSupport.*;
import static org.mybatis.dynamic.sql.SqlBuilder.*;

import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Generated;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.dynamic.sql.BasicColumn;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.delete.render.DeleteStatementProvider;
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider;
import org.mybatis.dynamic.sql.select.CountDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.UpdateDSL;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateModel;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.mybatis.dynamic.sql.util.SqlProviderAdapter;
import org.mybatis.dynamic.sql.util.mybatis3.MyBatis3Utils;

@Mapper
public interface RplStatMetricsMapper {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.668+08:00", comments="Source Table: rpl_stat_metrics")
    BasicColumn[] selectList = BasicColumn.columnList(id, gmtCreated, gmtModified, taskId, outRps, applyCount, inEps, outBps, inBps, outInsertRps, outUpdateRps, outDeleteRps, receiveDelay, processDelay, mergeBatchSize, rt, skipCounter, skipExceptionCounter, persistMsgCounter, msgCacheSize, cpuUseRatio, memUseRatio, fullGcCount, workerIp, fsmId, totalCommitCount, trueDelayMills);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.65+08:00", comments="Source Table: rpl_stat_metrics")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    long count(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.652+08:00", comments="Source Table: rpl_stat_metrics")
    @DeleteProvider(type=SqlProviderAdapter.class, method="delete")
    int delete(DeleteStatementProvider deleteStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.652+08:00", comments="Source Table: rpl_stat_metrics")
    @InsertProvider(type=SqlProviderAdapter.class, method="insert")
    @SelectKey(statement="SELECT LAST_INSERT_ID()", keyProperty="record.id", before=false, resultType=Long.class)
    int insert(InsertStatementProvider<RplStatMetrics> insertStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.658+08:00", comments="Source Table: rpl_stat_metrics")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="apply_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="in_eps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_bps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="in_bps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_insert_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_update_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_delete_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="receive_delay", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="process_delay", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="merge_batch_size", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="rt", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="skip_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="skip_exception_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="persist_msg_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="msg_cache_size", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cpu_use_ratio", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem_use_ratio", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="full_gc_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="worker_ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="fsm_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="total_commit_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="true_delay_mills", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    Optional<RplStatMetrics> selectOne(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.659+08:00", comments="Source Table: rpl_stat_metrics")
    @SelectProvider(type=SqlProviderAdapter.class, method="select")
    @ConstructorArgs({
        @Arg(column="id", javaType=Long.class, jdbcType=JdbcType.BIGINT, id=true),
        @Arg(column="gmt_created", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="gmt_modified", javaType=Date.class, jdbcType=JdbcType.TIMESTAMP),
        @Arg(column="task_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="apply_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="in_eps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_bps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="in_bps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_insert_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_update_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="out_delete_rps", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="receive_delay", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="process_delay", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="merge_batch_size", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="rt", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="skip_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="skip_exception_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="persist_msg_counter", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="msg_cache_size", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="cpu_use_ratio", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="mem_use_ratio", javaType=Integer.class, jdbcType=JdbcType.INTEGER),
        @Arg(column="full_gc_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="worker_ip", javaType=String.class, jdbcType=JdbcType.VARCHAR),
        @Arg(column="fsm_id", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="total_commit_count", javaType=Long.class, jdbcType=JdbcType.BIGINT),
        @Arg(column="true_delay_mills", javaType=Long.class, jdbcType=JdbcType.BIGINT)
    })
    List<RplStatMetrics> selectMany(SelectStatementProvider selectStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.66+08:00", comments="Source Table: rpl_stat_metrics")
    @UpdateProvider(type=SqlProviderAdapter.class, method="update")
    int update(UpdateStatementProvider updateStatement);

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.661+08:00", comments="Source Table: rpl_stat_metrics")
    default long count(CountDSLCompleter completer) {
        return MyBatis3Utils.countFrom(this::count, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.662+08:00", comments="Source Table: rpl_stat_metrics")
    default int delete(DeleteDSLCompleter completer) {
        return MyBatis3Utils.deleteFrom(this::delete, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.663+08:00", comments="Source Table: rpl_stat_metrics")
    default int deleteByPrimaryKey(Long id_) {
        return delete(c -> 
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.663+08:00", comments="Source Table: rpl_stat_metrics")
    default int insert(RplStatMetrics record) {
        return MyBatis3Utils.insert(this::insert, record, rplStatMetrics, c ->
            c.map(gmtCreated).toProperty("gmtCreated")
            .map(gmtModified).toProperty("gmtModified")
            .map(taskId).toProperty("taskId")
            .map(outRps).toProperty("outRps")
            .map(applyCount).toProperty("applyCount")
            .map(inEps).toProperty("inEps")
            .map(outBps).toProperty("outBps")
            .map(inBps).toProperty("inBps")
            .map(outInsertRps).toProperty("outInsertRps")
            .map(outUpdateRps).toProperty("outUpdateRps")
            .map(outDeleteRps).toProperty("outDeleteRps")
            .map(receiveDelay).toProperty("receiveDelay")
            .map(processDelay).toProperty("processDelay")
            .map(mergeBatchSize).toProperty("mergeBatchSize")
            .map(rt).toProperty("rt")
            .map(skipCounter).toProperty("skipCounter")
            .map(skipExceptionCounter).toProperty("skipExceptionCounter")
            .map(persistMsgCounter).toProperty("persistMsgCounter")
            .map(msgCacheSize).toProperty("msgCacheSize")
            .map(cpuUseRatio).toProperty("cpuUseRatio")
            .map(memUseRatio).toProperty("memUseRatio")
            .map(fullGcCount).toProperty("fullGcCount")
            .map(workerIp).toProperty("workerIp")
            .map(fsmId).toProperty("fsmId")
            .map(totalCommitCount).toProperty("totalCommitCount")
            .map(trueDelayMills).toProperty("trueDelayMills")
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.665+08:00", comments="Source Table: rpl_stat_metrics")
    default int insertSelective(RplStatMetrics record) {
        return MyBatis3Utils.insert(this::insert, record, rplStatMetrics, c ->
            c.map(gmtCreated).toPropertyWhenPresent("gmtCreated", record::getGmtCreated)
            .map(gmtModified).toPropertyWhenPresent("gmtModified", record::getGmtModified)
            .map(taskId).toPropertyWhenPresent("taskId", record::getTaskId)
            .map(outRps).toPropertyWhenPresent("outRps", record::getOutRps)
            .map(applyCount).toPropertyWhenPresent("applyCount", record::getApplyCount)
            .map(inEps).toPropertyWhenPresent("inEps", record::getInEps)
            .map(outBps).toPropertyWhenPresent("outBps", record::getOutBps)
            .map(inBps).toPropertyWhenPresent("inBps", record::getInBps)
            .map(outInsertRps).toPropertyWhenPresent("outInsertRps", record::getOutInsertRps)
            .map(outUpdateRps).toPropertyWhenPresent("outUpdateRps", record::getOutUpdateRps)
            .map(outDeleteRps).toPropertyWhenPresent("outDeleteRps", record::getOutDeleteRps)
            .map(receiveDelay).toPropertyWhenPresent("receiveDelay", record::getReceiveDelay)
            .map(processDelay).toPropertyWhenPresent("processDelay", record::getProcessDelay)
            .map(mergeBatchSize).toPropertyWhenPresent("mergeBatchSize", record::getMergeBatchSize)
            .map(rt).toPropertyWhenPresent("rt", record::getRt)
            .map(skipCounter).toPropertyWhenPresent("skipCounter", record::getSkipCounter)
            .map(skipExceptionCounter).toPropertyWhenPresent("skipExceptionCounter", record::getSkipExceptionCounter)
            .map(persistMsgCounter).toPropertyWhenPresent("persistMsgCounter", record::getPersistMsgCounter)
            .map(msgCacheSize).toPropertyWhenPresent("msgCacheSize", record::getMsgCacheSize)
            .map(cpuUseRatio).toPropertyWhenPresent("cpuUseRatio", record::getCpuUseRatio)
            .map(memUseRatio).toPropertyWhenPresent("memUseRatio", record::getMemUseRatio)
            .map(fullGcCount).toPropertyWhenPresent("fullGcCount", record::getFullGcCount)
            .map(workerIp).toPropertyWhenPresent("workerIp", record::getWorkerIp)
            .map(fsmId).toPropertyWhenPresent("fsmId", record::getFsmId)
            .map(totalCommitCount).toPropertyWhenPresent("totalCommitCount", record::getTotalCommitCount)
            .map(trueDelayMills).toPropertyWhenPresent("trueDelayMills", record::getTrueDelayMills)
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.669+08:00", comments="Source Table: rpl_stat_metrics")
    default Optional<RplStatMetrics> selectOne(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectOne(this::selectOne, selectList, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.669+08:00", comments="Source Table: rpl_stat_metrics")
    default List<RplStatMetrics> select(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectList(this::selectMany, selectList, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.67+08:00", comments="Source Table: rpl_stat_metrics")
    default List<RplStatMetrics> selectDistinct(SelectDSLCompleter completer) {
        return MyBatis3Utils.selectDistinct(this::selectMany, selectList, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.67+08:00", comments="Source Table: rpl_stat_metrics")
    default Optional<RplStatMetrics> selectByPrimaryKey(Long id_) {
        return selectOne(c ->
            c.where(id, isEqualTo(id_))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.671+08:00", comments="Source Table: rpl_stat_metrics")
    default int update(UpdateDSLCompleter completer) {
        return MyBatis3Utils.update(this::update, rplStatMetrics, completer);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.672+08:00", comments="Source Table: rpl_stat_metrics")
    static UpdateDSL<UpdateModel> updateAllColumns(RplStatMetrics record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalTo(record::getGmtCreated)
                .set(gmtModified).equalTo(record::getGmtModified)
                .set(taskId).equalTo(record::getTaskId)
                .set(outRps).equalTo(record::getOutRps)
                .set(applyCount).equalTo(record::getApplyCount)
                .set(inEps).equalTo(record::getInEps)
                .set(outBps).equalTo(record::getOutBps)
                .set(inBps).equalTo(record::getInBps)
                .set(outInsertRps).equalTo(record::getOutInsertRps)
                .set(outUpdateRps).equalTo(record::getOutUpdateRps)
                .set(outDeleteRps).equalTo(record::getOutDeleteRps)
                .set(receiveDelay).equalTo(record::getReceiveDelay)
                .set(processDelay).equalTo(record::getProcessDelay)
                .set(mergeBatchSize).equalTo(record::getMergeBatchSize)
                .set(rt).equalTo(record::getRt)
                .set(skipCounter).equalTo(record::getSkipCounter)
                .set(skipExceptionCounter).equalTo(record::getSkipExceptionCounter)
                .set(persistMsgCounter).equalTo(record::getPersistMsgCounter)
                .set(msgCacheSize).equalTo(record::getMsgCacheSize)
                .set(cpuUseRatio).equalTo(record::getCpuUseRatio)
                .set(memUseRatio).equalTo(record::getMemUseRatio)
                .set(fullGcCount).equalTo(record::getFullGcCount)
                .set(workerIp).equalTo(record::getWorkerIp)
                .set(fsmId).equalTo(record::getFsmId)
                .set(totalCommitCount).equalTo(record::getTotalCommitCount)
                .set(trueDelayMills).equalTo(record::getTrueDelayMills);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.672+08:00", comments="Source Table: rpl_stat_metrics")
    static UpdateDSL<UpdateModel> updateSelectiveColumns(RplStatMetrics record, UpdateDSL<UpdateModel> dsl) {
        return dsl.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
                .set(gmtModified).equalToWhenPresent(record::getGmtModified)
                .set(taskId).equalToWhenPresent(record::getTaskId)
                .set(outRps).equalToWhenPresent(record::getOutRps)
                .set(applyCount).equalToWhenPresent(record::getApplyCount)
                .set(inEps).equalToWhenPresent(record::getInEps)
                .set(outBps).equalToWhenPresent(record::getOutBps)
                .set(inBps).equalToWhenPresent(record::getInBps)
                .set(outInsertRps).equalToWhenPresent(record::getOutInsertRps)
                .set(outUpdateRps).equalToWhenPresent(record::getOutUpdateRps)
                .set(outDeleteRps).equalToWhenPresent(record::getOutDeleteRps)
                .set(receiveDelay).equalToWhenPresent(record::getReceiveDelay)
                .set(processDelay).equalToWhenPresent(record::getProcessDelay)
                .set(mergeBatchSize).equalToWhenPresent(record::getMergeBatchSize)
                .set(rt).equalToWhenPresent(record::getRt)
                .set(skipCounter).equalToWhenPresent(record::getSkipCounter)
                .set(skipExceptionCounter).equalToWhenPresent(record::getSkipExceptionCounter)
                .set(persistMsgCounter).equalToWhenPresent(record::getPersistMsgCounter)
                .set(msgCacheSize).equalToWhenPresent(record::getMsgCacheSize)
                .set(cpuUseRatio).equalToWhenPresent(record::getCpuUseRatio)
                .set(memUseRatio).equalToWhenPresent(record::getMemUseRatio)
                .set(fullGcCount).equalToWhenPresent(record::getFullGcCount)
                .set(workerIp).equalToWhenPresent(record::getWorkerIp)
                .set(fsmId).equalToWhenPresent(record::getFsmId)
                .set(totalCommitCount).equalToWhenPresent(record::getTotalCommitCount)
                .set(trueDelayMills).equalToWhenPresent(record::getTrueDelayMills);
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.673+08:00", comments="Source Table: rpl_stat_metrics")
    default int updateByPrimaryKey(RplStatMetrics record) {
        return update(c ->
            c.set(gmtCreated).equalTo(record::getGmtCreated)
            .set(gmtModified).equalTo(record::getGmtModified)
            .set(taskId).equalTo(record::getTaskId)
            .set(outRps).equalTo(record::getOutRps)
            .set(applyCount).equalTo(record::getApplyCount)
            .set(inEps).equalTo(record::getInEps)
            .set(outBps).equalTo(record::getOutBps)
            .set(inBps).equalTo(record::getInBps)
            .set(outInsertRps).equalTo(record::getOutInsertRps)
            .set(outUpdateRps).equalTo(record::getOutUpdateRps)
            .set(outDeleteRps).equalTo(record::getOutDeleteRps)
            .set(receiveDelay).equalTo(record::getReceiveDelay)
            .set(processDelay).equalTo(record::getProcessDelay)
            .set(mergeBatchSize).equalTo(record::getMergeBatchSize)
            .set(rt).equalTo(record::getRt)
            .set(skipCounter).equalTo(record::getSkipCounter)
            .set(skipExceptionCounter).equalTo(record::getSkipExceptionCounter)
            .set(persistMsgCounter).equalTo(record::getPersistMsgCounter)
            .set(msgCacheSize).equalTo(record::getMsgCacheSize)
            .set(cpuUseRatio).equalTo(record::getCpuUseRatio)
            .set(memUseRatio).equalTo(record::getMemUseRatio)
            .set(fullGcCount).equalTo(record::getFullGcCount)
            .set(workerIp).equalTo(record::getWorkerIp)
            .set(fsmId).equalTo(record::getFsmId)
            .set(totalCommitCount).equalTo(record::getTotalCommitCount)
            .set(trueDelayMills).equalTo(record::getTrueDelayMills)
            .where(id, isEqualTo(record::getId))
        );
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.674+08:00", comments="Source Table: rpl_stat_metrics")
    default int updateByPrimaryKeySelective(RplStatMetrics record) {
        return update(c ->
            c.set(gmtCreated).equalToWhenPresent(record::getGmtCreated)
            .set(gmtModified).equalToWhenPresent(record::getGmtModified)
            .set(taskId).equalToWhenPresent(record::getTaskId)
            .set(outRps).equalToWhenPresent(record::getOutRps)
            .set(applyCount).equalToWhenPresent(record::getApplyCount)
            .set(inEps).equalToWhenPresent(record::getInEps)
            .set(outBps).equalToWhenPresent(record::getOutBps)
            .set(inBps).equalToWhenPresent(record::getInBps)
            .set(outInsertRps).equalToWhenPresent(record::getOutInsertRps)
            .set(outUpdateRps).equalToWhenPresent(record::getOutUpdateRps)
            .set(outDeleteRps).equalToWhenPresent(record::getOutDeleteRps)
            .set(receiveDelay).equalToWhenPresent(record::getReceiveDelay)
            .set(processDelay).equalToWhenPresent(record::getProcessDelay)
            .set(mergeBatchSize).equalToWhenPresent(record::getMergeBatchSize)
            .set(rt).equalToWhenPresent(record::getRt)
            .set(skipCounter).equalToWhenPresent(record::getSkipCounter)
            .set(skipExceptionCounter).equalToWhenPresent(record::getSkipExceptionCounter)
            .set(persistMsgCounter).equalToWhenPresent(record::getPersistMsgCounter)
            .set(msgCacheSize).equalToWhenPresent(record::getMsgCacheSize)
            .set(cpuUseRatio).equalToWhenPresent(record::getCpuUseRatio)
            .set(memUseRatio).equalToWhenPresent(record::getMemUseRatio)
            .set(fullGcCount).equalToWhenPresent(record::getFullGcCount)
            .set(workerIp).equalToWhenPresent(record::getWorkerIp)
            .set(fsmId).equalToWhenPresent(record::getFsmId)
            .set(totalCommitCount).equalToWhenPresent(record::getTotalCommitCount)
            .set(trueDelayMills).equalToWhenPresent(record::getTrueDelayMills)
            .where(id, isEqualTo(record::getId))
        );
    }
}