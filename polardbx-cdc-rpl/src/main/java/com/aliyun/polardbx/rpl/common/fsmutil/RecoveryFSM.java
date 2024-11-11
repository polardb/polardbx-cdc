/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

/**
 * @author yudong
 */
public class RecoveryFSM extends AbstractFSM<RecoveryMeta> {

    private static final RecoveryFSM instance = new RecoveryFSM();
    private static MetaManagerTranProxy manager = SpringContextHolder.getObject(MetaManagerTranProxy.class);

    private RecoveryFSM() {
        super(Arrays.asList(
            new RecoveryTransitions.SearchFinishTransition(),
            new RecoveryTransitions.ResultCombineFinishTransition()
        ), FSMState.REC_SEARCH);
    }

    public static RecoveryFSM getInstance() {
        return instance;
    }

    @Override
    public long create(RecoveryMeta meta) {
        try {
            TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
            return transactionTemplate.execute(o -> {
                RplStateMachine stateMachine = manager.initStateMachine(StateMachineType.RECOVERY, meta, this);
                return stateMachine.getId();
            });
        } catch (Throwable e) {
            return RplConstants.ERROR_FSMID;
        }
    }
}
