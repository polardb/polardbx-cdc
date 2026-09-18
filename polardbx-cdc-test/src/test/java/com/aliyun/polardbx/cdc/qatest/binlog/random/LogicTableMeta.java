/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LogicTableMeta {
    private static final Logger logger = LoggerFactory.getLogger(LogicTableMeta.class);
    private Map<String, ColumnTypeEnum> columnMap = Maps.newConcurrentMap();
    private Map<String, GeneratedColumnInfo> generatedColumnMap = Maps.newConcurrentMap();
    private static final int MAX_GENERATED_COLUMNS = 10;
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
        // 至少保留 2 个非生成列用于 INSERT 测试
        if (columnMap.size() - generatedColumnMap.size() < 2) {
            return;
        }
        List<String> columnList = new ArrayList<>(columnMap.keySet());
        Collections.shuffle(columnList);

        String columnName = null;
        for (String candidate : columnList) {
            if (generatedColumnMap.containsKey(candidate)) {
                // 候选是生成列，可以自由删除
                columnName = candidate;
                break;
            }
            // 候选是普通列，检查是否有生成列依赖它
            boolean hasDependents = generatedColumnMap.values().stream()
                .anyMatch(info -> candidate.equals(info.getSourceColumnName()));
            if (!hasDependents) {
                columnName = candidate;
                break;
            }
        }
        if (columnName == null) {
            return;
        }

        String dropColumnDDL =
            "alter table `random_dml_test`.`" + tableName + "` drop column `" + columnName + "`";
        Statement st = conn.createStatement();
        try {
            st.executeUpdate(dropColumnDDL);
        } finally {
            st.close();
        }
        logger.info("execute " + dropColumnDDL);
        columnMap.remove(columnName);
        generatedColumnMap.remove(columnName);
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
            // 生成列不能修改类型
            if (generatedColumnMap.containsKey(columnName)) {
                columnList.remove(idx);
                continue;
            }
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
            // 跳过生成列，MySQL 自动计算其值
            if (generatedColumnMap.containsKey(entry.getKey())) {
                continue;
            }
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

    /**
     * 随机添加一个生成列（GENERATED ALWAYS AS 表达式），
     * 70% 概率引用 id 主键，30% 概率引用随机普通列
     */
    public void randomAddGeneratedColumn(Connection conn) throws SQLException {
        if (generatedColumnMap.size() >= MAX_GENERATED_COLUMNS || columnMap.size() > 50) {
            return;
        }

        // 随机选择生成列结果类型
        List<ColumnTypeEnum> safeTypes =
            Lists.newArrayList(GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes());
        ColumnTypeEnum resultType = safeTypes.get(RandomUtils.nextInt(0, safeTypes.size()));

        // 决定源列策略：70% 引用 id，30% 引用随机普通列
        String sourceCol;
        ColumnTypeEnum sourceType;
        int strategy = RandomUtils.nextInt(0, 100);
        if (strategy < 70) {
            sourceCol = "id";
            sourceType = ColumnTypeEnum.TYPE_BIGINT;
        } else {
            // 尝试找一个兼容的非生成普通列
            String[] found = findCompatibleSourceColumn(resultType);
            if (found != null) {
                sourceCol = found[0];
                sourceType = columnMap.get(sourceCol);
            } else {
                // 回退到 id
                sourceCol = "id";
                sourceType = ColumnTypeEnum.TYPE_BIGINT;
            }
        }

        String expression =
            GeneratedColumnExpressionBuilder.buildExpression(resultType, sourceCol, sourceType);
        boolean stored = RandomUtils.nextBoolean();

        String columnName = "GEN_" + resultType.name() + RandomStringUtils.randomAlphanumeric(4);
        String addColumnDDL = "alter table `random_dml_test`.`" + tableName
            + "` add column `" + columnName + "` " + resultType.getDefine()
            + " GENERATED ALWAYS AS " + expression + " " + (stored ? "STORED" : "VIRTUAL");

        Statement st = conn.createStatement();
        try {
            st.executeUpdate(addColumnDDL);
        } finally {
            st.close();
        }
        logger.info("execute " + addColumnDDL);

        GeneratedColumnInfo info = new GeneratedColumnInfo(sourceCol, expression, resultType, stored);
        // 顺序重要：先放 generatedColumnMap 再放 columnMap，
        // 防止 DML 线程看到列却不知道是生成列而尝试 INSERT
        generatedColumnMap.put(columnName, info);
        columnMap.put(columnName, resultType);
    }

    /**
     * 从 columnMap 中找一个与 resultType 兼容的非生成普通列作为表达式源
     *
     * @return [列名] 或 null（没找到）
     */
    private String[] findCompatibleSourceColumn(ColumnTypeEnum resultType) {
        Set<ColumnTypeEnum> compatibleTypes =
            GeneratedColumnExpressionBuilder.getCompatibleSourceTypes(resultType);
        List<String> candidates = columnMap.entrySet().stream()
            .filter(e -> !generatedColumnMap.containsKey(e.getKey()))
            .filter(e -> compatibleTypes.contains(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }
        String col = candidates.get(RandomUtils.nextInt(0, candidates.size()));
        return new String[] {col};
    }

    public void update(Connection conn) throws SQLException {
    }

    public String getTableName() {
        return tableName;
    }
}
