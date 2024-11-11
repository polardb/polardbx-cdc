/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.alibaba.druid.util.StringUtils;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPolarxCommand;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;

/**
 * 元数据版本兼容性处理
 * <p>
 * created by ziyang.lb
 **/
public class TableCompatibilityProcessorWithTask {

    public static void process() {
        String clusterType = DynamicApplicationConfig.getClusterType();
        if (StringUtils.equals(clusterType, ClusterType.BINLOG.name())) {
            processBinlogPolarxCommandTable();
        } else if (StringUtils.equals(clusterType, ClusterType.BINLOG_X.name())) {
            processBinlogPolarxCommandTable();
        }
    }

    private static void processBinlogPolarxCommandTable() {
        TransactionTemplate template = getObject("metaTransactionTemplate");
        JdbcTemplate metaJdbcTemplate = getObject("metaJdbcTemplate");
        BinlogPolarxCommandMapper polarxCommandMapper = getObject(BinlogPolarxCommandMapper.class);
        template.execute(status -> {
            List<BinlogPolarxCommand> unSuccessCommandList = polarxCommandMapper.select(s -> s.where(
                BinlogPolarxCommandDynamicSqlSupport.cmdStatus, SqlBuilder.isNotEqualTo(1L)).and(
                BinlogPolarxCommandDynamicSqlSupport.cmdType, SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())
            ));
            if (!unSuccessCommandList.isEmpty()) {
                // 如果有当前未启动成功的集群，暂时不进行订正
                return false;
            }
            // 订正数据
            List<BinlogPolarxCommand> commandList = polarxCommandMapper.select(s -> s.where(
                    BinlogPolarxCommandDynamicSqlSupport.cmdType, SqlBuilder.isEqualTo(PolarxCommandType.CDC_START.name())).
                and(BinlogPolarxCommandDynamicSqlSupport.clusterId, SqlBuilder.isNotNull()).
                and(BinlogPolarxCommandDynamicSqlSupport.clusterId, SqlBuilder.isNotEqualTo("0")));
            for (BinlogPolarxCommand cmd : commandList) {
                if (!cmd.getCmdId().contains(":")) {
                    cmd.setCmdId(cmd.getClusterId() + ":" + cmd.getCmdId());
                    polarxCommandMapper.updateByPrimaryKey(cmd);
                }
            }
            // 修改索引
            Set<String> dbIndexColumnSet =
                metaJdbcTemplate.queryForList("show index from `binlog_polarx_command`").stream()
                    .filter(e -> org.apache.commons.lang3.StringUtils.equals(e.get("Key_name").toString(),
                        "uk_cmd_id_type"))
                    .map(e -> org.apache.commons.lang3.StringUtils.lowerCase(String.valueOf(e.get("Column_name"))))
                    .collect(
                        Collectors.toSet());

            if (dbIndexColumnSet.size() == 3) {
                // modify uk_cmd_id_type(id , type , cluster_id) to  uk_cmd_id_type(id , type)
                metaJdbcTemplate.execute("alter table binlog_polarx_command drop index uk_cmd_id_type");
                metaJdbcTemplate.execute(
                    "alter table binlog_polarx_command add unique index uk_cmd_id_type(cmd_id, cmd_type)");
            }
            return true;
        });
    }

}
