/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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

@Service("metaManagerTranProxy")
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

    @Transactional(rollbackFor = {Throwable.class})
    public void distributeTasks() {
        TaskDistributor.distributeTasks();
    }
}
