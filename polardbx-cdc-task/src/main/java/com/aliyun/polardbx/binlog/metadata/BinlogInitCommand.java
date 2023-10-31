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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.PolarxCommandType;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/**
 * @author yanfenglin
 */
public class BinlogInitCommand implements CdcInitCommand {

    private String commandId;
    private JdbcTemplate metaJdbcTemplate;
    private BinlogPolarxCommandMapper binlogPolarxCommandMapper;

    private Long id;

    public BinlogInitCommand(String commandId) {
        this.commandId = commandId;
        metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        binlogPolarxCommandMapper = SpringContextHolder.getObject(BinlogPolarxCommandMapper.class);
        if (ClusterType.BINLOG.name().equals(DynamicApplicationConfig.getClusterType())) {
            //单流考虑下兼容性
            Integer count = metaJdbcTemplate.queryForObject(
                String.format(
                    "select count(*) from binlog_polarx_command where cmd_id = '%s' and cmd_type = '%s'",
                    "00000000", PolarxCommandType.CDC_START.name()),
                Integer.class);
            if (count != null && count > 0) {
                this.commandId = "00000000";
            }
        }
    }

    @Override
    public boolean exists() {
        return queryStatus() != null;
    }

    @Override
    public void tryStart() {
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        if (id != null) {
            // doUpdate
            String sql =
                String.format("update binlog_polarx_command set gmt_modified = NOW(), cluster_id = '%s' where id = %s",
                    clusterId, id + "");
            metaJdbcTemplate.execute(sql);
            return;
        }
        String sql = String.format(
            "INSERT INTO binlog_polarx_command (cmd_id,cmd_type,cmd_status,cluster_id) values ('%s','%s',0,'%s') ON DUPLICATE KEY UPDATE gmt_modified = NOW()",
            clusterId + ":" + commandId, PolarxCommandType.CDC_START, clusterId);
        metaJdbcTemplate.execute(sql);
    }

    @Override
    public boolean isSuccess() {
        Integer status = queryStatus();

        if (status == null) {
            return false;
        }
        if (status == 1) {
            return true;
        }
        return false;
    }

    public Integer queryStatus() {
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        Optional<BinlogPolarxCommand> polarxCommandOptional = binlogPolarxCommandMapper.selectOne(s -> s.
            where(BinlogPolarxCommandDynamicSqlSupport.cmdId,
                SqlBuilder.isEqualTo(clusterId + ":" + commandId)).
            and(BinlogPolarxCommandDynamicSqlSupport.cmdType,
                SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())));

        if (!polarxCommandOptional.isPresent()) {
            // 兼容一下老版本，携带cluster_id的情况
            polarxCommandOptional = binlogPolarxCommandMapper.selectOne(s -> s.
                where(BinlogPolarxCommandDynamicSqlSupport.cmdId, SqlBuilder.isEqualTo(commandId)).
                and(BinlogPolarxCommandDynamicSqlSupport.cmdType,
                    SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())).
                and(BinlogPolarxCommandDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
        }

        if (!polarxCommandOptional.isPresent()) {
            // 兼容一下daemon没有及时更新clusterId的情况
            polarxCommandOptional = binlogPolarxCommandMapper.selectOne(s -> s.
                where(BinlogPolarxCommandDynamicSqlSupport.cmdId, SqlBuilder.isEqualTo(commandId)).
                and(BinlogPolarxCommandDynamicSqlSupport.cmdType,
                    SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())).
                and(BinlogPolarxCommandDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo("0")));
            if (!polarxCommandOptional.isPresent()) {
                polarxCommandOptional = binlogPolarxCommandMapper.selectOne(s -> s.
                    where(BinlogPolarxCommandDynamicSqlSupport.cmdId, SqlBuilder.isEqualTo(commandId)).
                    and(BinlogPolarxCommandDynamicSqlSupport.cmdType,
                        SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())).
                    and(BinlogPolarxCommandDynamicSqlSupport.clusterId, SqlBuilder.isNull()));
            }
        }

        if (!polarxCommandOptional.isPresent()) {
            this.id = null;
            return null;
        }
        this.id = polarxCommandOptional.get().getId();
        return polarxCommandOptional.get().getCmdStatus().intValue();
    }

    @Override
    public String toString() {
        return "CDC Start command : " + commandId;
    }
}
