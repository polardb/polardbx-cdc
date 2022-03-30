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

package com.aliyun.polardbx.binlog.cdc.topology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Iterator;
import java.util.List;

/**
 * created by ziyang.lb
 */
@Data
@AllArgsConstructor
@Builder
public class LogicMetaTopology {

    private List<LogicDbTopology> logicDbMetas;

    public void add(LogicDbTopology logicDbMeta) {
        logicDbMetas.add(logicDbMeta);
    }

    @Data
    public static class LogicDbTopology {
        private String schema;
        private String charset;
        private List<PhyDbTopology> phySchemas;
        private List<LogicTableMetaTopology> logicTableMetas;
    }

    @Data
    public static class LogicTableMetaTopology {
        private String tableName;
        private String tableCollation;
        private int tableType;
        private String createSql;
        private String createSql4Phy;
        private List<PhyTableTopology> phySchemas;
    }

    @Data
    public static class PhyDbTopology {
        private String storageInstId;
        private String group;
        private String schema;
    }

    @Data
    public static class PhyTableTopology extends PhyDbTopology {
        private List<String> phyTables;
    }

    public List<LogicDbTopology> getLogicDbMetas() {
        return logicDbMetas;
    }

    public void removeSchema(String schema) {
        Iterator<LogicDbTopology> iterator = this.logicDbMetas.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getSchema().equals(schema)) {
                iterator.remove();
            }
        }
    }

    public void removeTable(String schema, String table) {
        for (LogicDbTopology logicDbMeta : this.logicDbMetas) {
            if (logicDbMeta.getSchema().equals(schema)) {
                Iterator<LogicTableMetaTopology> iterator = logicDbMeta.getLogicTableMetas().iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getTableName().equals(table)) {
                        iterator.remove();
                    }
                }
                break;
            }
        }
    }
}
