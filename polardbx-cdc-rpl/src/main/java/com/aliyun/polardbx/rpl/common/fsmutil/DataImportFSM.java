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
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineType;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;

import java.util.Arrays;

/**
 * @author jiyue 2021/08/29
 */

public class DataImportFSM extends AbstractFSM<DataImportMeta> {

    private static DataImportFSM instance = new DataImportFSM();
    private static MetaManagerTranProxy manager = SpringContextHolder.getObject(MetaManagerTranProxy.class);

    private DataImportFSM() {
        super(Arrays.asList(
            new DataImportTransitions.FullCopyFinishTransition(),
            new DataImportTransitions.FullCheckFinishTransition(),
            new DataImportTransitions.FullReconFinishTransition(),
            new DataImportTransitions.IncCopyCatchUpTransition(),
            new DataImportTransitions.CheckCatchUpTransition(),
            new DataImportTransitions.CheckNotCatchUpTransition(),
            new DataImportTransitions.CheckBackFlowCatchUpTransition(),
            new DataImportTransitions.CheckBackFlowNotCatchUpTransition()
        ), FSMState.FULL_COPY);
    }

    public static DataImportFSM getInstance() {
        return instance;
    }

    @Override
    public long create(DataImportMeta meta) {
        try {
            RplStateMachine stateMachine = manager.initStateMachine(StateMachineType.DATA_IMPORT, meta, this);
            return stateMachine.getId();
        } catch (Throwable e) {
            return RplConstants.ERROR_FSMID;
        }
    }

}
