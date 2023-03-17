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
package com.aliyun.polardbx.binlog.metadata;

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.system.PolarxCommandType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.springframework.jdbc.core.JdbcTemplate;

public class BinlogInitCommand implements CdcInitCommand {

    private String commandId;

    public BinlogInitCommand(String commandId) {
        this.commandId = commandId;
    }

    @Override
    public void tryStart() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");

        if (ClusterTypeEnum.BINLOG.name().equals(DynamicApplicationConfig.getClusterType())) {
            //单流考虑下兼容性
            Integer count = metaJdbcTemplate.queryForObject(
                String.format(
                    "select count(*) from binlog_polarx_command where cmd_id = '%s' and cmd_type = '%s'",
                    "00000000", PolarxCommandType.CDC_START.name()),
                Integer.class);
            if (count > 0) {
                commandId = "00000000";
            }
        }

        metaJdbcTemplate.execute(
            "INSERT INTO binlog_polarx_command (cmd_id,cmd_type,cmd_status) values ('"
                + commandId + "','"
                + PolarxCommandType.CDC_START + "',0) ON DUPLICATE KEY UPDATE gmt_modified = NOW()");
    }

    @Override
    public boolean waitSuccess() {
        JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        Integer status = metaJdbcTemplate.queryForObject(
            String.format(
                "select cmd_status from binlog_polarx_command where cmd_id = '%s' and cmd_type = '%s'",
                commandId, PolarxCommandType.CDC_START.name()),
            Integer.class);

        if (status == 0) {
            return false;
        } else if (status == 1) {
            return true;
        } else {
            throw new PolardbxException("CDC_START command is failed.");
        }
    }

    @Override
    public String toString() {
        return "CDC Start command : " + commandId;
    }
}
