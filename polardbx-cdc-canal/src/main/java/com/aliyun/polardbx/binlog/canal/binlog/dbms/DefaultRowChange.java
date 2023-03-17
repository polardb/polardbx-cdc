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
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class defines a set of one or more row changes. Default implementation of {@link DBMSRowChange DBMSRowChange}
 * For insert and delete operations, we store operation data in an array of {@link DBMSRowData DBMSRowData} and you can
 * get a row in the operation data by method {@link #getRowData(int)} getRowData}, the parameter is the number of the
 * row in the array which starts from 1. We also provide a convenient method {@link #getRowValue(int, int) getRowValue}
 * to get a column of a row. For update operations, we store operation data before and after update in 2 arrays of
 * {@link DBMSRowData DBMSRowData}. You can get data before and after update by method {@link #getRowData(int)}
 * getRowData} and {@link #getChangeData(int)} getChangeData}
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultRowChange extends DBMSRowChange {

    private static final long serialVersionUID = -5752487668003007643L;

    protected DBMSAction action;
    protected String schema;
    protected String table;

    protected List<DBMSRowData> dataSet;
    protected BitSet changeColumnsBitSet;
    protected List<DBMSRowData> changeDataSet;

    protected List<DBMSOption> options;

    /**
     * BitSet changeColumnsBitSet 无法被 FAST Json 正确，此字段作为辅助使用
     */
    protected List<Boolean> dummyChangeColumnsBitSet = new ArrayList<>();

    /**
     * 字段动态增加的插件的映射map，主要用于支持过滤代码中
     */
    protected Map<String, String> dynamicPluginMap = new HashMap<>();

    /**
     * 保留字段和分隔符的关系
     */
    protected Map<String, String> separatorMap = new HashMap<>();

    public DefaultRowChange() {
    }

    /**
     * Crack for DrcRowChange
     */
    public DefaultRowChange(DBMSAction action, String schema, String table) {
        this.action = action;
        this.schema = schema;
        this.table = table;
    }

    /**
     * Create an empty <code>DefaultRowChange</code> object.
     */
    public DefaultRowChange(DBMSAction action, String schema, String table, DBMSColumnSet columnSet) {
        this.columnSet = columnSet;
        this.action = action;
        this.schema = schema;
        this.table = table;
        init();
    }

    /**
     * Create a fullfill <code>DefaultRowChange</code> object.
     */
    public DefaultRowChange(DBMSAction action, String schema, String table, DBMSColumnSet columnSet,
                            List<DBMSRowData> dataSet, List<DBMSOption> options) {
        this.columnSet = columnSet;
        this.action = action;
        this.schema = schema;
        this.table = table;
        this.dataSet = dataSet;
        this.options = options;
        init();
    }

    /**
     * Create a fullfill <code>DefaultRowChange</code> object.
     */
    public DefaultRowChange(DBMSAction action, String schema, String table, DBMSColumnSet columnSet,
                            List<DBMSRowData> dataSet, BitSet changeColumnsBitSet, List<DBMSRowData> changeDataSet,
                            List<DBMSOption> options) {
        this.columnSet = columnSet;
        this.action = action;
        this.schema = schema;
        this.table = table;
        this.dataSet = dataSet;
        this.changeColumnsBitSet = changeColumnsBitSet;
        this.changeDataSet = changeDataSet;
        this.options = options;
        init();
    }

    /**
     * Init data-set if constructor not given.
     */
    protected void init() {
        if (dataSet == null) {
            dataSet = new ArrayList<DBMSRowData>();
        }

        if (DBMSAction.UPDATE == action) {
            if (changeColumnsBitSet == null) {
                changeColumnsBitSet = new BitSet(columnSet.getColumnSize());
            }
            if (changeDataSet == null) {
                changeDataSet = new ArrayList<DBMSRowData>();
            }
        }
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

    /**
     * Return the row-change action.
     */
    public DBMSAction getAction() {
        return action;
    }

    public void setAction(DBMSAction action) {
        this.action = action;
    }

    /**
     * Return the schema of row-change data.
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Change the schema of row-change data.
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Return the table name.
     */
    public String getTable() {
        return table;
    }

    /**
     * Change the table name of row-change data.
     */
    public void setTable(String table) {
        this.table = table;
    }

    /**
     * Return the count of row data.
     */
    public int getRowSize() {
        return dataSet.size();
    }

    /**
     * Return origin row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @return The origin row-data.
     */
    public DBMSRowData getRowData(int rownum) {
        while (rownum > dataSet.size()) {
            DBMSRowData data = new DefaultRowData(columnSet.getColumnSize());
            dataSet.add(data);
        }
        return dataSet.get(rownum - 1);
    }

    /**
     * Set row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param data The new row-data.
     */
    public void setRowData(int rownum, DBMSRowData data) {
        while (rownum > dataSet.size() + 1) {
            dataSet.add(new DefaultRowData(columnSet.getColumnSize()));
        }
        dataSet.add(rownum - 1, data);
    }

    /**
     * Add row to row-change data.
     *
     * @param data The new row-data.
     */
    public void addRowData(DBMSRowData data) {
        dataSet.add(data);
    }

    /**
     * Return columns changed flags in table.
     */
    public BitSet getChangeIndexes() {
        return changeColumnsBitSet;
    }

    /**
     * Set columns changed flags in table.
     */
    public void setChangeColumnsBitSet(BitSet changeSet) {
        this.changeColumnsBitSet = changeSet;
    }

    /**
     * Check the row value of the designated column is currently changed.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     */
    public boolean hasChangeColumn(int columnIndex) {
        if (changeColumnsBitSet != null) {
            return changeColumnsBitSet.get(columnIndex - 1);
        }
        return false;
    }

    /**
     * Mark the row value of the designated column is changed or not.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param changed The designated column is changed.
     */
    protected void setChangeColumn(int columnIndex, boolean changed) {
        if (changeColumnsBitSet == null) {
            changeColumnsBitSet = new BitSet(columnSet.getColumnSize());
        }
        changeColumnsBitSet.set(columnIndex - 1, changed);
    }

    /**
     * Return changed row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @return The changed row-data.
     */
    public DBMSRowData getChangeData(int rownum) {
        if (changeDataSet != null) {
            while (rownum > changeDataSet.size()) {
                DBMSRowData data = new DefaultRowData(columnSet.getColumnSize());
                changeDataSet.add(data);
            }
            return changeDataSet.get(rownum - 1);
        }
        return null;
    }

    /**
     * Set changed row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param change The changed row-data.
     */
    public void setChangeData(int rownum, DBMSRowData change) {
        if (changeDataSet != null) {
            while (rownum > changeDataSet.size() + 1) {
                changeDataSet.add(new DefaultRowData(columnSet.getColumnSize()));
            }
            changeDataSet.add(rownum - 1, change);
        }
    }

    /**
     * Add changed row to row-change data.
     *
     * @param data The changed row-data.
     */
    public void addChangeData(DBMSRowData change) {
        changeDataSet.add(change);
    }

    /**
     * Returns the session options.
     */
    public List<? extends DBMSOption> getOptions() {
        if (options != null) {
            return options;
        }
        return Collections.emptyList();
    }

    /**
     * Set the session option.
     */
    public void setOptionValue(String name, Serializable value) {
        DBMSOption option = getOption(name);
        if (option != null) {
            option.setValue(value);
            return;
        }
        option = new DefaultOption(name, value);
        putOption(option);
    }

    /**
     * Add the session option.
     */
    public DBMSOption putOption(DBMSOption option) {
        if (options == null) {
            options = new ArrayList<DBMSOption>(4);
        }

        DBMSOption exist = super.putOption(option);
        if (exist != null) {
            options.remove(exist);
        }
        options.add(option);
        return exist;
    }

    /**
     * Remove the row-data by row number. If changed row-data exist, it will be removed also.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     */
    public void removeRowData(int rownum) {
        if (changeDataSet != null) {
            if (rownum <= changeDataSet.size()) {
                changeDataSet.remove(rownum - 1);
            }
        }

        if (rownum <= dataSet.size()) {
            dataSet.remove(rownum - 1);
        }
    }

    public List<DBMSRowData> getDataSet() {
        return this.dataSet;
    }

    public void setDataSet(List<DBMSRowData> dataSet) {
        this.dataSet = dataSet;
    }

    public List<DBMSRowData> getChangeDataSet() {
        return this.changeDataSet;
    }

    public void setChangeDataSet(List<DBMSRowData> changeDataSet) {
        this.changeDataSet = changeDataSet;
    }

    public void setColumnSet(DBMSColumnSet columnSet) {
        this.columnSet = columnSet;
    }

    public DBMSColumnSet getColumnSet() {
        return columnSet;
    }

    public void setOptions(List<DBMSOption> options) {
        this.options = options;
    }

    public List<Boolean> getDummyChangeColumnsBitSet() {
        dummyChangeColumnsBitSet.clear();
        if (changeColumnsBitSet != null) {
            for (int i = 0; i < changeColumnsBitSet.length(); i++) {
                dummyChangeColumnsBitSet.add(changeColumnsBitSet.get(i));
            }
        }

        return dummyChangeColumnsBitSet;
    }

    public void setDummyChangeColumnsBitSet(List<Boolean> dummyChangeColumnsBitSet) {
        if (dummyChangeColumnsBitSet != null) {
            this.changeColumnsBitSet = new BitSet();
            for (int i = 0; i < dummyChangeColumnsBitSet.size(); i++) {
                if (dummyChangeColumnsBitSet.get(i)) {
                    changeColumnsBitSet.set(i);
                }
            }
        }
    }
}
