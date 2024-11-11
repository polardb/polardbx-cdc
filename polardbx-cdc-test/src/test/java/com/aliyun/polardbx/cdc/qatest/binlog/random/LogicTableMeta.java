/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(LogicTableMeta.class);
    private Map<String, ColumnTypeEnum> columnMap = Maps.newConcurrentMap();
    private String pkName = "id";
    private ColumnTypeEnum pkType = ColumnTypeEnum.TYPE_BIGINT;
    private String createSql;
    private String tableName;
    private boolean withPartition = true;

    public LogicTableMeta(Set<ColumnTypeEnum> typeEnumSet) {
        StringBuilder sb = new StringBuilder();
        tableName = RandomStringUtils.randomAlphabetic(10);
        sb.append("create table `").append(tableName).append("`(");
        sb.append("id bigint primary key auto_increment ,");
        for (ColumnTypeEnum typeEnum : typeEnumSet) {
            String columnName = typeEnum.name() + RandomStringUtils.randomAlphanumeric(4);
            sb.append("`").append(columnName).append("` ").append(" ").append(typeEnum.getDefine()).append(" ")
                .append(typeEnum.getDefaultValue()).append(",");
            columnMap.put(columnName, typeEnum);
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        if (withPartition) {
            sb.append("dbpartition by hash(id) tbpartition by hash(id) tbpartitions 32");
        }
        createSql = sb.toString();
    }

    public void initTable(Connection conn) throws SQLException {
        Statement st = conn.createStatement();
        st.executeUpdate(createSql);
    }

    public void randomDropColumn(Connection conn) throws SQLException {
        if (columnMap.size() < 2) {
            return;
        }
        List<String> columnList = new ArrayList<>(columnMap.keySet());
        String columnName = columnList.get(RandomUtils.nextInt(0, columnList.size()));
        String dropColumnDDL = "alter table `random_dml_test`.`" + tableName + "` drop column " + columnName;
        Statement st = conn.createStatement();
        try {
            st.executeUpdate(dropColumnDDL);
        } finally {
            st.close();
        }
        logger.info("execute " + dropColumnDDL);
        columnMap.remove(columnName);
    }

    public void randomAddColumn(Connection conn) throws SQLException {
        if (columnMap.size() > 50) {
            return;
        }
        List<ColumnTypeEnum> columnTypeList = Lists.newArrayList(ColumnTypeEnum.allType());
        ColumnTypeEnum typeEnum = columnTypeList.get(RandomUtils.nextInt(0, columnTypeList.size()));

        String columnName = typeEnum.name() + RandomStringUtils.randomAlphanumeric(4);
        String addColumnDDL =
            "alter table `random_dml_test`.`" + tableName + "` add column `" + columnName + "` " + typeEnum.getDefine()
                + " " + typeEnum
                .getDefaultValue();
        Statement st = conn.createStatement();
        try {
            st.executeUpdate(addColumnDDL);
        } finally {
            st.close();
        }
        logger.info("execute " + addColumnDDL);
        columnMap.put(columnName, typeEnum);
    }

    public void randomModifyColumnType(Connection conn) throws SQLException {
        List<String> columnList = new ArrayList<>(columnMap.keySet());
        ColumnTypeEnum targetColumnType = null;
        String columnName = null;
        while (!columnList.isEmpty()) {
            int idx = RandomUtils.nextInt(0, columnList.size());
            columnName = columnList.get(idx);
            ColumnTypeEnum columnTypeEnum = columnMap.get(columnName);
            List<ColumnTypeEnum> convertTypeList =
                ConverterManager.getInstance().getColumnTypeChangeList(columnTypeEnum);
            if (CollectionUtils.isEmpty(convertTypeList)) {
                columnList.remove(idx);
                continue;
            }
            targetColumnType = convertTypeList.get(RandomUtils.nextInt(0, convertTypeList.size()));
            break;
        }
        if (targetColumnType == null || columnName == null) {
            return;
        }

        String modifyColumnType =
            "alter table `random_dml_test`.`" + tableName + "` modify column `" + columnName + "` " + targetColumnType
                .getDefine() + " "
                + targetColumnType.getDefaultValue();
        Statement st = conn.createStatement();
        try {
            st.executeUpdate(modifyColumnType);
        } finally {
            st.close();
        }
        logger.info("execute " + modifyColumnType);
        columnMap.put(columnName, targetColumnType);
    }

    public void insert(Connection conn) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into `random_dml_test`.`").append(tableName).append("`(");
        List<Object> valueList = Lists.newArrayList();
        List<Integer> geoList = Lists.newArrayList();
        int idx = 0;
        for (Map.Entry<String, ColumnTypeEnum> entry : columnMap.entrySet()) {
            sb.append("`").append(entry.getKey()).append("`,");
            if (entry.getValue().equals(ColumnTypeEnum.TYPE_GEO)) {
                geoList.add(idx);
            } else {
                valueList.add(entry.getValue().generateValue());
            }
            idx++;
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append(") values(");
        for (int i = 0; i < valueList.size() + geoList.size(); i++) {
            if (geoList.contains(i)) {
                sb.append(ColumnTypeEnum.TYPE_GEO.generateValue()).append(",");
            } else {
                sb.append("?,");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");

        PreparedStatement ps = conn.prepareStatement(sb.toString());
        try {
            for (int i = 0; i < valueList.size(); i++) {
                ps.setObject(i + 1, valueList.get(i));
            }
            ps.executeUpdate();
        } finally {
            ps.close();
        }

    }

    public void update(Connection conn) throws SQLException {
    }

    public String getTableName() {
        return tableName;
    }
}
