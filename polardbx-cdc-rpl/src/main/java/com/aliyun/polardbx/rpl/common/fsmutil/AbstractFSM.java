/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jiyue 2021/08/29
 */

public abstract class AbstractFSM<T> {

    private List<FSMTransition> transitionList;
    private FSMState initialState;

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
        int nowState = stateMachine.getState();
        List<FSMTransition> transitions = transitionList
            .stream()
            .filter(tran -> tran.getCurrentState().getValue() == nowState)
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
        }
    }

}
