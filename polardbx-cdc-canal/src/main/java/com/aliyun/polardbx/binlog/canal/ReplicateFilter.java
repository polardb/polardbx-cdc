/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.canal;

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据匹配
 */
public class ReplicateFilter {

    private Pattern doDbPattern;
    private Pattern ignoreDbPattern;
    private Pattern doTabPattern;
    private Pattern ignoreTabPattern;

    private String doDbFilter;
    private String doTabFilter;
    private String ignoreDbFilter;
    private String ignoreTabFilter;

    private Map<Pattern, Pattern> ignoreSchemaTablePatternMap = Maps.newHashMap();
    private Map<String, String> ignoreSchemaTableMap = Maps.newHashMap();

    public boolean filter(String schema, String tableName) {
        return filterSchema(schema) || filterTable(tableName) || filterSchemaAndTable(schema, tableName);
    }

    public boolean filterSchemaAndTable(String schema, String tableName) {
        for (Map.Entry<Pattern, Pattern> entry : ignoreSchemaTablePatternMap.entrySet()) {
            if (entry.getKey().matcher(schema).matches() && entry.getValue().matcher(tableName).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean filterSchema(String schema) {
        // If schema not provided, do not filter, otherwise filter schema.
        if (schema == null || schema.length() == 0) {
            // If no schema or empty schema, ignore SQL/data.
            // return (doDbPattern != null);
            return false;
        }

        // If ignore pattern provided, ignore matches schema.
        if (ignoreDbPattern != null) {
            // If schema matches ignore pattern, filter it.
            if (ignoreDbPattern.matcher(schema).matches()) {
                return true;
            }
        }

        // If no ignore pattern, or schema not matches ignore pattern.
        if (doDbPattern != null) {
            // If do pattern provided, ignore schema not matched.
            if (!doDbPattern.matcher(schema).matches()) {
                return true;
            }
        }

        // If no ignore pattern, or schema not matches ignore pattern,
        // and if no do pattern, or schema matches do pattern.
        return false;
    }

    public boolean filterTable(String table) {
        // If table not provided, do not filter, otherwise filter table.
        if (table == null || table.length() == 0) {
            // If no table or table empty, ignore SQL/data.
            // return (doTabPattern != null);
            return false;
        }

        // If ignore pattern provided, ignore matches table.
        if (ignoreTabPattern != null) {
            // If table matches ignore pattern, filter it.
            if (ignoreTabPattern.matcher(table).matches()) {
                return true;
            }
        }

        // If no ignore pattern, or table not matches ignore pattern.
        if (doTabPattern != null) {
            // If do pattern provided, ignore table not matched.
            if (!doTabPattern.matcher(table).matches()) {
                return true;
            }
        }

        // If no ignore pattern, or table not matches ignore pattern,
        // and if no do pattern, or table matches do pattern.
        return false;
    }

    public String getDoDbFilter() {
        return doDbFilter;
    }

    public void setDoDbFilter(String doDbFilter) {
        this.doDbFilter = doDbFilter;
        if (doDbFilter != null && !doDbFilter.isEmpty()) {
            doDbPattern = Pattern.compile(doDbFilter, Pattern.CASE_INSENSITIVE);
        }
    }

    public void addIgnoreDbTbFilter(String ignoreDbFilter, String ignoreTabFilter) {
        this.ignoreSchemaTableMap.put(ignoreDbFilter, ignoreTabFilter);
        this.ignoreSchemaTablePatternMap.put(Pattern.compile(ignoreDbFilter, Pattern.CASE_INSENSITIVE),
            Pattern.compile(ignoreTabFilter, Pattern.CASE_INSENSITIVE));
    }

    public String getDoTabFilter() {
        return doTabFilter;
    }

    public void setDoTabFilter(String doTabFilter) {
        this.doTabFilter = doTabFilter;
        if (doTabFilter != null && !doTabFilter.isEmpty()) {
            doTabPattern = Pattern.compile(doTabFilter, Pattern.CASE_INSENSITIVE);
        }
    }

    public String getIgnoreDbFilter() {
        return ignoreDbFilter;
    }

    public void setIgnoreDbFilter(String ignoreDbFilter) {
        this.ignoreDbFilter = ignoreDbFilter;
        if (ignoreDbFilter != null && !ignoreDbFilter.isEmpty()) {
            ignoreDbPattern = Pattern.compile(ignoreDbFilter, Pattern.CASE_INSENSITIVE);
        }
    }

    public String getIgnoreTabFilter() {
        return ignoreTabFilter;
    }

    public void setIgnoreTabFilter(String ignoreTabFilter) {
        this.ignoreTabFilter = ignoreTabFilter;
        if (ignoreTabFilter != null && !ignoreTabFilter.isEmpty()) {
            ignoreTabPattern = Pattern.compile(ignoreTabFilter, Pattern.CASE_INSENSITIVE);
        }
    }

}
