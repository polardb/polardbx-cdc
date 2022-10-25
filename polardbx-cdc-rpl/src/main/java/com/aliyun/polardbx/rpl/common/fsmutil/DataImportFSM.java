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
            new DataImportTransitions.CheckNotCatchUpTransition()
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
