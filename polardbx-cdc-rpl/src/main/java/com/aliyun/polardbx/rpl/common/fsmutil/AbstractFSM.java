/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jiyue 2021/08/29
 */

public abstract class AbstractFSM<T> {

    private List<FSMTransition> transitionList;
    private FSMState initialState;
    private static final MetaManagerTranProxy TRANSACTION_MANAGER =
        SpringContextHolder.getObject(MetaManagerTranProxy.class);

    public AbstractFSM(List<FSMTransition> transitionList, FSMState initialState) {
        this.transitionList = transitionList;
        this.initialState = initialState;
    }

    public FSMState getInitialState() {
        return initialState;
    }

    public abstract long create(T metaData);

    public void update(RplStateMachine stateMachine, FSMAction action) {
        long FSMId = stateMachine.getId();
        FSMState nowState = FSMState.valueOf(stateMachine.getState());
        List<FSMTransition> transitions = transitionList
            .stream()
            .filter(tran -> tran.getCurrentState() == nowState)
            .collect(Collectors.toList());

        if (transitions.isEmpty()) {
            return;
        }
        // 一次update只进行一次状态切换
        for (FSMTransition transition : transitions) {
            if (transition.isMatch(FSMId, null)) {
                updateState(FSMId, transition.getNextState());
                if (transition.getTriggeredAction() != null) {
                    transition.getTriggeredAction().execute(FSMId);
                }
                break;
            }
        }
    }

    public void updateState(long FSMId, FSMState state) {
        // 防止重入导致状态错乱
        synchronized (this) {
            DbTaskMetaManager.updateStateMachineState(FSMId, state);
            FSMMetaManager.startServiceByState(FSMId);
            // 立即distribute一次，防止running进入ready导致重启
            TRANSACTION_MANAGER.distributeTasks();
        }
    }

}
