/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
public class ReplicaFSM extends AbstractFSM<ReplicaMeta> {

    @Getter
    private static ReplicaFSM instance = new ReplicaFSM();
    private static final MetaManagerTranProxy manager = SpringContextHolder.getObject(MetaManagerTranProxy.class);

    private ReplicaFSM() {
        super(Arrays.asList(
            new ReplicaTransitions.IncrementalModeInitTransition(),
            new ReplicaTransitions.ImageModeInitTransition(),
            new ReplicaTransitions.ReplicaFullFinishTransition(),
            new ReplicaTransitions.ReplicaIncCatchUpTransition(),
            new ReplicaTransitions.ReplicaFullValidStartTransition(),
            new ReplicaTransitions.ReplicaFullValidFinishedTransition()
        ), FSMState.REPLICA_INIT);
    }

    @Override
    public long create(ReplicaMeta meta) {
        try {
            RplStateMachine stateMachine = manager.initStateMachine(StateMachineType.REPLICA, meta, this);
            return stateMachine.getId();
        } catch (Throwable e) {
            // 不能静默吞掉异常：内层initStateMachine与外层共享同一事务，异常已使事务被标记为rollback-only，
            // 如果这里吞掉，客户端只能看到提交阶段的UnexpectedRollbackException，真实原因丢失
            // 注意：meta中包含master密码，不能整体打印
            log.error("create replica state machine failed, channel: {}, master: {}:{}", meta.getChannel(),
                meta.getMasterHost(), meta.getMasterPort(), e);
            throw new PolardbxException("create replica state machine failed: " + e.getMessage(), e);
        }
    }
}

