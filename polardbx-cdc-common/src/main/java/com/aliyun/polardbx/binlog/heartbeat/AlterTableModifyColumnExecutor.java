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
package com.aliyun.polardbx.binlog.heartbeat;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * @author yanfenglin
 */
public class AlterTableModifyColumnExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableModifyColumnExecutor.class);
    private final JdbcTemplate template;
    private String tableName;
    private String columnName;
    private String dstColumnType;
    private String defaultValue;
    private boolean canNull = true;
    private boolean isModify;

    public AlterTableModifyColumnExecutor(JdbcTemplate template) {
        this.template = template;
    }

    public AlterTableModifyColumnExecutor canNull() {
        this.canNull = true;
        return this;
    }

    public AlterTableModifyColumnExecutor notNull() {
        this.canNull = false;
        return this;
    }

    public AlterTableModifyColumnExecutor defaultNull() {
        this.canNull = true;
        this.defaultValue = "NULL";
        return this;
    }

    public AlterTableModifyColumnExecutor defaultValue(String value) {
        this.defaultValue = value;
        return this;
    }

    public AlterTableModifyColumnExecutor tableName(String tableName) {
        this.tableName = tableName;
        return this;
    }

    public AlterTableModifyColumnExecutor targetColumnType(String targetType) {
        this.dstColumnType = targetType;
        return this;
    }

    public AlterTableModifyColumnExecutor addColumn(String columnName) {
        this.columnName = columnName;
        this.isModify = false;
        return this;
    }

    public AlterTableModifyColumnExecutor modifyColumn(String columnName) {
        this.columnName = columnName;
        this.isModify = true;
        return this;
    }

    private String columnDefine() {
        StringBuilder sb = new StringBuilder();
        sb.append("column `").append(columnName).append("` ").append(dstColumnType);
        if (!canNull) {
            sb.append(" not null");
        }
        if (StringUtils.isNotBlank(defaultValue)) {
            sb.append(" default ").append(defaultValue);
        }
        return sb.toString();
    }

    public void execute() {
        Optional<Map<String, Object>> optional = template.queryForList("desc `" + tableName + "`").stream()
            .filter(e -> StringUtils.equalsIgnoreCase(e.get("Field").toString(), columnName)).findFirst();
        if (optional.isPresent() && dstColumnType.equalsIgnoreCase(optional.get().get("Type").toString())) {
            return;
        }

        if (isModify) {
            String ddlFormat = "alter table `%s` modify %s";
            String ddl = String.format(ddlFormat, tableName, columnDefine());
            template.execute(ddl);
            logger.info("execute modify ddl : " + ddl);
        } else {
            String ddlFormat = "alter table `%s` add %s";
            String ddl = String.format(ddlFormat, tableName, columnDefine());
            template.execute(ddl);
            logger.info("execute add ddl : " + ddl);
        }
    }
}
