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
package com.aliyun.polardbx.rpl.extractor.full;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class RowChangeHelper {

    public static MapData convertEvent2Map(DBMSRowChange event) {
        return convertEvent2Map(event, Boolean.FALSE);
    }

    /**
     * 将DBMSRowChange 转换成K-V 的MAP形式
     *
     * @param event DBMSRowChange
     * @param upperCase 是否将dbname,table,column的名称转换成大写
     * @return MapData 对象
     */
    public static MapData convertEvent2Map(DBMSRowChange event, boolean upperCase) {
        // 获取数据的描述信息
        String dbName = upperCase ? StringUtils.upperCase(event.getSchema()) : StringUtils.lowerCase(event.getSchema());
        String tableName =
            upperCase ? StringUtils.upperCase(event.getTable()) : StringUtils.lowerCase(event.getTable());
        DBMSAction action = event.getAction();

        // 创建MapData
        MapData mapData = new MapData(dbName, tableName, action);

        // 设置 primaryKey到DataInfo描述对象中
        List<DBMSColumn> primaryKeyColumns = event.getPrimaryKey();
        List<String> primaryColumnNames = new ArrayList<String>(primaryKeyColumns.size());
        for (DBMSColumn primaryKey : primaryKeyColumns) {
            String primaryName =
                upperCase ? StringUtils.upperCase(primaryKey.getName()) : StringUtils.lowerCase(primaryKey.getName());
            primaryColumnNames.add(primaryName);
        }
        mapData.setPrimaryKeyNames(primaryColumnNames);

        // 如果是update设置changeColumnNames到DataInfo描述对象中
        if (DBMSAction.UPDATE == action) {
            List<? extends DBMSColumn> changeColumns = event.getChangeColumns();
            List<String> changeColumnNames = new ArrayList<String>(changeColumns.size());
            for (DBMSColumn changeKey : changeColumns) {
                String changeColumnName =
                    upperCase ? StringUtils.upperCase(changeKey.getName()) : StringUtils.lowerCase(changeKey.getName());
                changeColumnNames.add(changeColumnName);
            }
            mapData.setModifiedFieldNames(changeColumnNames);
        }

        int rowSize = event.getRowSize();
        List<? extends DBMSColumn> allColumns = event.getColumns();
        if (DBMSAction.UPDATE == action) {
            // 获取变化的Column列表
            List<? extends DBMSColumn> modifyColumns = event.getChangeColumns();
            // 准备调用Dispatcher使用的修改前数据列表，和修改后数据列表
            List<Map<String, Serializable>> rowDataMaps = new ArrayList<Map<String, Serializable>>(rowSize);
            List<Map<String, Serializable>> modifyRowDataMaps = new ArrayList<Map<String, Serializable>>(rowSize);
            for (int i = 1; i <= rowSize; i++) {
                Map<String, Serializable> rowDataMap = new HashMap<String, Serializable>(allColumns.size(), 1.0f);
                Map<String, Serializable> modifyRowDataMap = new HashMap<String, Serializable>(allColumns.size(), 1.0f);
                DBMSRowData rowData = event.getRowData(i);
                for (DBMSColumn column : allColumns) {
                    String columnName =
                        upperCase ? StringUtils.upperCase(column.getName()) : StringUtils.lowerCase(column.getName());
                    Serializable columnValue = rowData.getRowValue(column);
                    // 设置修改前rowDataMap
                    rowDataMap.put(columnName, columnValue);
                    if (modifyColumns.contains(column)) {
                        // 如果当前字段是修改的字段，使用修改的值
                        Serializable modifyColumnValue = event.getChangeValue(i, column);
                        modifyRowDataMap.put(columnName, modifyColumnValue);
                    }
                }
                rowDataMaps.add(rowDataMap);
                modifyRowDataMaps.add(modifyRowDataMap);
            }
            mapData.setRowDataMaps(rowDataMaps);
            mapData.setModifyRowDataMaps(modifyRowDataMaps);
        } else if (DBMSAction.INSERT == action || DBMSAction.DELETE == action) {
            List<Map<String, Serializable>> rowDataMaps = new ArrayList<Map<String, Serializable>>(rowSize);
            for (int i = 1; i <= rowSize; i++) {
                Map<String, Serializable> rowDataMap = new HashMap<String, Serializable>(allColumns.size(), 1.0f);
                DBMSRowData rowData = event.getRowData(i);
                for (DBMSColumn column : allColumns) {
                    String columnName =
                        upperCase ? StringUtils.upperCase(column.getName()) : StringUtils.lowerCase(column.getName());
                    Serializable columnValue = rowData.getRowValue(column);
                    rowDataMap.put(columnName, columnValue);
                }
                rowDataMaps.add(rowDataMap);
            }
            mapData.setRowDataMaps(rowDataMaps);
        }
        // options
        List<? extends DBMSOption> optionLists = event.getOptions();
        if (null != optionLists && !optionLists.isEmpty()) {
            Map<String, Serializable> optionMaps = new HashMap<String, Serializable>(optionLists.size(), 1.0f);
            for (DBMSOption dbmsOption : optionLists) {
                optionMaps.put(upperCase ? StringUtils.upperCase(dbmsOption.getName()) :
                        StringUtils.lowerCase(dbmsOption.getName()),
                    dbmsOption.getValue());
            }
            mapData.setOptionMaps(optionMaps);
        }
        return mapData;
    }

    public static DBMSRowChange removeRowData(DBMSRowChange event, RemoveCondition condition) {
        if (null == event) {
            return null;
        }
        int rowCount = event.getRowSize();
        for (int i = rowCount; i > 0; i--) {
            DBMSRowData rowData = event.getRowData(i);
            if (condition.isRemove(event, rowData)) {
                event.removeRowData(i);
            }
        }
        if (event.getRowSize() == 0) {
            return null;
        } else {
            return event;
        }
    }

    public static interface RemoveCondition {

        public boolean isRemove(DBMSRowChange event, DBMSRowData rowData);
    }

    public static class MapData implements Serializable {

        private static final long serialVersionUID = 5457745807246773498L;

        public MapData() {
        }

        /**
         * 数据库名
         */
        private String dbName;
        /**
         * 表名
         */
        private String tableName;

        /**
         * 主键名称
         */
        private List<String> primaryKeyNames;
        /**
         * 修改后字段名称，只有Update的时候才有
         */
        private List<String> modifiedFieldNames;

        /**
         * INSERT，UPDATE，DELETE 对应的枚举类
         */
        private DBMSAction action;

        /**
         * 修改前数据，Insert,Update,Delete时都有
         */
        private List<Map<String, Serializable>> rowDataMaps;

        /**
         * 修改后数据,只有Update的时候才有
         */
        private List<Map<String, Serializable>> modifyRowDataMaps;

        /**
         * EVENT消息扩展字段
         */
        private Map<String, Serializable> optionMaps = Collections.emptyMap();

        public MapData(String dbName, String tableName, DBMSAction action) {
            this.dbName = dbName;
            this.tableName = tableName;
            this.action = action;
        }

        public String getDbName() {
            return dbName;
        }

        public void setDbName(String dbName) {
            this.dbName = dbName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public void setAction(DBMSAction action) {
            this.action = action;
        }

        public String getTableName() {
            return tableName;
        }

        public List<String> getPrimaryKeyNames() {
            return primaryKeyNames;
        }

        public void setPrimaryKeyNames(List<String> primaryKeyNames) {
            this.primaryKeyNames = primaryKeyNames;
        }

        public List<String> getModifiedFieldNames() {
            return modifiedFieldNames;
        }

        public void setModifiedFieldNames(List<String> modifiedFieldNames) {
            this.modifiedFieldNames = modifiedFieldNames;
        }

        public DBMSAction getAction() {
            return action;
        }

        public List<Map<String, Serializable>> getRowDataMaps() {
            return rowDataMaps;
        }

        public void setRowDataMaps(List<Map<String, Serializable>> rowDataMaps) {
            this.rowDataMaps = rowDataMaps;
        }

        public List<Map<String, Serializable>> getModifyRowDataMaps() {
            return modifyRowDataMaps;
        }

        public void setModifyRowDataMaps(List<Map<String, Serializable>> modifyRowDataMaps) {
            this.modifyRowDataMaps = modifyRowDataMaps;
        }

        public Map<String, Serializable> getOptionMaps() {
            return optionMaps;
        }

        public void setOptionMaps(Map<String, Serializable> optionMaps) {
            this.optionMaps = optionMaps;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((action == null) ? 0 : action.hashCode());
            result = prime * result + ((dbName == null) ? 0 : dbName.hashCode());
            result = prime * result + ((modifiedFieldNames == null) ? 0 : modifiedFieldNames.hashCode());
            result = prime * result + ((modifyRowDataMaps == null) ? 0 : modifyRowDataMaps.hashCode());
            result = prime * result + ((optionMaps == null) ? 0 : optionMaps.hashCode());
            result = prime * result + ((primaryKeyNames == null) ? 0 : primaryKeyNames.hashCode());
            result = prime * result + ((rowDataMaps == null) ? 0 : rowDataMaps.hashCode());
            result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MapData other = (MapData) obj;
            if (action != other.action) {
                return false;
            }
            if (dbName == null) {
                if (other.dbName != null) {
                    return false;
                }
            } else if (!dbName.equals(other.dbName)) {
                return false;
            }
            if (modifiedFieldNames == null) {
                if (other.modifiedFieldNames != null) {
                    return false;
                }
            } else if (!modifiedFieldNames.equals(other.modifiedFieldNames)) {
                return false;
            }
            if (modifyRowDataMaps == null) {
                if (other.modifyRowDataMaps != null) {
                    return false;
                }
            } else if (!modifyRowDataMaps.equals(other.modifyRowDataMaps)) {
                return false;
            }
            if (optionMaps == null) {
                if (other.optionMaps != null) {
                    return false;
                }
            } else if (!optionMaps.equals(other.optionMaps)) {
                return false;
            }
            if (primaryKeyNames == null) {
                if (other.primaryKeyNames != null) {
                    return false;
                }
            } else if (!primaryKeyNames.equals(other.primaryKeyNames)) {
                return false;
            }
            if (rowDataMaps == null) {
                if (other.rowDataMaps != null) {
                    return false;
                }
            } else if (!rowDataMaps.equals(other.rowDataMaps)) {
                return false;
            }
            if (tableName == null) {
                if (other.tableName != null) {
                    return false;
                }
            } else if (!tableName.equals(other.tableName)) {
                return false;
            }
            return true;
        }

        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }
    }
}
