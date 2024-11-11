/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common.fsmutil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FSMTransition {
    /*
    参数1和2:当前状态和目标状态
    triggeredaction:状态转换成功后的触发函数
    fsmAction:用于判断是否可以进行本次转换，如action相等则成功，不相等则取isMatch函数的结果
    * */
    private FSMState currentState;

    private FSMState nextState;

    private FSMTriggeredAction triggeredAction;

    private FSMAction fsmAction;

    /* 重载此函数实现自定义判断 */
    public boolean isMatch(long FSMId) {
        return false;
    }

    /* action为空时需要自行判断是否match */
    public boolean isMatch(long FSMId, FSMAction action) {
        if (fsmAction != null && action != null && StringUtils.equals(action.getValue(), fsmAction.getValue())) {
            return true;
        }
        return isMatch(FSMId);
    }

}
