/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * created by ziyang.lb
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class LogicMetaTopology {

    private List<LogicDbTopology> logicDbMetas;
    private boolean shared;
    private boolean interned;
    private boolean lowerCased;

    public void add(LogicDbTopology logicDbMeta) {
        logicDbMetas.add(logicDbMeta);
    }

    public LogicMetaTopology copy() {
        LogicMetaTopology obj = new LogicMetaTopology();
        obj.shared = this.shared;
        obj.interned = this.interned;
        obj.lowerCased = this.lowerCased;
        if (this.logicDbMetas != null) {
            List<LogicDbTopology> copyList = new ArrayList<>();
            this.logicDbMetas.forEach(d -> copyList.add(d.copy()));
            obj.logicDbMetas = copyList;
        } else {
            obj.logicDbMetas = new ArrayList<>();
        }
        return obj;
    }

    @Data
    public static class LogicDbTopology {
        private String schema;
        private String charset;
        private List<PhyDbTopology> phySchemas;
        private List<LogicTableMetaTopology> logicTableMetas;

        public LogicDbTopology copy() {
            LogicDbTopology obj = new LogicDbTopology();
            obj.schema = this.schema;
            obj.charset = this.charset;
            if (this.phySchemas != null) {
                List<PhyDbTopology> copyList = new ArrayList<>();
                this.phySchemas.forEach(p -> copyList.add(p.copy()));
                obj.phySchemas = copyList;
            }
            if (this.logicTableMetas != null) {
                List<LogicTableMetaTopology> copyList = new ArrayList<>();
                this.logicTableMetas.forEach(t -> copyList.add(t.copy()));
                obj.logicTableMetas = copyList;
            }
            return obj;
        }
    }

    @Data
    public static class LogicTableMetaTopology {
        private String tableName;
        private String tableCollation;
        private int tableType;
        private String createSql;
        private String createSql4Phy;
        private List<PhyTableTopology> phySchemas;

        public LogicTableMetaTopology copy() {
            LogicTableMetaTopology obj = new LogicTableMetaTopology();
            obj.tableName = this.tableName;
            obj.tableCollation = this.tableCollation;
            obj.tableType = this.tableType;
            obj.createSql = this.createSql;
            obj.createSql4Phy = this.createSql4Phy;
            if (this.phySchemas != null) {
                List<PhyTableTopology> copyList = new ArrayList<>();
                this.phySchemas.forEach(p -> copyList.add(p.copy()));
                obj.phySchemas = copyList;
            } else {
                obj.phySchemas = new ArrayList<>();
            }
            return obj;
        }
    }

    @Data
    public static class PhyDbTopology {
        private String storageInstId;
        private String group;
        private String schema;

        public PhyDbTopology copy() {
            PhyDbTopology obj = new PhyDbTopology();
            obj.storageInstId = this.storageInstId;
            obj.group = this.group;
            obj.schema = this.schema;
            return obj;
        }
    }

    @Data
    public static class PhyTableTopology extends PhyDbTopology {
        private List<String> phyTables;

        @Override
        public PhyTableTopology copy() {
            PhyDbTopology superObj = super.copy();

            PhyTableTopology obj = new PhyTableTopology();
            obj.setStorageInstId(superObj.storageInstId);
            obj.setGroup(superObj.group);
            obj.setSchema(superObj.schema);
            if (this.phyTables != null) {
                obj.phyTables = new ArrayList<>(this.phyTables);
            } else {
                obj.phyTables = new ArrayList<>();
            }
            return obj;
        }
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
