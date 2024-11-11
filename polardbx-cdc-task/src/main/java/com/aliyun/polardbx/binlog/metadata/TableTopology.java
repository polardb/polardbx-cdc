/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metadata;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ziyang.lb
 **/
public class TableTopology {

    /**
     * key：物理库名，value：逻辑库名
     */
    private Map<String, String> physicalDatabasesMap = new ConcurrentHashMap<>();
    /**
     * key: 物理表名(格式：逻辑库.物理表)，value：逻辑表名
     */
    private Map<String, String> physicalTablesMap = new ConcurrentHashMap<>();
    /**
     * key: 逻辑库名，value：物理库名集合
     */
    private Map<String, Set<String>> logicalDatabasesMap = new ConcurrentHashMap<>();
    /**
     * key: 逻辑表名(格式：逻辑库.逻辑表)，value：物理表名集合
     */
    private Map<String, Set<String>> logicalTablesMap = new ConcurrentHashMap<>();

    /**
     * key: 物理库名，value：物理表名集合
     */
    private Map<String, Set<String>> physicalDatabaseTableMap = new ConcurrentHashMap<>();

    // -----------------------------------getter&setter-----------------------------------
    public Map<String, String> getPhysicalDatabasesMap() {
        return physicalDatabasesMap;
    }

    public void setPhysicalDatabasesMap(Map<String, String> physicalDatabasesMap) {
        this.physicalDatabasesMap = physicalDatabasesMap;
    }

    public Map<String, String> getPhysicalTablesMap() {
        return physicalTablesMap;
    }

    public void setPhysicalTablesMap(Map<String, String> physicalTablesMap) {
        this.physicalTablesMap = physicalTablesMap;
    }

    public Map<String, Set<String>> getLogicalDatabasesMap() {
        return logicalDatabasesMap;
    }

    public void setLogicalDatabasesMap(Map<String, Set<String>> logicalDatabasesMap) {
        this.logicalDatabasesMap = logicalDatabasesMap;
    }

    public Map<String, Set<String>> getLogicalTablesMap() {
        return logicalTablesMap;
    }

    public void setLogicalTablesMap(Map<String, Set<String>> logicalTablesMap) {
        this.logicalTablesMap = logicalTablesMap;
    }

    public Map<String, Set<String>> getPhysicalDatabaseTableMap() {
        return physicalDatabaseTableMap;
    }

    public void setPhysicalDatabaseTableMap(
        Map<String, Set<String>> physicalDatabaseTableMap) {
        this.physicalDatabaseTableMap = physicalDatabaseTableMap;
    }
}
