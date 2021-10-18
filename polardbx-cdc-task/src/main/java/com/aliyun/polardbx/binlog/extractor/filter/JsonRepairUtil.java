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

package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_OPEN_TOPOLOGY_REPAIR;

/**
 * Created by ziyang.lb，
 **/
@Slf4j
public class JsonRepairUtil {

    //特殊情况，如果收到的json信息有问题，可以调用此方法进行修复
    public static TopologyRecord repair(DDLRecord ddlRecord) {
        if ("CREATE_TABLE".equals(ddlRecord.getSqlKind())) {
            JdbcTemplate polarxTemplate = SpringContextHolder.getObject("polarxJdbcTemplate");
            JdbcTemplate metaJdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");

            // 获取大小写配置
            // 该参数的介绍参见：https://dev.mysql.com/doc/refman/5.7/en/server-system-variables.html#sysvar_lower_case_table_names
            String lowerCaseFlag = polarxTemplate.queryForObject("select @@lower_case_table_names", String.class);

            LogicMetaTopology.LogicTableMetaTopology tableTopology = new LogicMetaTopology.LogicTableMetaTopology();
            tableTopology.setTableName(ddlRecord.getTableName());

            //phy schemas
            tableTopology.setPhySchemas(new ArrayList<>());

            Map<String, List<Entry>> groupTables = polarxTemplate.queryForList(
                String.format("show topology from `%s`.`%s`", ddlRecord.getSchemaName(), ddlRecord.getTableName()))
                .stream().map(m -> new Entry(m.get("GROUP_NAME").toString(),
                    translate(m.get("TABLE_NAME").toString(), lowerCaseFlag)))
                .collect(Collectors.groupingBy(Entry::getGroupName));

            Map<String, List<EntryDetail>> groupDetails = metaJdbcTemplate.queryForList(
                String.format(
                    "select g.group_name,g.phy_db_name,d.storage_inst_id from db_group_info g,group_detail_info d where "
                        + "g.db_name=d.db_name and g.group_name = d.group_name and g.db_name = '%s'",
                    ddlRecord.getSchemaName()))
                .stream().map(
                    i -> new EntryDetail(i.get("group_name").toString(), i.get("storage_inst_id").toString(),
                        i.get("phy_db_name").toString())).collect(Collectors.groupingBy(EntryDetail::getGroupName));

            groupTables.forEach((k, v) -> {
                LogicMetaTopology.PhyTableTopology ptt = new LogicMetaTopology.PhyTableTopology();
                ptt.setGroup(k);
                ptt.setSchema(translate(groupDetails.get(k).get(0).phySchema, lowerCaseFlag));
                ptt.setStorageInstId(groupDetails.get(k).get(0).getStorageInstId());
                ptt.setPhyTables(v.stream().map(Entry::getTableName).collect(Collectors.toList()));
                tableTopology.getPhySchemas().add(ptt);
            });

            // create sql
            String createSql = polarxTemplate.queryForList(
                String.format("show create table  `%s`.`%s`", ddlRecord.getSchemaName(), ddlRecord.getTableName()))
                .stream().map(i -> i.get("Create Table").toString()).collect(Collectors.toList()).get(0);
            tableTopology.setCreateSql(createSql);

            // table type
            String tableType = metaJdbcTemplate.queryForObject(
                String.format("select table_type from tables_ext where table_schema='%s' and table_name='%s'",
                    ddlRecord.getSchemaName(),
                    ddlRecord.getTableName()), String.class);

            String tableCollation = metaJdbcTemplate.queryForObject(
                String.format("select table_collation from tables where table_schema='%s' and table_name='%s'",
                    ddlRecord.getSchemaName(),
                    ddlRecord.getTableName()), String.class);
            tableTopology.setTableType(Integer.parseInt(tableType));
            tableTopology.setTableCollation(tableCollation);

            log.info("repair meta info is :" + new Gson().toJson(tableTopology));

            if (!DynamicApplicationConfig.getBoolean(META_OPEN_TOPOLOGY_REPAIR)) {
                return null;
            }

            TopologyRecord topologyRecord = new TopologyRecord();
            topologyRecord.setLogicTableMeta(tableTopology);
            ddlRecord.setMetaInfo(new Gson().toJson(topologyRecord));
            return topologyRecord;
        }
        return null;
    }

    private static String translate(String input, String flag) {
        if ("0".equals(flag) || "2".equals(flag)) {
            return input;
        } else {
            return input.toLowerCase();
        }
    }

    public static class Entry {
        private String groupName;
        private String tableName;

        public Entry(String groupName, String tableName) {
            this.groupName = groupName;
            this.tableName = tableName;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public static class EntryDetail {
        private String groupName;
        private String storageInstId;
        private String phySchema;

        public EntryDetail(String groupName, String storageInstId, String phySchema) {
            this.groupName = groupName;
            this.storageInstId = storageInstId;
            this.phySchema = phySchema;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getStorageInstId() {
            return storageInstId;
        }

        public void setStorageInstId(String storageInstId) {
            this.storageInstId = storageInstId;
        }

        public String getPhySchema() {
            return phySchema;
        }

        public void setPhySchema(String phySchema) {
            this.phySchema = phySchema;
        }
    }
}
