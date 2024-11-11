/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
