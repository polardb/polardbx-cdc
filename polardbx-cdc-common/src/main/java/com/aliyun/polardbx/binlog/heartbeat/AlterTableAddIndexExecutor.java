/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.heartbeat;

import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yanfenglin
 */
public class AlterTableAddIndexExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableAddIndexExecutor.class);
    private final JdbcTemplate template;
    private String tableName;
    private String indexName;
    private boolean unique = false;

    private List<String> columnList = Lists.newArrayList();

    public AlterTableAddIndexExecutor(JdbcTemplate template) {
        this.template = template;
    }

    public AlterTableAddIndexExecutor tableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public AlterTableAddIndexExecutor indexName(String indexName) {
        this.indexName = indexName;
        return this;
    }

    public AlterTableAddIndexExecutor addIndexColumn(String column) {
        this.columnList.add(column);
        return this;
    }

    public AlterTableAddIndexExecutor unique() {
        this.unique = true;
        return this;
    }

    public void execute() {
        Set<String> dbIndexColumnSet =
            template.queryForList("show index from `" + tableName + "`").stream()
                .filter(e -> StringUtils.equals(e.get("Key_name").toString(), indexName))
                .map(e -> StringUtils.lowerCase(String.valueOf(e.get("Column_name")))).collect(
                    Collectors.toSet());
        if (CollectionUtils.isNotEmpty(dbIndexColumnSet)) {
            boolean equal = true;
            if (dbIndexColumnSet.size() != columnList.size()) {
                equal = false;
            }
            if (equal) {
                int size = dbIndexColumnSet.size();
                for (int i = 0; i < size; i++) {
                    String columnName = columnList.get(i);
                    if (!dbIndexColumnSet.contains(StringUtils.lowerCase(columnName))) {
                        equal = false;
                        break;
                    }
                }
            }
            if (!equal) {
                doDrop();
                doCreate();
            }
        } else {
            doCreate();
        }
    }

    private void doDrop() {
        StringBuilder sb = new StringBuilder();
        sb.append("alter table `").append(tableName).append("` drop index `").append(indexName).append("`");
        template.execute(sb.toString());
        logger.info("execute drop index : " + sb.toString());
    }

    private void doCreate() {
        StringBuilder sb = new StringBuilder();
        sb.append("alter table `").append(tableName).append("` add ");
        if (unique) {
            sb.append(" unique ");
        }
        sb.append(" index `").append(indexName).append("`(");
        for (String c : columnList) {
            sb.append("`").append(c).append("`,");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        template.execute(sb.toString());
        logger.info("execute add index : " + sb.toString());
    }

}
