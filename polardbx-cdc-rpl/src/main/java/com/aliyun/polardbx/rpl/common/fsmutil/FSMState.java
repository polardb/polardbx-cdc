/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.common.fsmutil;

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
    REPLICA(201);

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
            stateList.add(from(Integer.parseInt(str)));
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
