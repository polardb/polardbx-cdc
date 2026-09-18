/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta.mapping;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_VIRTUAL_TABLE_MAPPING_RULE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

@Slf4j
public class TableNameMapper {

    private final Map<Pair<String, String>, Pair<String, String>> cache = new ConcurrentHashMap<>();
    private volatile List<TableMappingRule> rules = new ArrayList<>();

    public TableNameMapper() {
        loadRules();
    }

    private void loadRules() {
        String rulesStr = getString(META_VIRTUAL_TABLE_MAPPING_RULE);
        if (rulesStr == null || rulesStr.trim().isEmpty()) {
            rules = Collections.emptyList();
        } else {
            rulesStr = rulesStr.trim().toLowerCase();
            for (String ruleStr : rulesStr.split(",")) {
                String[] parts = ruleStr.trim().split("\\|", 2);
                if (parts.length == 2) {
                    rules.add(new TableMappingRule(parts[0], parts[1]));
                }
            }
        }

        this.cache.clear(); // 清除缓存
        log.info("table name mapping rule is loaded, with rule [{}]", rulesStr);
    }

    public Pair<String, String> mapToVirtualTableName(Pair<String, String> actualTableName) {
        if (rules.isEmpty()) {
            return actualTableName;
        }

        Pair<String, String> value = cache.computeIfAbsent(actualTableName, key -> {
            String fullTableName = key.getLeft() + "." + key.getRight();
            for (TableMappingRule rule : rules) {
                if (rule.matches(fullTableName)) {
                    String[] parts = rule.getVirtualTableName().split("\\.");
                    if (parts.length == 2) {
                        return Pair.of(parts[0], parts[1]);
                    } else if (parts.length == 1) {
                        return Pair.of(key.getLeft(), parts[0]);
                    } else {
                        throw new PolardbxException("invalid virtual table name config: " + rule.getVirtualTableName());
                    }
                }
            }
            return key; // 默认返回原表名
        });

        checkResult(value);

        return value;
    }

    private void checkResult(Pair<String, String> value) {
        if (StringUtils.isBlank(value.getKey())) {
            throw new PolardbxException("schema name can`t be empty for virtual table : " + value);
        }

        if (StringUtils.isBlank(value.getValue())) {
            throw new PolardbxException("table name can`t be empty for virtual table : " + value);
        }
    }
}

