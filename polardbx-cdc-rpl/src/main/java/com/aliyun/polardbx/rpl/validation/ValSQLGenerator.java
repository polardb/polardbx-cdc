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

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.applier.SqlContext;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.validation.common.Record;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validation SQL generator per table
 * @author siyu.yusi
 * @date 03/03/2022
 **/
@Builder
@Slf4j
public class ValSQLGenerator {
    private ValidationContext ctx;
    private boolean convertToByte;
    private static final String CHECKSUM = "checksum";

    public String getSelectAllKeysChecksumSQL(TableInfo srcTable) throws Exception {
        // ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)
        List<String> srcColList = srcTable.getColumns().stream().map(ColumnInfo::getName).collect(Collectors.toList());
        // Use dst table key list as it most likely will contain partition keys
        List<String> keyColList = ctx.getMappingTable().get(srcTable.getName()).getKeyList();
        StringBuilder concatSb = new StringBuilder();
        for (int i = 0; i < srcColList.size(); i++) {
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", srcColList.get(i)));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", srcColList.get(i)));
            }
        }
        // ',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)))
        StringBuilder concatWsSb = new StringBuilder();
        // ',' + space
        concatWsSb.append("',', ");
        for (String column : srcColList) {
            concatWsSb.append(String.format("`%s`, ", column));
        }
        concatWsSb.append(concatSb);
        // CONCAT_WS(',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", concatWsSb);

        // c_w_id, c_d_id, c_id
        String keyCols = keyColList.stream().collect(Collectors.joining("`,`", "`", "`"));

        return String.format("/*+TDDL:IN_SUB_QUERY_THRESHOLD=2*/ SELECT CAST(CRC32(%s) AS UNSIGNED) AS checksum, %s FROM `%s`.`%s`", concatWs, keyCols, ctx.getSrcPhyDB(), srcTable.getName());
    }

    public SqlContext formatSelectSQL(TableInfo table, List<Serializable> keyValList) {
        List<Serializable> params = new ArrayList<>(table.getKeyList().size());
        String where = getWhereString(keyValList, table.getKeyList(), params);
        String columns = table.getColumns().stream().map(ColumnInfo::getName).collect(Collectors.joining("`,`", "`", "`"));
        String sql = String.format("SELECT %s FROM `%s`.`%s` WHERE %s", columns, ctx.getSrcPhyDB(), table.getName(), where);
        return new SqlContext(sql, null, null, params);
    }

    // replace into `%s`.`%s` (%s) values (?,?,?)
    public PreparedStatement formatInsertStatement(Connection conn, TableInfo dstTable, Record...records) throws SQLException {
        if (records.length < 1) {
            throw new SQLException(String.format("Records cannot be less than one. db: %s, table: %s", ctx.getDstLogicalDB(), dstTable.getName()));
        }
        List<String> valueList = new ArrayList<>();
        List<String> colList = records[0].getColumnList();


        for (int k = 0; k < records.length; k++) {
            StringBuilder updateSb = new StringBuilder();
            for (int i = 0; i < colList.size(); i++) {
                if (i == 0) {
                    updateSb.append("?");
                } else {
                    updateSb.append(",?");
                }
            }
            valueList.add(updateSb.toString());
        }


        // (`a`=?,`b`=?),(`a`=?,`b`=?)
        String values = valueList.stream().collect(Collectors.joining("),(", "(", ")"));
        String columns = colList.stream().collect(Collectors.joining("`,`", "`", "`"));

        String sql = String.format("REPLACE INTO `%s`.`%s` (%s) VALUES %s", ctx.getDstLogicalDB(), dstTable.getName(), columns, values);
        PreparedStatement preparedStatement = conn.prepareStatement(sql);
        int pos = 1;
        for (Record r : records) {
            for (Object obj : r.getValList()) {
                preparedStatement.setObject(pos, obj);
                pos++;
            }
        }
        return preparedStatement;
    }

    /**
     * e.g. SELECT BIT_XOR(CAST(CRC32(CONCAT_WS(',', C1, C2, C3, C4, C5, C6, C7, C8, C9, CONCAT(ISNULL(C1), ISNULL(C2), ISNULL(C3), ISNULL(C4), ISNULL(C5), ISNULL(C6), ISNULL(C7), ISNULL(C8), ISNULL(C9)))) AS UNSIGNED)) AS checksum FROM `tpch.orders`;
     */
    public SqlContext generateChecksumSQL(TableInfo srcTable, Map<String, List<Serializable>> keyValMap, boolean isDst) {
        // ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)
        String db = isDst ? ctx.getDstLogicalDB() : ctx.getSrcPhyDB();
        TableInfo table = isDst ? ctx.getMappingTable().get(srcTable.getName()) : srcTable;

        StringBuilder concatSb = new StringBuilder();
        List<ColumnInfo> columns = srcTable.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", columns.get(i).getName()));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", columns.get(i).getName()));
            }
        }
        // ',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)))
        StringBuilder concatWsSb = new StringBuilder();
        // ',' + space
        concatWsSb.append("',', ");
        for (ColumnInfo column : columns) {
            if (convertToByte) {
                // deal with Illegal mix of collations for operation 'concat_ws'
                concatWsSb.append(String.format("convert(`%s` using byte), ", column.getName()));
            } else {
                concatWsSb.append(String.format("`%s`, ", column.getName()));
            }
        }
        concatWsSb.append(concatSb);
        // CONCAT_WS(',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", concatWsSb);
        List<String> predicate = new ArrayList<>();
        List<Serializable> params = new ArrayList<>();
        for (String key : table.getKeyList()) {
            boolean hasNull = false;
            boolean hasNotNull = false;
            List<Serializable> values = keyValMap.get(key);
            for (Serializable value : values) {
                if (value == null) {
                    hasNull = true;
                    break;
                }
            }
            List<Serializable> thisKeyParams = values.stream().filter(Objects::nonNull)
                .map(Object::toString).distinct().collect(Collectors.toList());
            if (thisKeyParams.size() > 0) {
                hasNotNull = true;
                params.addAll(thisKeyParams);
            }
            if (hasNotNull && hasNull) {
                predicate.add(String.format("(`%s` IN (%s) or `%s` IS NULL)", key,
                    StringUtils.repeat("?",",",thisKeyParams.size()), key));
            } else if (hasNotNull) {
                predicate.add(String.format("(`%s` IN (%s))", key,
                    StringUtils.repeat("?",",",thisKeyParams.size())));
            } else if (hasNull) {
                predicate.add(String.format("`%s` IS NULL", key));
            } else {
                throw new PolardbxException(String.format("where for `%s` must has null or has not null", key));
            }
            // String valList = keyValMap.get(key).stream().map(Object::toString).distinct().collect(Collectors.joining("','", "'", "'"));
        }
        String where = predicate.stream().collect(Collectors.joining(" AND "));
        String sql = String.format("SELECT BIT_XOR(CAST(CRC32(%s) AS UNSIGNED)) AS checksum FROM `%s`.`%s` WHERE %s",
            concatWs, db, table.getName(), where);
        return new SqlContext(sql, null, null, params);
    }

    public SqlContext formatSingleRowChecksumSQL(TableInfo dstTable, List<Serializable> keyValList) {
        // ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)

        StringBuilder concatSb = new StringBuilder();
        for (int i = 0; i < dstTable.getColumns().size(); i++) {
            ColumnInfo column = dstTable.getColumns().get(i);
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", column.getName()));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", column.getName()));
            }
        }
        // ',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)))
        StringBuilder concatWsSb = new StringBuilder();
        // ',' + space
        concatWsSb.append("',', ");
        for (ColumnInfo column : dstTable.getColumns()) {
            if (convertToByte) {
                // deal with Illegal mix of collations for operation 'concat_ws'
                concatWsSb.append(String.format("convert(`%s` using byte), ", column.getName()));
            } else {
                concatWsSb.append(String.format("`%s`, ", column.getName()));
            }
        }
        concatWsSb.append(concatSb);
        // CONCAT_WS(',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", concatWsSb);
        List<Serializable> params = new ArrayList<>(dstTable.getKeyList().size());
        String where = getWhereString(keyValList, dstTable.getKeyList(), params);
        String sql =  String.format("/*+TDDL:IN_SUB_QUERY_THRESHOLD=2*/ SELECT BIT_XOR(CAST(CRC32(%s) AS UNSIGNED)) AS checksum FROM `%s`.`%s` WHERE %s",
                concatWs, ctx.getDstLogicalDB(), dstTable.getName(), where);
        return new SqlContext(sql, null, null, params);
    }

    public String getWhereString(List<Serializable> keyValList, List<String> keyList, List<Serializable> params) {
        List<String> whereList = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++) {
            if (keyValList.get(i) == null) {
                whereList.add(String.format("`%s` IS NULL", keyList.get(i)));
            } else {
                whereList.add(String.format("`%s` = ?", keyList.get(i)));
                params.add(keyValList.get(i));
            }
        }
        return String.join(" AND ", whereList);
    }

}
