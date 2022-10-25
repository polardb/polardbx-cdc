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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineStatus;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author jiyue 2021/08/29
 */

public class FSMManager {

    private static String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
    private static Logger metaLogger = LogUtil.getMetaLogger();

    public static void update() {
        List<RplStateMachine> runningFSM = DbTaskMetaManager.listStateMachine(StateMachineStatus.RUNNING, clusterId);
        for (RplStateMachine stateMachine : runningFSM) {
            try {
                Class<?> clazz = Class.forName(stateMachine.getClassName());
                Method method = clazz.getMethod("getInstance");
                AbstractFSM fsm = (AbstractFSM) method.invoke(null);
                fsm.update(stateMachine, null);
            } catch (Exception e) {
                metaLogger.error("update FSM error: ", e);
            }
        }
    }

}
