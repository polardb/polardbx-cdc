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
package com.aliyun.polardbx.binlog.domain.po;

import java.util.Date;
import javax.annotation.Generated;

public class RplStatMetrics {
    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.629+08:00", comments="Source field: rpl_stat_metrics.id")
    private Long id;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.632+08:00", comments="Source field: rpl_stat_metrics.gmt_created")
    private Date gmtCreated;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.gmt_modified")
    private Date gmtModified;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.task_id")
    private Long taskId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_rps")
    private Long outRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.apply_count")
    private Long applyCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.in_eps")
    private Long inEps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_bps")
    private Long outBps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.in_bps")
    private Long inBps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_insert_rps")
    private Long outInsertRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_update_rps")
    private Long outUpdateRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_delete_rps")
    private Long outDeleteRps;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.receive_delay")
    private Long receiveDelay;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.process_delay")
    private Long processDelay;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.merge_batch_size")
    private Long mergeBatchSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.rt")
    private Long rt;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_counter")
    private Long skipCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_exception_counter")
    private Long skipExceptionCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.persist_msg_counter")
    private Long persistMsgCounter;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.msg_cache_size")
    private Long msgCacheSize;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.cpu_use_ratio")
    private Integer cpuUseRatio;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.mem_use_ratio")
    private Integer memUseRatio;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.full_gc_count")
    private Long fullGcCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.worker_ip")
    private String workerIp;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.fsm_id")
    private Long fsmId;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.total_commit_count")
    private Long totalCommitCount;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.true_delay_mills")
    private Long trueDelayMills;

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.621+08:00", comments="Source Table: rpl_stat_metrics")
    public RplStatMetrics(Long id, Date gmtCreated, Date gmtModified, Long taskId, Long outRps, Long applyCount, Long inEps, Long outBps, Long inBps, Long outInsertRps, Long outUpdateRps, Long outDeleteRps, Long receiveDelay, Long processDelay, Long mergeBatchSize, Long rt, Long skipCounter, Long skipExceptionCounter, Long persistMsgCounter, Long msgCacheSize, Integer cpuUseRatio, Integer memUseRatio, Long fullGcCount, String workerIp, Long fsmId, Long totalCommitCount, Long trueDelayMills) {
        this.id = id;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.taskId = taskId;
        this.outRps = outRps;
        this.applyCount = applyCount;
        this.inEps = inEps;
        this.outBps = outBps;
        this.inBps = inBps;
        this.outInsertRps = outInsertRps;
        this.outUpdateRps = outUpdateRps;
        this.outDeleteRps = outDeleteRps;
        this.receiveDelay = receiveDelay;
        this.processDelay = processDelay;
        this.mergeBatchSize = mergeBatchSize;
        this.rt = rt;
        this.skipCounter = skipCounter;
        this.skipExceptionCounter = skipExceptionCounter;
        this.persistMsgCounter = persistMsgCounter;
        this.msgCacheSize = msgCacheSize;
        this.cpuUseRatio = cpuUseRatio;
        this.memUseRatio = memUseRatio;
        this.fullGcCount = fullGcCount;
        this.workerIp = workerIp;
        this.fsmId = fsmId;
        this.totalCommitCount = totalCommitCount;
        this.trueDelayMills = trueDelayMills;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.628+08:00", comments="Source Table: rpl_stat_metrics")
    public RplStatMetrics() {
        super();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.632+08:00", comments="Source field: rpl_stat_metrics.id")
    public Long getId() {
        return id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.632+08:00", comments="Source field: rpl_stat_metrics.id")
    public void setId(Long id) {
        this.id = id;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.632+08:00", comments="Source field: rpl_stat_metrics.gmt_created")
    public Date getGmtCreated() {
        return gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.gmt_created")
    public void setGmtCreated(Date gmtCreated) {
        this.gmtCreated = gmtCreated;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.gmt_modified")
    public Date getGmtModified() {
        return gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.gmt_modified")
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.task_id")
    public Long getTaskId() {
        return taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.task_id")
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_rps")
    public Long getOutRps() {
        return outRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_rps")
    public void setOutRps(Long outRps) {
        this.outRps = outRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.apply_count")
    public Long getApplyCount() {
        return applyCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.apply_count")
    public void setApplyCount(Long applyCount) {
        this.applyCount = applyCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.in_eps")
    public Long getInEps() {
        return inEps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.in_eps")
    public void setInEps(Long inEps) {
        this.inEps = inEps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_bps")
    public Long getOutBps() {
        return outBps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.633+08:00", comments="Source field: rpl_stat_metrics.out_bps")
    public void setOutBps(Long outBps) {
        this.outBps = outBps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.in_bps")
    public Long getInBps() {
        return inBps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.in_bps")
    public void setInBps(Long inBps) {
        this.inBps = inBps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_insert_rps")
    public Long getOutInsertRps() {
        return outInsertRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_insert_rps")
    public void setOutInsertRps(Long outInsertRps) {
        this.outInsertRps = outInsertRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_update_rps")
    public Long getOutUpdateRps() {
        return outUpdateRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_update_rps")
    public void setOutUpdateRps(Long outUpdateRps) {
        this.outUpdateRps = outUpdateRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_delete_rps")
    public Long getOutDeleteRps() {
        return outDeleteRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.634+08:00", comments="Source field: rpl_stat_metrics.out_delete_rps")
    public void setOutDeleteRps(Long outDeleteRps) {
        this.outDeleteRps = outDeleteRps;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.receive_delay")
    public Long getReceiveDelay() {
        return receiveDelay;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.receive_delay")
    public void setReceiveDelay(Long receiveDelay) {
        this.receiveDelay = receiveDelay;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.process_delay")
    public Long getProcessDelay() {
        return processDelay;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.process_delay")
    public void setProcessDelay(Long processDelay) {
        this.processDelay = processDelay;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.merge_batch_size")
    public Long getMergeBatchSize() {
        return mergeBatchSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.merge_batch_size")
    public void setMergeBatchSize(Long mergeBatchSize) {
        this.mergeBatchSize = mergeBatchSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.rt")
    public Long getRt() {
        return rt;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.635+08:00", comments="Source field: rpl_stat_metrics.rt")
    public void setRt(Long rt) {
        this.rt = rt;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_counter")
    public Long getSkipCounter() {
        return skipCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_counter")
    public void setSkipCounter(Long skipCounter) {
        this.skipCounter = skipCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_exception_counter")
    public Long getSkipExceptionCounter() {
        return skipExceptionCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.skip_exception_counter")
    public void setSkipExceptionCounter(Long skipExceptionCounter) {
        this.skipExceptionCounter = skipExceptionCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.persist_msg_counter")
    public Long getPersistMsgCounter() {
        return persistMsgCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.persist_msg_counter")
    public void setPersistMsgCounter(Long persistMsgCounter) {
        this.persistMsgCounter = persistMsgCounter;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.msg_cache_size")
    public Long getMsgCacheSize() {
        return msgCacheSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.636+08:00", comments="Source field: rpl_stat_metrics.msg_cache_size")
    public void setMsgCacheSize(Long msgCacheSize) {
        this.msgCacheSize = msgCacheSize;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.cpu_use_ratio")
    public Integer getCpuUseRatio() {
        return cpuUseRatio;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.cpu_use_ratio")
    public void setCpuUseRatio(Integer cpuUseRatio) {
        this.cpuUseRatio = cpuUseRatio;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.mem_use_ratio")
    public Integer getMemUseRatio() {
        return memUseRatio;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.mem_use_ratio")
    public void setMemUseRatio(Integer memUseRatio) {
        this.memUseRatio = memUseRatio;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.full_gc_count")
    public Long getFullGcCount() {
        return fullGcCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.full_gc_count")
    public void setFullGcCount(Long fullGcCount) {
        this.fullGcCount = fullGcCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.worker_ip")
    public String getWorkerIp() {
        return workerIp;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.worker_ip")
    public void setWorkerIp(String workerIp) {
        this.workerIp = workerIp == null ? null : workerIp.trim();
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.fsm_id")
    public Long getFsmId() {
        return fsmId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.fsm_id")
    public void setFsmId(Long fsmId) {
        this.fsmId = fsmId;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.total_commit_count")
    public Long getTotalCommitCount() {
        return totalCommitCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.637+08:00", comments="Source field: rpl_stat_metrics.total_commit_count")
    public void setTotalCommitCount(Long totalCommitCount) {
        this.totalCommitCount = totalCommitCount;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.639+08:00", comments="Source field: rpl_stat_metrics.true_delay_mills")
    public Long getTrueDelayMills() {
        return trueDelayMills;
    }

    @Generated(value="org.mybatis.generator.api.MyBatisGenerator", date="2024-05-17T15:45:51.64+08:00", comments="Source field: rpl_stat_metrics.true_delay_mills")
    public void setTrueDelayMills(Long trueDelayMills) {
        this.trueDelayMills = trueDelayMills;
    }
}