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
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogDumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.TaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author yudong
 * @since 2023/7/24 17:14
 **/
public class GmsTimeUtil {
    public static long getCurrentTimeMillis() {
        try {
            JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
            Long now =
                metaTemplate.queryForObject("SELECT ROUND(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(4)) * 1000)", Long.class);
            if (now == null) {
                throw new Exception("get current timestamp null");
            }
            return now;
        } catch (Exception e) {
            throw new RuntimeException("get current time from GMS error", e);
        }
    }

    public static long getHeartbeatInterval(String taskType, String clusterId, String taskName) {
        if (TaskType.isTask(taskType)) {
            TaskInfoMapper taskInfoMapper = SpringContextHolder.getObject(TaskInfoMapper.class);
            return taskInfoMapper.getHeartbeatInterval(taskName, clusterId);
        } else if (TaskType.isDumper(taskType)) {
            BinlogDumperInfoMapper dumperInfoMapper = SpringContextHolder.getObject(BinlogDumperInfoMapper.class);
            return dumperInfoMapper.getHeartbeatInterval(taskName, clusterId);
        } else {
            throw new IllegalArgumentException("illegal task type:" + taskName);
        }

    }

}
