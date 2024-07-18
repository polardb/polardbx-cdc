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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * created by ziyang.lb
 */
public class LogicTableMeta {
    private boolean compatible;//是否兼容
    private String logicSchema;
    private String logicTable;
    private String phySchema;
    private String phyTable;
    private List<FieldMetaExt> logicFields = new ArrayList<>();
    private List<FieldMetaExt> pkList = new ArrayList<>();
    private boolean hasHiddenPk = false;

    public void addPk(FieldMetaExt fieldMetaExt) {
        pkList.add(fieldMetaExt);
    }

    public List<FieldMetaExt> getPkList() {
        return pkList;
    }

    public boolean isCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    public boolean isHasHiddenPk() {
        return hasHiddenPk;
    }

    public void setHasHiddenPk(boolean hasHiddenPk) {
        this.hasHiddenPk = hasHiddenPk;
    }

    public String getLogicSchema() {
        return logicSchema;
    }

    public void setLogicSchema(String logicSchema) {
        this.logicSchema = logicSchema;
    }

    public String getLogicTable() {
        return logicTable;
    }

    public void setLogicTable(String logicTable) {
        this.logicTable = logicTable;
    }

    public String getPhySchema() {
        return phySchema;
    }

    public void setPhySchema(String phySchema) {
        this.phySchema = phySchema;
    }

    public String getPhyTable() {
        return phyTable;
    }

    public void setPhyTable(String phyTable) {
        this.phyTable = phyTable;
    }

    public List<FieldMetaExt> getLogicFields() {
        return logicFields;
    }

    public void setLogicFields(List<FieldMetaExt> logicFields) {
        this.logicFields = logicFields;
    }

    public void add(FieldMetaExt metaExt) {
        this.logicFields.add(metaExt);
    }

    @Override
    public String toString() {
        StringBuilder data = new StringBuilder();
        data.append(
            "LogicTableMeta [logicSchema=" + logicSchema + ", logicTable=" + logicTable + ", phySchema=" + phySchema
                + ", phyTable=" + phyTable + ", compatible=" + compatible + ", fileds=");
        for (FieldMetaExt field : logicFields) {
            data.append("\n\t").append(field.toString());
        }
        data.append("\n]");
        return data.toString();
    }

    public static class FieldMetaExt extends FieldMeta {
        private final int logicIndex;
        private final int phyIndex;
        private FieldMeta phyFieldMeta;
        private boolean typeMatch = true;

        public FieldMetaExt(FieldMeta fieldMeta, int logicIndex, int phyIndex) {
            super(fieldMeta.getColumnName(), fieldMeta.getColumnType(), fieldMeta.isNullable(), fieldMeta.isKey(),
                fieldMeta.getDefaultValue(), fieldMeta.isUnique(), fieldMeta.getCharset());
            this.logicIndex = logicIndex;
            this.phyIndex = phyIndex;
        }

        public FieldMeta getPhyFieldMeta() {
            return phyFieldMeta;
        }

        public void setPhyFieldMeta(FieldMeta phyFieldMeta) {
            this.phyFieldMeta = phyFieldMeta;
        }

        public void setTypeNotMatch() {
            this.typeMatch = false;
        }

        public boolean isTypeMatch() {
            return typeMatch;
        }

        public int getLogicIndex() {
            return logicIndex;
        }

        public int getPhyIndex() {
            return phyIndex;
        }

        @Override
        public String toString() {
            return super.toString() + ", FieldMetaExt{" +
                "logicIndex=" + logicIndex +
                ", phyIndex=" + phyIndex +
                ", typeMatch=" + typeMatch +
                '}';
        }
    }

}
