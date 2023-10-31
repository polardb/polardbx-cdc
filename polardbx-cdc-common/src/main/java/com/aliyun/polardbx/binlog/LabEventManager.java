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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.BinlogLabEventMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLabEvent;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 实验室环境一些场景需要打点记录log
 */
public class LabEventManager {

    private static final Logger logger = LoggerFactory.getLogger(LabEventManager.class);
    private static final LabEventManager instance = new LabEventManager();
    private final boolean isOn;
    private final BinlogLabEventMapper mapper;

    private LabEventManager() {
        mapper = SpringContextHolder.getObject(BinlogLabEventMapper.class);
        isOn = DynamicApplicationConfig.getBoolean(ConfigKeys.TEST_OPEN_BINLOG_LAB_EVENT_SUPPORT);
        if (isOn) {
            logger.info("init action log in test environment");
        }
    }

    public static void logEvent(LabEventType actionType) {
        logEvent(actionType, null);
    }

    public static void logEvent(LabEventType actionType, String params) {
        innerLog(actionType, params);
    }

    private static void innerLog(LabEventType actionType, String params) {
        try {
            if (isIgnore()) {
                return;
            }
            instance.mapper.insert(actionType.ordinal(), actionType.getDesc(), params);
        } catch (Exception e) {
            logger.error("log lab event error ! type " + actionType + ", params : " + params, e);
        }
    }

    public static List<BinlogLabEvent> selectByType(LabEventType typeEnum) {
        if (isIgnore()) {
            return Lists.newArrayList();
        }

        return instance.mapper.selectByEventType(typeEnum.ordinal());
    }

    private static boolean isIgnore() {
        return !instance.isOn;
    }
}
