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
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.RecoveryFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * @author jiyue
 */

@Service
public class MetaManagerTranProxy {

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, DataImportMeta meta,
                                            DataImportFSM fsm) throws Throwable {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, ReplicaMeta meta, ReplicaFSM fsm) throws Throwable {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public RplStateMachine initStateMachine(StateMachineType type, RecoveryMeta meta, RecoveryFSM fsm) {
        return FSMMetaManager.initStateMachine(type, meta, fsm);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> deleteStateMachine(long id) {
        return FSMMetaManager.deleteStateMachine(id);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> startSlaveWithTran(Map<String, String> params) {
        return RplServiceManager.startSlaveWithTran(params);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> stopSlaveWithTran(Map<String, String> params) {
        return RplServiceManager.stopSlaveWithTran(params);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> resetSlaveWithTran(Map<String, String> params) {
        return RplServiceManager.resetSlaveWithTran(params);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> changeMasterWithTran(Map<String, String> params) {
        return RplServiceManager.changeMasterWithTran(params);
    }

    @Transactional(rollbackFor = {Throwable.class})
    public ResultCode<?> changeReplicationFilterWithTran(Map<String, String> params) {
        return RplServiceManager.changeReplicationFilterWithTran(params);
    }
}
