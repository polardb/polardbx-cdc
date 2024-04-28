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
package com.aliyun.polardbx.rpl.common.fsmutil;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jiyue 2021/08/29
 */
public enum FSMState {
    /*
    for non use
    * */
    NULL,
    INITIAL,
    FINISHED,
    FULL_COPY,
    INC_COPY,
    CATCH_UP_VALIDATION,
    RECONCILIATION,
    RECON_FINISHED_WAIT_CATCH_UP,
    RECON_FINISHED_CATCH_UP,
    BACK_FLOW,
    BACK_FLOW_CATCH_UP,
    BI_DIRECTION,
    REPLICA_INIT,
    REPLICA_FULL,
    REPLICA_INC,
    REPLICA_INC_CATCH_UP,
    REPLICA_FULL_VALID,
    REC_SEARCH,
    REC_COMBINE;

    public static List<FSMState> listFromString(String stateListStr) {
        List<FSMState> stateList = new LinkedList<>();
        String[] strList = stateListStr.split(",");
        for (String str : strList) {
            if (StringUtils.isNotBlank(str)) {
                stateList.add(valueOf(str));
            }
        }
        return stateList;
    }

    public static String listToString(List<FSMState> stateList) {
        return stateList.stream()
            .map(FSMState::name)
            .collect(Collectors.joining(","));
    }

    /* 检测stateListStr中是否包含目标state */
    public static boolean contain(String stateListStr, FSMState targetState) {
        List<FSMState> stateList = listFromString(stateListStr);
        for (FSMState state : stateList) {
            if (StringUtils.equals(state.name(), targetState.name())) {
                return true;
            }
        }
        return false;
    }
}
