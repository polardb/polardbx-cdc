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
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RowChangeBuilder {

    private DBMSAction action;

    private String schema;                                                            // 库名

    private String table;                                                             // 表名

    private final List<DBMSColumn> metaColumns = new ArrayList<DBMSColumn>();

    private final List<DBMSOption> options = new ArrayList<DBMSOption>();

    private final List<Map<String, Serializable>> rowDatas = new ArrayList<Map<String, Serializable>>();

    private final List<Map<String, Serializable>> changeRowDatas = new ArrayList<Map<String, Serializable>>();

    /**
     * 字段动态增加的插件的映射map，主要用于支持过滤代码中
     */
    protected Map<String, String> dynamicPluginMap = new HashMap<>();

    /**
     * 保留字段和分隔符的关系
     */
    protected Map<String, String> separatorMap = new HashMap<>();

    private static final int DEFAULT_ORDINAL_INDEX = -1;
    private static final boolean DEFAULT_SIGNED = true;
    private static final boolean DEFAULT_NULLABLE = true;
    private static final boolean DEFAULT_PRIMARYKEY = false;
    private static final boolean DEFAULT_UNIQUEKEY = false;

    private int currentFieldIndex = 1;

    private RowChangeBuilder(String schema, String table, DBMSAction action) {
        this.schema = StringUtils.lowerCase(schema);
        this.table = StringUtils.lowerCase(table);
        this.action = action;
    }

    public static RowChangeBuilder createBuilder(String schema, String table, DBMSAction action) {
        return new RowChangeBuilder(schema, table, action);
    }

    // for inc copy
    public static RowChangeBuilder createFromEvent(DBMSRowChange event) {
        // 转换成MAP结构数据
        RowChangeHelper.MapData mapData = RowChangeHelper.convertEvent2Map(event);
        RowChangeBuilder builder = new RowChangeBuilder(mapData.getDbName(),
            mapData.getTableName(),
            mapData.getAction());
        // 获得表头
        List<? extends DBMSColumn> oMetaDatas = event.getColumns();
        List<DBMSColumn> nMetaDatas = new ArrayList<DBMSColumn>(oMetaDatas.size());
        // copy old metaData
        nMetaDatas.addAll(oMetaDatas);
        builder.addMetaColumns(nMetaDatas);
        // 填充变化前数据
        builder.addRowDatas(mapData.getRowDataMaps());
        // 填充变化后的发数据
        builder.addChangeRowDatas(mapData.getModifyRowDataMaps());
        // 填充OPTIONS
        builder.addOptions(event.getOptions());

        // 填充插件相关信息
        builder.setSeparatorMap(((DefaultRowChange) event).getSeparatorMap());
        builder.setDynamicPluginMap(((DefaultRowChange) event).getDynamicPluginMap());

        return builder;
    }
//
//    // for full copy
//    public RowChangeBuilder getDataFromQueryResult(TableInfo tableInfo, ResultSet resultSet) throws Exception{
//        Map<String, Serializable> fieldValueMap = new HashMap<>(tableInfo.getColumns().size());
//        for (ColumnInfo column : tableInfo.getColumns()) {
//            Object value = ExtractorUtil.getColumnValue(resultSet, column.getName(), column.getType());
//            fieldValueMap.put(column.getName(), (Serializable) value);
//        }
//        getRowDatas().clear();
//        addRowData(fieldValueMap);
//        return this;
//    }

    public RowChangeBuilder excludeColumn(String columnName) {
        columnName = StringUtils.lowerCase(columnName);
        if (!isContainColumn(columnName)) {
            // need throw exception here
            return this;
        }
        removeMetaColumn(columnName);
        if (rowDatas.isEmpty()) {
            return this;
        }
        if (DBMSAction.UPDATE.equals(getAction())) {
            //rowData和changeRowData需要一起过滤
            getChangeRowData(0).remove(columnName);
            getRowData(0).remove(columnName);
        } else {
            getRowData(0).remove(columnName);
        }
        return this;
    }

    public boolean isContainColumn(String columnName) {
        for (DBMSColumn column : metaColumns) {
            if (column.getName().equals(columnName)) {
                return true;
            }
        }
        return false;
    }

    public synchronized DBMSRowChange build() throws Exception {
        // 必要参数检查
        if (StringUtils.isBlank(this.schema)) {
            throw new Exception("schema is empty!");
        }
        if (StringUtils.isBlank(this.table)) {
            throw new Exception("table is empty!");
        }
        if (null == this.action) {
            throw new Exception("action is null!");
        }
        if (this.metaColumns.isEmpty()) {
            throw new Exception("metaColumns is empty!");
        }
        if (this.rowDatas.isEmpty()) {
            throw new Exception("rowData is empty!");
        }
        // 获取所有字段名称，用来当索引判断字段是否存在
        Set<String> metaColumnNames = new HashSet<String>(metaColumns.size());
        for (DBMSColumn column : metaColumns) {
            metaColumnNames.add(StringUtils.lowerCase(column.getName()));
        }
        // 构建columnSet
        DBMSColumnSet dbmsColumnSet = new DefaultColumnSet(metaColumns);
        DBMSRowChange rowChange = new DefaultRowChange(action, schema, table, dbmsColumnSet);
        // 获取总记录数
        int rowCount = this.rowDatas.size();
        // 填充Row数据
        for (int i = 0; i < rowCount; i++) {
            Map<String, Serializable> dataMap = this.rowDatas.get(i);
            if (metaColumnNames.size() != dataMap.size()) {
                throw new Exception("rowData column count not match meta column count, metaColumnNames : "
                    + metaColumnNames + ", dataColumnNames : " + dataMap.keySet());
            }
            // 全行数据
            for (Map.Entry<String, Serializable> entry : dataMap.entrySet()) {
                String columnName = StringUtils.lowerCase(entry.getKey());
                if (!metaColumnNames.contains(columnName)) {
                    throw new Exception("metaColumns not contains rowData column: " + columnName);
                }
                rowChange.setRowValue(i + 1, columnName, entry.getValue());
            }
            if (DBMSAction.UPDATE == action) {
                if (!this.changeRowDatas.isEmpty()) {
                    if (this.rowDatas.size() != this.changeRowDatas.size()) {
                        throw new Exception("update  changeRowData Count not Match rowData Count!");
                    }
                    // 填充RowChange数据
                    Set<String> changeColumnNames = new HashSet<String>(metaColumns.size());
                    Map<String, Serializable> changeDataMap = this.changeRowDatas.get(i);
                    changeColumnNames.addAll(changeDataMap.keySet());

                    for (Map.Entry<String, Serializable> entry : changeDataMap.entrySet()) {
                        String columnName = StringUtils.lowerCase(entry.getKey());
                        if (!metaColumnNames.contains(columnName)) {
                            throw new Exception("metaColumns not contains rowChangeData column: " + columnName);
                        }
                        rowChange.setChangeValue(i + 1, columnName, entry.getValue());
                        changeColumnNames.add(columnName);
                    }
                }
            }
        }
        // 填充 options
        for (DBMSOption option : this.options) {
            rowChange.setOptionValue(option.getName(), option.getValue());
        }
        // 填充插件信息
        ((DefaultRowChange) rowChange).setDynamicPluginMap(dynamicPluginMap);
        ((DefaultRowChange) rowChange).setSeparatorMap(separatorMap);
        return rowChange;
    }

    public void addOptions(List<? extends DBMSOption> list) {
        if (null != list && !list.isEmpty()) {
            this.options.addAll(list);
        }
    }

    public void addOption(DBMSOption option) {
        this.options.add(option);
    }

    public void addOption(String name, Serializable value) {
        this.options.add(new DefaultOption(name, value));
    }

    public void addRowDatas(List<Map<String, Serializable>> rowDatas) {
        if (null != rowDatas && !rowDatas.isEmpty()) {
            this.rowDatas.addAll(rowDatas);
        }
    }

    public void addChangeRowDatas(List<Map<String, Serializable>> changeRowDatas) {
        if (null != changeRowDatas && !changeRowDatas.isEmpty()) {
            this.changeRowDatas.addAll(changeRowDatas);
        }
    }

    public void addRowData(Map<String, Serializable> rowData) {
        if (null != rowData && !rowData.isEmpty()) {
            this.rowDatas.add(rowData);
        }
    }

    public void addChangeRowData(Map<String, Serializable> changeRowData) {
        if (null != changeRowData && !changeRowData.isEmpty()) {
            this.changeRowDatas.add(changeRowData);
        }
    }

    public void addMetaColumns(Collection<DBMSColumn> metaColumns) {
        if (null != metaColumns && !metaColumns.isEmpty()) {
            List<DBMSColumn> newColumns = new ArrayList<DBMSColumn>(metaColumns.size());
            for (DBMSColumn column : metaColumns) {
                newColumns.add(cloneDBMSColumn(column));
            }
            this.metaColumns.addAll(newColumns);
        }
    }

    public void addMetaColumn(DBMSColumn metaColumn) {
        this.metaColumns.add(this.cloneDBMSColumn(metaColumn));
    }

    public void addMetaColumnWithoutClone(DBMSColumn metaColumn) {
        this.metaColumns.add(metaColumn);
    }

    /**
     * 删除row change event中的列，给opensearch一些字段展开功能使用
     */
    public void removeMetaColumn(String name) {
        for (int i = 0; i < this.metaColumns.size(); i++) {
            if (metaColumns.get(i).getName().equals(name)) {
                metaColumns.remove(i);
                break;
            }
        }
    }

    public void addMetaColumn(String name, Class<?> valueClass) {
        this.addMetaColumn(name, getSqyTypeByClass(valueClass));
    }

    public void addMetaColumn(String name, Class<?> valueClass, boolean primaryKey) {

        this.addMetaColumn(name, getSqyTypeByClass(valueClass), primaryKey);
    }

    public void addMetaColumn(String name, Class<?> valueClass, boolean nullable, boolean primaryKey) {
        this.addMetaColumn(name, getSqyTypeByClass(valueClass), DEFAULT_SIGNED, nullable, primaryKey);
    }

    public void addMetaColumn(String name, Class<?> valueClass, boolean signed, boolean nullable, boolean primaryKey) {
        this.addMetaColumn(name, getSqyTypeByClass(valueClass), signed, nullable, primaryKey);
    }

    public void addMetaColumn(String name, Class<?> valueClass, int ordinalIndex, boolean signed, boolean nullable,
                              boolean primaryKey) {
        this.addMetaColumn(name,
            ordinalIndex,
            getSqyTypeByClass(valueClass),
            signed,
            nullable,
            primaryKey,
            DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, Class<?> valueClass, int ordinalIndex, boolean signed, boolean nullable,
                              boolean primaryKey, boolean uniqueKey) {
        this.addMetaColumn(name, ordinalIndex, getSqyTypeByClass(valueClass), signed, nullable, primaryKey, uniqueKey);
    }

    public void addMetaColumn(String name, int sqlType) {
        this.addMetaColumn(name,
            currentFieldIndex++,
            sqlType,
            DEFAULT_SIGNED,
            DEFAULT_NULLABLE,
            DEFAULT_PRIMARYKEY,
            DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, int sqlType, boolean primaryKey) {
        this.addMetaColumn(name,
            currentFieldIndex++,
            sqlType,
            DEFAULT_SIGNED,
            DEFAULT_NULLABLE,
            primaryKey,
            DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, int sqlType, boolean nullable, boolean primaryKey) {
        this.addMetaColumn(name, currentFieldIndex++, sqlType, DEFAULT_SIGNED, nullable, primaryKey, DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, int sqlType, boolean signed, boolean nullable, boolean primaryKey) {
        this.addMetaColumn(name, currentFieldIndex++, sqlType, signed, nullable, primaryKey, DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, int sqlType, boolean signed, boolean nullable, boolean primaryKey,
                              boolean uniqueKey) {
        this.addMetaColumn(name, currentFieldIndex++, sqlType, signed, nullable, primaryKey, uniqueKey);
    }

    public void addMetaColumn(String name, int ordinalIndex, int sqlType, boolean nullable, boolean primaryKey) {
        this.addMetaColumn(name, ordinalIndex, sqlType, DEFAULT_SIGNED, nullable, primaryKey, DEFAULT_UNIQUEKEY);
    }

    public void addMetaColumn(String name, int ordinalIndex, int sqlType, boolean signed, boolean nullable,
                              boolean primaryKey, boolean uniqueKey) {
        this.metaColumns.add(createMetaColumn(name, ordinalIndex, sqlType, signed, nullable, primaryKey, uniqueKey));
    }

    public Map<String, String> getDynamicPluginMap() {
        return dynamicPluginMap;
    }

    public void setDynamicPluginMap(Map<String, String> dynamicPluginMap) {
        this.dynamicPluginMap = dynamicPluginMap;
    }

    public Map<String, String> getSeparatorMap() {
        return separatorMap;
    }

    public void setSeparatorMap(Map<String, String> separatorMap) {
        this.separatorMap = separatorMap;
    }

    public static DBMSColumn createMetaColumn(String name, int sqlType) {
        return createMetaColumn(name,
            DEFAULT_ORDINAL_INDEX,
            sqlType,
            DEFAULT_SIGNED,
            DEFAULT_NULLABLE,
            DEFAULT_PRIMARYKEY);
    }

    public static DBMSColumn createMetaColumn(String name, int sqlType, boolean primaryKey) {
        return createMetaColumn(name, DEFAULT_ORDINAL_INDEX, sqlType, DEFAULT_SIGNED, DEFAULT_NULLABLE, primaryKey);
    }

    public static DBMSColumn createMetaColumn(String name, int sqlType, boolean nullable, boolean primaryKey) {
        return createMetaColumn(name, DEFAULT_ORDINAL_INDEX, sqlType, DEFAULT_SIGNED, nullable, primaryKey);
    }

    public static DBMSColumn createMetaColumn(String name, int ordinalIndex, int sqlType, boolean nullable,
                                              boolean primaryKey) {
        return createMetaColumn(name, ordinalIndex, sqlType, DEFAULT_SIGNED, nullable, primaryKey);
    }

    public static DBMSColumn createMetaColumn(String name, int sqlType, boolean signed, boolean nullable,
                                              boolean primaryKey) {
        return createMetaColumn(name, DEFAULT_ORDINAL_INDEX, sqlType, signed, nullable, primaryKey);
    }

    public static DBMSColumn createMetaColumn(String name, int ordinalIndex, int sqlType, boolean signed,
                                              boolean nullable, boolean primaryKey) {
        return createMetaColumn(name, ordinalIndex, sqlType, signed, nullable, primaryKey, DEFAULT_UNIQUEKEY);
    }

    public static DBMSColumn createMetaColumn(String name, int ordinalIndex, int sqlType, boolean signed,
                                              boolean nullable, boolean primaryKey, boolean uniqueKey) {
        DBMSColumn newColumn = new DefaultColumn(StringUtils
            .lowerCase(name), ordinalIndex, sqlType, signed, nullable, primaryKey, uniqueKey);
        return newColumn;
    }

    /**
     * 根据value的class和signed获取对应的sqlType
     * 参考mysql驱动com.mysql.jdbc.ResultSetMetaData.getClassNameForJavaType(int,
     * boolean, int, boolean, boolean)
     */
    public static int getSqyTypeByClass(Class<?> valueClass) {
        if (null == valueClass) {
            return Types.NULL;
        }
        // 记录数组标记
        boolean isArray = valueClass.isArray();
        // 如果是数组取数组内class,否则用原先的class
        valueClass = isArray ? valueClass.getComponentType() : valueClass;
        // 如果是小的基本类型，则转换成大的包装类型
        valueClass = valueClass.isPrimitive() ? ClassUtils.primitiveToWrapper(valueClass) : valueClass;
        if (isArray) {
            if (Byte.class.equals(valueClass)) {
                return Types.LONGVARBINARY;
            }
        } else {
            if (valueClass.isPrimitive()) {
                valueClass = ClassUtils.primitiveToWrapper(valueClass);
            }
            if (Boolean.class.equals(valueClass)) {
                return Types.BOOLEAN;
            } else if (Integer.class.equals(valueClass)) {
                return Types.INTEGER;
            } else if (Long.class.equals(valueClass)) {
                return Types.BIGINT;
            } else if (BigDecimal.class.equals(valueClass)) {
                return Types.DECIMAL;
            } else if (Float.class.equals(valueClass)) {
                return Types.REAL;
            } else if (Double.class.equals(valueClass)) {
                return Types.DOUBLE;
            } else if (String.class.equals(valueClass)) {
                return Types.VARCHAR;
            } else if (java.sql.Date.class.equals(valueClass) || java.util.Date.class.equals(valueClass)) {
                return Types.DATE;
            } else if (java.sql.Time.class.equals(valueClass)) {
                return Types.TIME;
            } else if (java.sql.Timestamp.class.equals(valueClass)) {
                return Types.TIMESTAMP;
            } else if (java.sql.Blob.class.equals(valueClass)) {
                return Types.BINARY;
            }
        }
        throw new RuntimeException("not support java Type class: " + valueClass.getName());
    }

    /**
     * 根据sqlType返回相应的valueClass，用于EtlBuilder中addColumn、renameColumn等地方
     *
     * @return 只会返回泛型的String或者Blob
     */
    public Class<?> getGeneralValueTypeBySqlType(int sqlType) {
        if (sqlType == Types.BLOB || sqlType == Types.BINARY || sqlType == Types.VARBINARY
            || sqlType == Types.LONGVARBINARY) {
            return Blob.class;
        } else {
            return String.class;
        }
    }

    /**
     * 复制一份DBMSColumn 要是DBMSColumn实现cloneable就好了
     *
     * @return 复制后 <code>new</code> 出来的DBMSColumn
     */
    private DBMSColumn cloneDBMSColumn(DBMSColumn dbmsColumn) {
        String name = StringUtils.lowerCase(dbmsColumn.getName());
        int ordinalIndex = dbmsColumn.getOrdinalIndex();
        int sqlType = dbmsColumn.getSqlType();
        boolean signed = dbmsColumn.isSigned();
        boolean nullable = dbmsColumn.isNullable();
        boolean primaryKey = dbmsColumn.isPrimaryKey();
        boolean uniqueKey = dbmsColumn.isUniqueKey();
        boolean generated = dbmsColumn.isGenerated();
        boolean implicitPk = dbmsColumn.isRdsImplicitPk();
        return new DefaultColumn(name, ordinalIndex, sqlType, signed, nullable, primaryKey, uniqueKey,
            generated, implicitPk);
    }

    public DBMSAction getAction() {
        return action;
    }

    public String getSchema() {
        return schema;
    }

    public String getTable() {
        return table;
    }

    public List<DBMSColumn> getMetaColumns() {
        return metaColumns;
    }

    public List<? extends DBMSOption> getOptions() {
        return options;
    }

    public List<Map<String, Serializable>> getRowDatas() {
        return rowDatas;
    }

    public Map<String, Serializable> getRowData(int i) {
        return rowDatas.get(i);
    }

    public List<Map<String, Serializable>> getChangeRowDatas() {
        return changeRowDatas;
    }

    public Map<String, Serializable> getChangeRowData(int i) {
        return changeRowDatas.get(i);
    }

    private static List<Map<String, Serializable>> buildDataWithFilterColumn(Set<String> filterColumns,
                                                                             List<Map<String, Serializable>> rowDataList) {
        if (null == rowDataList || rowDataList.isEmpty()) {
            return rowDataList;
        }
        List<Map<String, Serializable>> newRowDataList = Lists.newArrayListWithExpectedSize(rowDataList.size());
        for (Map<String, Serializable> rowData : rowDataList) {
            Map<String, Serializable> newRowData = Maps.newHashMapWithExpectedSize(rowData.size());
            Set<String> columnNameSets = rowData.keySet();
            for (String columnName : columnNameSets) {
                if (filterColumns.contains(columnName) || filterColumns.contains(columnName.toLowerCase())
                    || filterColumns.contains(columnName.toUpperCase())) {
                    continue;
                } else {
                    newRowData.put(columnName, rowData.get(columnName));
                }
            }
            newRowDataList.add(newRowData);
        }
        return newRowDataList;
    }
}
