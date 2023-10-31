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
    NULL(-1),
    INITIAL(0),
    FINISHED(1),
    // 100 - 199 for data import
    FULL_COPY(101),
    INC_COPY(102),
    CATCH_UP_VALIDATION(104),
    RECONCILIATION(105),
    RECON_FINISHED_WAIT_CATCH_UP(108),
    RECON_FINISHED_CATCH_UP(106),
    BACK_FLOW(107),
    BACK_FLOW_CATCH_UP(109),
    // 200 - 299 for replica
    REPLICA_INIT(202),
    REPLICA_FULL(203),
    REPLICA_INC(201),
    // 300 - 399 for flash back
    REC_SEARCH(301),
    REC_COMBINE(302);
    private final int value;

    FSMState(int value) {
        this.value = value;
    }

    public static FSMState from(int value) {
        for (FSMState i : FSMState.values()) {
            if (i.value == value) {
                return i;
            }
        }
        return NULL;
    }

    public static List<FSMState> listFromString(String stateListStr) {
        List<FSMState> stateList = new LinkedList<>();
        String[] strList = stateListStr.split(",");
        for (String str : strList) {
            if (StringUtils.isNotBlank(str)) {
                stateList.add(from(Integer.parseInt(str)));
            }
        }
        return stateList;
    }

    public static String listToString(List<FSMState> stateList) {
        return stateList.stream()
            .map(FSMState::getValueStr)
            .collect(Collectors.joining(","));
//        return stateList
//            .stream()
//            .reduce(new StringBuilder(), (sb, s) -> sb.append(s.value).append(','), StringBuilder::append).toString();
    }

    /* 检测stateListStr中是否包含目标state */
    public static boolean contain(String stateListStr, FSMState targetState) {
        List<FSMState> stateList = listFromString(stateListStr);
        if (stateList == null) {
            return false;
        }
        for (FSMState state : stateList) {
            if (state.value == targetState.value) {
                return true;
            }
        }
        return false;
    }

    public int getValue() {
        return value;
    }

    public String getValueStr() {
        return Integer.toString(value);
    }
}
