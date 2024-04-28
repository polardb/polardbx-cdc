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
package com.aliyun.polardbx.rpl.validation;

import lombok.Getter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * @author yudong
 * @since 2024/1/23 10:34
 **/
public class SqlContextBuilder {

    public static SqlContext buildRangeQueryContext(String sqlBeforeWhere,
                                                    String sqlAfterWhere,
                                                    List<String> KeyNames,
                                                    List<Object> lowerBound,
                                                    List<Object> upperBound) {
        List<Object> params = new ArrayList<>();
        StringBuilder lowerCondition = new StringBuilder();
        StringBuilder upperCondition = new StringBuilder();

        boolean withLowerBound = false, withUpperBound = false;
        if (CollectionUtils.isNotEmpty(lowerBound)) {
            withLowerBound = true;
            // 构建下界条件
            for (int i = 0; i < KeyNames.size(); i++) {
                if (i > 0) {
                    lowerCondition.append(" OR ");
                }
                lowerCondition.append("(");
                for (int j = 0; j < i; j++) {
                    lowerCondition.append(String.format("`%s`", escape(KeyNames.get(j)))).append(" = ? AND ");
                    params.add(lowerBound.get(j));
                }
                lowerCondition.append(String.format("`%s`", escape(KeyNames.get(i)))).append(" >= ?");
                params.add(lowerBound.get(i));
                lowerCondition.append(")");
            }
        }

        if (CollectionUtils.isNotEmpty(upperBound)) {
            withUpperBound = true;
            // 构建上界条件
            for (int i = 0; i < KeyNames.size(); i++) {
                if (i > 0) {
                    upperCondition.append(" OR ");
                }
                upperCondition.append("(");
                for (int j = 0; j < i; j++) {
                    upperCondition.append(KeyNames.get(j)).append(" = ? AND ");
                    params.add(upperBound.get(j));
                }
                upperCondition.append(KeyNames.get(i)).append(" < ?");
                params.add(upperBound.get(i));
                upperCondition.append(")");
            }
        }

        String sql;
        if (withLowerBound && withUpperBound) {
            sql = sqlBeforeWhere + " WHERE (" + lowerCondition + ") AND (" + upperCondition + ")";
        } else if (withLowerBound) {
            sql = sqlBeforeWhere + " WHERE " + lowerCondition;
        } else if (withUpperBound) {
            sql = sqlBeforeWhere + " WHERE " + upperCondition;
        } else {
            sql = sqlBeforeWhere;
        }

        if (StringUtils.isNotEmpty(sqlAfterWhere)) {
            sql += sqlAfterWhere;
        }

        return new SqlContext(sql, params);
    }

    public static SqlContext buildPointQueryContext(String sqlBeforeWhere, List<String> keyNames, List<Object> keyVal) {
        if (keyNames == null || keyVal == null || keyNames.size() != keyVal.size()) {
            throw new IllegalArgumentException(
                "illegal argument for point query, keyNames:" + keyNames + ", keyVal:" + keyVal);
        }

        List<Object> params = new ArrayList<>();
        List<String> whereList = new ArrayList<>();
        for (int i = 0; i < keyNames.size(); i++) {
            if (keyVal.get(i) == null) {
                whereList.add(String.format("`%s` IS NULL", escape(keyNames.get(i))));
            } else {
                whereList.add(String.format("`%s` = ?", escape(keyNames.get(i))));
                params.add(keyVal.get(i));
            }
        }

        String sql = sqlBeforeWhere + " WHERE " + String.join(" AND ", whereList);

        return new SqlContext(sql, params);
    }

    @Getter
    public static class SqlContext {
        String sql;
        List<Object> params;

        public SqlContext(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

}
