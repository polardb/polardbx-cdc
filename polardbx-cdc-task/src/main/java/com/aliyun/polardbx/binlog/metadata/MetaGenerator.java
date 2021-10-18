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

package com.aliyun.polardbx.binlog.metadata;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class MetaGenerator {
    public enum Command {
        /*
         * 初始化
         */
        INIT("00000000", "CDC_START");
        private final String requestId;
        private final String commandType;

        Command(String requestId, String commandType) {
            this.requestId = requestId;
            this.commandType = commandType;
        }
    }

    public boolean isStartSuccess() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        Integer status = metaJdbcTemplate.queryForObject(
            String.format(
                "select cmd_status from binlog_polarx_command where cmd_id = '%s' and cmd_type = '%s'",
                Command.INIT.requestId, Command.INIT.commandType),
            Integer.class);

        if (status == 0) {
            return false;
        } else if (status == 1) {
            return true;
        } else {
            throw new PolardbxException("CDC_START command is failed.");
        }
    }

    public void tryStart() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        metaJdbcTemplate.execute(
            "INSERT INTO binlog_polarx_command (cmd_id,cmd_type,cmd_status) values ('" + Command.INIT.requestId + "','"
                + Command.INIT.commandType + "',0) ON DUPLICATE KEY UPDATE gmt_modified = NOW()");

        while (true) {
            if (isStartSuccess()) {
                return;
            }
            log.info("CDC_START command is not finished, will try later.");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }
}
