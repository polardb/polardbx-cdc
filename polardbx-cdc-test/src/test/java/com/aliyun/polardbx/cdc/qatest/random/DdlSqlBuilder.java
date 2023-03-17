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
package com.aliyun.polardbx.cdc.qatest.random;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isBit;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isBoolean;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isCompatibleTime;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isFloatingType;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isFloatingWithZeroFraction;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isGeometry;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isJson;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isNumberic;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isTextOrBlob;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isTime;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isVarCharOrBinary;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.parseLength;

/**
 * created by ziyang.lb
 **/
public class DdlSqlBuilder {

    private final String tableName;
    private final ColumnSeeds columnSeeds;
    private final boolean supportModifyFractionalFloating;
    private final boolean supportModifyBetweenFloatingAndInteger;
    private final boolean supportZeroFillToString;
    private final boolean supportJsonToFixString;
    private final boolean checkTimeCompatibility;
    private final boolean timestampDefaultNull;

    private final Set<String> addedColumnTypes;
    private final Set<String> droppedColumnTypes;
    private final Set<String> modifiedColumnTypes;

    public DdlSqlBuilder(String tableName, ColumnSeeds columnSeeds, boolean supportModifyFractionalFloating,
                         boolean supportModifyBetweenFloatingAndInteger, boolean supportJsonToFixString,
                         boolean supportZeroFillToString, boolean checkTimeCompatibility,
                         boolean timestampDefaultNull) {
        this.tableName = tableName;
        this.columnSeeds = columnSeeds;
        this.supportModifyFractionalFloating = supportModifyFractionalFloating;
        this.supportModifyBetweenFloatingAndInteger = supportModifyBetweenFloatingAndInteger;
        this.supportJsonToFixString = supportJsonToFixString;
        this.supportZeroFillToString = supportZeroFillToString;
        this.checkTimeCompatibility = checkTimeCompatibility;
        this.timestampDefaultNull = timestampDefaultNull;

        this.addedColumnTypes = new HashSet<>();
        this.droppedColumnTypes = new HashSet<>();
        this.modifiedColumnTypes = new HashSet<>();
    }

    Pair<String, String> buildAddColumnSql(String columnName) {
        Pair<String, String> columnSeed = findSeedColumn4Add();
        String seedColumnName = columnSeed.getKey();
        String seedColumnType = columnSeed.getValue();
        String seedColumnDefault =
            getRandomDefaultValue(columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.get(seedColumnType));
        seedColumnDefault = getDefaultValue(seedColumnType, seedColumnDefault, "ADD");

        String sql = String.format("alter table %s add column `%s` %s DEFAULT %s", tableName,
            columnName, seedColumnType,
            needQuota(seedColumnType, seedColumnDefault) ? "'" + seedColumnDefault + "'" : seedColumnDefault);

        boolean withAfter = new Random().nextBoolean();
        if (withAfter) {
            sql += " after `" + seedColumnName + "`";
        }

        return Pair.of(seedColumnType, sql);
    }

    String buildDropColumnSql(String columnName) {
        return String.format("alter table `%s` drop column `%s`", tableName, columnName);
    }

    Pair<Pair<String, String>, String> buildModifyColumnSql() {
        Pair<String, String> columnSeed = findSeedColumn4Modify();
        Map.Entry<String, List<String>> targetColumnType;

        if (isNumberic(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Numberic(columnSeed.getValue());
        } else if (isTextOrBlob(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4String();
        } else if (isVarCharOrBinary(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4String();
        } else if (isTime(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Time(columnSeed.getValue());
        } else if (isBit(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Bit();
        } else if (isJson(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Json();
        } else if (isGeometry(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Geometry();
        } else if (isBoolean(columnSeed.getValue())) {
            targetColumnType = findTargetColumnType4Numberic(columnSeed.getValue());
        } else {
            throw new PolardbxException("invalid column type for modify " + columnSeed.getValue());
        }

        String defaultValue =
            getDefaultValue(targetColumnType.getKey(), getRandomDefaultValue(targetColumnType.getValue()), "MODIFY");
        String sql = String.format("alter table `%s` modify column `%s` %s DEFAULT %s", tableName,
            columnSeed.getKey(), targetColumnType.getKey(),
            needQuota(targetColumnType.getKey(), defaultValue) ? "'" + defaultValue + "'" : defaultValue);
        return Pair.of(Pair.of(columnSeed.getKey(), targetColumnType.getKey()), sql);
    }

    // 对于时间类型(datetime/timestamp等)来说，指定了默认值为CURRENT_TIMESTAMP，会导致上下游数据校验时，数据不一致
    // 举例来说:
    // 1） 执行Sql：alter table t1 add column c_timestamp timestamp DEFAULT CURRENT_TIMESTAMP
    // 2） 上游执行的时间和下游复制执行的时间点如果不同，上下游数据则不同，校验时出现失败
    String getDefaultValue(String columnType, String defaultValue, String target) {
        if (StringUtils.equalsIgnoreCase(defaultValue, "CURRENT_TIMESTAMP") && timestampDefaultNull) {
            return "NULL";
        }

        // 1）create table ttt(id bigint,`jkwtvdru` datetime default null,primary key(id));
        // 2）insert into ttt(id,`jkwtvdru`)values(1,null);
        // 3）insert into ttt(id,`jkwtvdru`)values(1,null);
        // 4）alter table `t_random_1` modify column `jkwtvdru` timestamp default '2020-12-29 12:27:30'
        // 对于上面的sql-4来说，虽然显示指定了默认值，但是mysql并没有使用该默认值，而是使用了当前时间
        // 但是如果执行： alter table ttt add column `juuu` timestamp default '2020-12-29 12:27:30'，则会使用sql中的默认值
        if (StringUtils.startsWithIgnoreCase(columnType, "timestamp")) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                dateFormat.parse(defaultValue);
                if (timestampDefaultNull && "MODIFY".equals(target)) {
                    return "NULL";
                }
            } catch (ParseException e) {
            }
        }

        return defaultValue;
    }

    Pair<String, String> findSeedColumn4Add() {
        // 尽量每个类型都能执行add操作
        List<String> columnTypesList = new ArrayList<>(columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.keySet());
        columnTypesList.removeIf(addedColumnTypes::contains);
        if (columnTypesList.isEmpty()) {
            addedColumnTypes.clear();
            columnTypesList = new ArrayList<>(columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.keySet());
        }
        String columnType = columnTypesList.get(new Random().nextInt(columnTypesList.size()));
        addedColumnTypes.add(columnType);

        //随机取一个列名
        List<String> columnNames = new ArrayList<>(columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.keySet());
        String columnName = columnNames.get(new Random().nextInt(columnNames.size()));

        return Pair.of(columnName, columnType);
    }

    String findSeedColumn4Drop() {
        // 尽量每个类型都能执行drop操作
        List<Map.Entry<String, String>> list = new ArrayList<>(columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.entrySet());
        list.removeIf(entry -> droppedColumnTypes.contains(entry.getValue()));
        if (list.isEmpty()) {
            droppedColumnTypes.clear();
            list = new ArrayList<>(columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.entrySet());
        }
        Map.Entry<String, String> entry = list.get(new Random().nextInt(list.size()));
        droppedColumnTypes.add(entry.getValue());
        return entry.getKey();
    }

    Pair<String, String> findSeedColumn4Modify() {
        // 尽量每个类型都能执行modify操作
        List<Map.Entry<String, String>> list = filterColumn4Modify();
        list.removeIf(entry -> modifiedColumnTypes.contains(entry.getValue()));
        if (list.isEmpty()) {
            modifiedColumnTypes.clear();
            list = filterColumn4Modify();
        }

        Map.Entry<String, String> entry = list.get(new Random().nextInt(list.size()));
        modifiedColumnTypes.add(entry.getValue());
        return Pair.of(entry.getKey(), entry.getValue());
    }

    List<Map.Entry<String, String>> filterColumn4Modify() {
        return columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.entrySet().stream()
            .filter(i -> !i.getValue().startsWith("enum") && !i.getValue().startsWith("set"))
            .filter(i -> !isGeometry(i.getValue()))
            .filter(i -> {
                //对于浮点类型(float & double & decimal)来说
                // 1) polardbx和mysql转化为字符串之后的值，不一定是相同的
                // 2) float转换为bigint/int等类型时，也会出现精度差异
                // 3) decimal(10,0)转换为double(10,3)时，上游数据是9999999.999，下游数据是9999999.0
                if (isFloatingType(i.getValue())) {
                    return supportModifyFractionalFloating || isFloatingWithZeroFraction(i.getValue());
                } else {
                    return true;
                }
            })
            .collect(Collectors.toList());
    }

    Map.Entry<String, List<String>> findTargetColumnType4Numberic(String seedType) {
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> isNumberic(i.getKey())
                || (isVarCharOrBinary(i.getKey()) && parseLength(i.getKey()).getKey() > 50)
                || isTextOrBlob(i.getKey()))
            .filter(i -> {
                // 对于使用了ZEROFILL的数字类型来说，转换为字符串类型，当触发整形操作时，可能会触发上下游数据不一致，举例来说
                // 1）列定义如下： `c_mediumint_zerofill_un` mediumint(8) UNSIGNED ZEROFILL DEFAULT 7788
                // 2) 将其变更为text类型： modify column `c_mediumint_zerofill_un` text default null
                // 3) 部分物理表已经变更成功，对其进行插入操作，不会前补0，如插入3504631，select结果就是3504631
                // 4) 下游mysql收到3504631，但由于类型仍然还是mediumint unsigned，所以会按数字进行处理，即select结果是03504631
                // 5）当下游mysql接收到逻辑ddl sql时，在将数字转换为字符串的过程中，会保留前面的0，最终上下游数据不一致
                if (StringUtils.containsIgnoreCase(seedType, "zerofill") && !supportZeroFillToString) {
                    return isNumberic(i.getKey());
                } else {
                    return true;
                }
            })
            .filter(i -> {
                // floating to integer时，可能会导致上下游数据不一致，举例
                // 1) create table t_v(id bigint,`vueg2tpi` float default '9.1096275e8',primary key(id));
                // 2）变更物理表结构：alter table `t_v_urds` modify column `vueg2tpi` bigint(64) default '9223372036854775807'
                // 3) 插入数据：insert into t_v(id,`vueg2tpi`)values(2,54174980300268130000000000000000000000);
                // 4）此时上游数据是：9223372036854775807，下游数据是：9.22337e18
                // 5）逻辑表完成表结构变更，并同步ddl到下游单机mysql
                // 6) 此时上游数据是：9223372036854775807，下游数据是：-9223372036854775808

                // integer to floating时，也可能会导致上下游数据不一致，举例
                // 1) create table t_o(id bigint,`c_bigint_zerofill_un` bigint(20) unsigned zerofill default 1,primary key(id));
                // 2) 变更物理表机构：alter table t_o_qa4z modify column `c_bigint_zerofill_un` decimal(10,3) default '1223077.292';
                // 3) 插入数据：insert into t_o(id,c_bigint_zerofill_un)values(2,1014451943225);
                // 4）此时上游数据是9999999.999，下游数据是00000000000009999999
                // 5）逻辑表完成表结构变更，并同步ddl到下游单机mysql
                // 6）此时上游数据是：9999999.999，下游数据是9999999.000
                if (isFloatingType(seedType) && isFloatingType(i.getKey())) {
                    return (supportModifyFractionalFloating || parseLength(i.getKey()).getValue() == 0);
                } else if (isFloatingType(seedType) && !isFloatingType(i.getKey())) {
                    return supportModifyBetweenFloatingAndInteger;
                } else if (!isFloatingType(seedType) && isFloatingType(i.getKey())) {
                    return supportModifyBetweenFloatingAndInteger || parseLength(i.getKey()).getValue() == 0;
                } else {
                    return true;
                }
            })
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    Map.Entry<String, List<String>> findTargetColumnType4String() {
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> isVarCharOrBinary(i.getKey()) || isTextOrBlob(i.getKey()))
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    Map.Entry<String, List<String>> findTargetColumnType4Time(String columnType) {
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> (isVarCharOrBinary(i.getKey()) && parseLength(i.getKey()).getKey() > 30)
                || isTextOrBlob(i.getKey())
                || (isTime(i.getKey()) && (!checkTimeCompatibility || isCompatibleTime(columnType, i.getKey()))))
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    Map.Entry<String, List<String>> findTargetColumnType4Bit() {
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> isBit(i.getKey()))
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    Map.Entry<String, List<String>> findTargetColumnType4Json() {
        // 增加参数supportJsonToFixString，控制是否可以将json转化为定长字符串
        // 原因参见：com.aliyun.polardbx.binlog.format.field.JsonField.check
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> isVarCharOrBinary(i.getKey()) && supportJsonToFixString || isTextOrBlob(i.getKey()))
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    Map.Entry<String, List<String>> findTargetColumnType4Geometry() {
        List<Map.Entry<String, List<String>>> list = columnSeeds.COLUMN_TYPE_DEFAULT_VALUE_MAPPING.entrySet().stream()
            .filter(i -> isVarCharOrBinary(i.getKey()) || isTextOrBlob(i.getKey()))
            .collect(Collectors.toList());
        return list.get(new Random().nextInt(list.size()));
    }

    boolean needQuota(String columnType, String defaultValue) {
        boolean needQuota = true;
        if (StringUtils.equalsIgnoreCase(defaultValue, "NULL")
            || StringUtils.equalsIgnoreCase(defaultValue, "CURRENT_TIMESTAMP")
            || StringUtils.startsWithIgnoreCase(defaultValue, "b'")
            || StringUtils.startsWithIgnoreCase(defaultValue, "x'")
            || StringUtils.startsWithIgnoreCase(defaultValue, "0x")) {
            needQuota = false;
        }
        return needQuota;
    }

    private String getRandomDefaultValue(List<String> list) {
        if (list.size() == 1) {
            return list.get(0);
        } else {
            int index = RandomUtils.nextInt(0, list.size());
            return list.get(index);
        }
    }
}
