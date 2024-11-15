/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain.po;

public class TablesExtWithBLOBs extends TablesExt {
    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column tables_ext.db_meta_map
     */
    private String dbMetaMap;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column tables_ext.tb_meta_map
     */
    private String tbMetaMap;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database column tables_ext.ext_partitions
     */
    private String extPartitions;

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column tables_ext.db_meta_map
     *
     * @return the value of tables_ext.db_meta_map
     */
    public String getDbMetaMap() {
        return dbMetaMap;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column tables_ext.db_meta_map
     *
     * @param dbMetaMap the value for tables_ext.db_meta_map
     */
    public void setDbMetaMap(String dbMetaMap) {
        this.dbMetaMap = dbMetaMap == null ? null : dbMetaMap.trim();
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column tables_ext.tb_meta_map
     *
     * @return the value of tables_ext.tb_meta_map
     */
    public String getTbMetaMap() {
        return tbMetaMap;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column tables_ext.tb_meta_map
     *
     * @param tbMetaMap the value for tables_ext.tb_meta_map
     */
    public void setTbMetaMap(String tbMetaMap) {
        this.tbMetaMap = tbMetaMap == null ? null : tbMetaMap.trim();
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method returns the value of the database column tables_ext.ext_partitions
     *
     * @return the value of tables_ext.ext_partitions
     */
    public String getExtPartitions() {
        return extPartitions;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method sets the value of the database column tables_ext.ext_partitions
     *
     * @param extPartitions the value for tables_ext.ext_partitions
     */
    public void setExtPartitions(String extPartitions) {
        this.extPartitions = extPartitions == null ? null : extPartitions.trim();
    }
}