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

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.cdc.qatest.base.ConnectionManager;
import com.aliyun.polardbx.cdc.qatest.base.JdbcUtil;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isBit;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isBoolean;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isGeometry;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isTextOrBlob;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isTime;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.isVarCharOrBinary;
import static com.aliyun.polardbx.cdc.qatest.random.ColumnTypeUtil.parseLength;

/**
 * created by ziyang.lb
 **/
public class DmlSqlBuilder {
    private final String dbName;
    private final String tableName;
    private final ColumnSeeds columnSeeds;
    private final boolean useStringMode4TimeType;

    public DmlSqlBuilder(String dbName, String tableName, ColumnSeeds columnSeeds, boolean useStringMode4TimeType) {
        this.dbName = dbName;
        this.tableName = tableName;
        this.columnSeeds = columnSeeds;
        this.useStringMode4TimeType = useStringMode4TimeType;
    }

    private static Object buildRandomText(String columnType) {
        if (columnType.startsWith("tinytext") || columnType.startsWith("tinyblob")) {
            return RandomStringUtils.randomAlphabetic(128);
        } else {
            return RandomStringUtils.randomAscii(1024);
        }
    }

    Pair<String, List<Pair<String, Object>>> buildInsertSql(boolean useRandomColumn4Dml) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);

        List<Pair<String, Object>> parameter = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("insert into ").append(tableName).append("(");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            sb.append("`").append(entry.getKey()).append("`");
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(")values(");

        // build column values
        count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            if (isGeometry(entry.getValue())) {
                sb.append(buildRandomGeometry());
            } else {
                sb.append("?");
                parameter.add(Pair.of(entry.getKey(), randomValue(entry.getValue())));
            }

            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(")");

        return Pair.of(sb.toString(), parameter);
    }

    String buildInsertBatchSql(boolean useRandomColumn4Dml, int dmlBatchLimitNum) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);
        Pair<Integer, Integer> idRange = randomRange();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("insert into ").append(tableName).append("(");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            sb.append("`").append(entry.getKey()).append("`");
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(") select ");

        // build column values
        count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            sb.append("`").append(entry.getKey()).append("`");
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(" from ");
        sb.append(tableName);
        sb.append(" where id >= ");
        sb.append(idRange.getKey());
        sb.append(" and id <= ");
        sb.append(idRange.getValue());
        sb.append(" limit ").append(dmlBatchLimitNum);

        return sb.toString();
    }

    Pair<String, List<Pair<String, Object>>> buildInsertBatchSql2(boolean useRandomColumn4Dml, int dmlBatchLimitNum) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);
        List<Pair<String, Object>> parameter = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("insert into ").append(tableName).append("(");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            sb.append("`").append(entry.getKey()).append("`");
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(") values ");

        // build column values
        for (int i = 0; i < dmlBatchLimitNum; i++) {
            sb.append("(");
            count = 0;
            for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
                if (isGeometry(entry.getValue())) {
                    sb.append(buildRandomGeometry());
                } else {
                    sb.append("?");
                    parameter.add(Pair.of(entry.getKey(), randomValue(entry.getValue())));
                }

                if (count < randomMapping.size() - 1) {
                    sb.append(",");
                }
                count++;
            }

            sb.append(")");
            if (i < dmlBatchLimitNum - 1) {
                sb.append(",");
            }
        }

        return Pair.of(sb.toString(), parameter);
    }

    Pair<String, List<Pair<String, Object>>> buildUpdateSql(boolean useRandomColumn4Dml) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);
        List<Pair<String, Object>> parameter = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("update ").append(tableName).append(" set ");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            if (isGeometry(entry.getValue())) {
                sb.append(" `").append(entry.getKey()).append("` =  ").append(buildRandomGeometry());
            } else {
                sb.append(" `").append(entry.getKey()).append("` = ? ");
                parameter.add(Pair.of(entry.getKey(), randomValue(entry.getValue())));
            }

            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(" where id = ");
        sb.append(getMaxId());

        return Pair.of(sb.toString(), parameter);
    }

    Pair<String, List<Pair<String, Object>>> buildUpdateBatchSql(boolean useRandomColumn4Dml,
                                                                 int dmlBatchLimitNum) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);
        Pair<Integer, Integer> idRange = randomRange();
        List<Pair<String, Object>> parameter = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("update ").append(tableName).append(" set ");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            if (isGeometry(entry.getValue())) {
                sb.append(" `").append(entry.getKey()).append("` =  ").append(buildRandomGeometry());
            } else {
                sb.append(" `").append(entry.getKey()).append("` = ?");
                parameter.add(Pair.of(entry.getKey(), randomValue(entry.getValue())));
            }
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(" where id >= ");
        sb.append(idRange.getKey());
        sb.append(" and id<= ");
        sb.append(idRange.getValue());
        sb.append(" limit ").append(dmlBatchLimitNum);

        return Pair.of(sb.toString(), parameter);
    }

    Pair<String, List<Pair<String, Object>>> buildUpdateBatchSql2(boolean useRandomColumn4Dml,
                                                                  int dmlBatchLimitNum) {
        Map<String, String> randomMapping = buildRandomColumnMapping(useRandomColumn4Dml);
        Pair<Integer, Integer> idRange = randomRange();
        Set<Integer> ids = getIds(idRange.getKey(), idRange.getValue(), dmlBatchLimitNum);
        List<Pair<String, Object>> parameter = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        // build column names
        sb.append("update ").append(tableName).append(" set ");
        int count = 0;
        for (Map.Entry<String, String> entry : randomMapping.entrySet()) {
            if (isGeometry(entry.getValue())) {
                sb.append(" `").append(entry.getKey()).append("` =  ").append(buildRandomGeometry());
            } else {
                sb.append(" `").append(entry.getKey()).append("` = ?");
                parameter.add(Pair.of(entry.getKey(), randomValue(entry.getValue())));
            }
            if (count < randomMapping.size() - 1) {
                sb.append(",");
            }
            count++;
        }

        // build where in
        count = 0;
        sb.append(" where id in (");
        for (Integer id : ids) {
            sb.append(id);
            if (count < ids.size() - 1) {
                sb.append(",");
            }
            count++;
        }
        sb.append(")");

        return Pair.of(sb.toString(), parameter);
    }

    String buildDeleteSql() {
        return "delete from " + tableName + " where id = " + getMinId();
    }

    String buildDeleteBatchSql(int dmlBatchLimitNum) {
        Pair<Integer, Integer> idRange = randomRange();
        return "delete from " + tableName + " where id >= " + idRange.getKey() + " and id<= " + idRange
            .getValue() + " limit " + dmlBatchLimitNum;
    }

    private Map<String, String> buildRandomColumnMapping(boolean useRandomColumn4Dml) {
        Map<String, String> result;
        if (useRandomColumn4Dml) {
            ArrayList<Map.Entry<String, String>> list =
                new ArrayList<>(columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING.entrySet());
            Collections.shuffle(list);
            int count = RandomUtils.nextInt(10, list.size());

            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < count; i++) {
                map.put(list.get(i).getKey(), list.get(i).getValue());
            }
            result = map;
        } else {
            result = Maps.newHashMap(columnSeeds.COLUMN_NAME_COLUMN_TYPE_MAPPING);
        }
        result.put("c_idx", "bigint");
        return result;
    }

    private Object randomValue(String columnType) {
        if (isTime(columnType)) {
            return buildRandomTime(columnType);
        } else if (isVarCharOrBinary(columnType)) {
            return RandomStringUtils.randomAlphabetic(parseLength(columnType).getKey());
        } else if (isTextOrBlob(columnType)) {
            return buildRandomText(columnType);
        } else if (columnType.startsWith("tinyint")) {
            return buildRandomInt(columnType, 255, 128);
        } else if (columnType.startsWith("smallint")) {
            return buildRandomInt(columnType, 65535, 32768);
        } else if (columnType.startsWith("mediumint")) {
            return buildRandomInt(columnType, 16777215, 8388608);
        } else if (columnType.startsWith("int")) {
            return RandomUtils.nextInt();
        } else if (columnType.startsWith("bigint")) {
            return buildRandomBigInt();
        } else if (columnType.startsWith("double")) {
            return buildRandomDouble(columnType);
        } else if (columnType.startsWith("float")) {
            return buildRandomFloat(columnType);
        } else if (columnType.startsWith("decimal") || columnType.startsWith("numeric")
            || columnType.startsWith("dec")) {
            return buildRandomBigDecimal(columnType);
        } else if (isBit(columnType)) {
            return buildRandomBits(parseLength(columnType).getKey(), new Random());
        } else if (columnType.startsWith("enum") || columnType.startsWith("set")) {
            return buildRandomEnum(columnType);
        } else if (columnType.startsWith("json")) {
            return buildRandomJson();
        } else if (isBoolean(columnType)) {
            return new Random().nextBoolean() ? 1 : 0;
        } else {
            throw new PolardbxException("unsupported column type " + columnType);
        }
    }

    // 当需要对上下游数据进行校验时，需要对部分时间类型进行字符串格式化处理，否则当触发整形时，可能会出现上下游数据不一致的情况，如
    // 1) timestamp类型转换为mediumtext类型时，上游部分物理表已经变更为mediumtext类型，此时直接插入new Date(),
    //    表中保存的数据格式为yyyy-MM-dd HH:mm:ss.SSS，这些数据被整形为yyyy-MM-dd HH:mm:ss的形式，导致上下游数据不一致
    // 2) 更典型的例子是date类型，上游部分物理表已经变更为mediumtext类型，此时直接插入new Date(),表中保存的数据格式为
    //    yyyy-MM-dd HH:mm:ss.SSS，这些数据被整形为yyyy-MM-dd的形式，上下游差异更大
    private Object buildRandomTime(String columnType) {
        if (columnType.startsWith("year")) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            return calendar.get(Calendar.YEAR);
        } else if (useStringMode4TimeType && columnType.startsWith("timestamp")) {
            return DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss");
        } else if (useStringMode4TimeType && columnType.equalsIgnoreCase("date")) {
            return DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd");
        } else if (useStringMode4TimeType && columnType.startsWith("datetime")) {
            if (columnType.startsWith("datetime(")) {
                int length = parseLength(columnType).getKey();
                return DateFormatUtils.format(System.currentTimeMillis(),
                    String.format("yyyy-MM-dd HH:mm:ss.%s", StringUtils.rightPad("", length, "S")));
            } else {
                return DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss");
            }
        } else if (useStringMode4TimeType && columnType.equalsIgnoreCase("time")) {
            return DateFormatUtils.format(System.currentTimeMillis(), "HH:mm:ss");
        } else if (useStringMode4TimeType && columnType.startsWith("time(")) {
            int length = parseLength(columnType).getKey();
            return DateFormatUtils.format(System.currentTimeMillis(),
                String.format("HH:mm:ss.%s", StringUtils.rightPad("", length, "S")));
        } else {
            return new Date();
        }
    }

    private Object buildRandomEnum(String columnType) {
        columnType = StringUtils.substringBefore(columnType, ")");
        columnType = StringUtils.substringAfter(columnType, "(");
        columnType = StringUtils.replace(columnType, "'", "");
        String[] array = StringUtils.split(columnType, ",");
        int index = new Random().nextInt(array.length);
        return array[index];
    }

    private Integer buildRandomInt(String columnType, int x, int y) {
        if (StringUtils.containsIgnoreCase(columnType, "unsigned")) {
            return new Random().nextInt(x);
        } else {
            int i = new Random().nextInt(y);
            return new Random().nextBoolean() ? i : i * -1;
        }
    }

    private Object buildRandomBigInt() {
        return new BigInteger(new Random().nextInt(64), new Random());
    }

    private byte[] buildRandomBits(int numBits, Random rnd) {
        if (numBits < 0) {
            throw new IllegalArgumentException("numBits must be non-negative");
        }
        int numBytes = (int) (((long) numBits + 7) / 8); // avoid overflow
        byte[] randomBits = new byte[numBytes];

        // Generate random bytes and mask out any excess bits
        if (numBytes > 0) {
            rnd.nextBytes(randomBits);
            int excessBits = 8 * numBytes - numBits;
            randomBits[0] &= (1 << (8 - excessBits)) - 1;
        }
        return randomBits;
    }

    private String buildRandomJson() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("name", RandomStringUtils.randomAlphanumeric(10));
        valueMap.put("age", RandomUtils.nextInt(4, 100));
        valueMap.put("birthday", new Date());
        return JSON.toJSONString(valueMap);
    }

    private String buildRandomGeometry() {
        return "ST_GeomFromText('POINT(" + RandomUtils.nextFloat() + " " + RandomUtils.nextFloat() + ")')";
    }

    private double buildRandomDouble(String columnType) {
        if (StringUtils.startsWithIgnoreCase(columnType, "double(")) {
            Pair<Integer, Integer> pair = parseLength(columnType);
            int M = pair.getKey();
            int D = pair.getValue();
            long max1 = Long.parseLong(StringUtils.leftPad("", M - D, "9"));
            long max2 = D == 0 ? 0L : Long.parseLong(StringUtils.leftPad("", D, "9"));
            String value1 = String.valueOf(RandomUtils.nextLong(0, max1));
            String value2 = D == 0 ? "" : "." + RandomUtils.nextLong(0, max2);
            return Double.parseDouble(value1 + value2);
        } else {
            return RandomUtils.nextDouble();
        }
    }

    private float buildRandomFloat(String columnType) {
        if (StringUtils.startsWithIgnoreCase(columnType, "float(")) {
            Pair<Integer, Integer> pair = parseLength(columnType);
            int M = pair.getKey();
            int D = pair.getValue();
            long max1 = Long.parseLong(StringUtils.leftPad("", M - D, "9"));
            long max2 = D == 0 ? 0L : Long.parseLong(StringUtils.leftPad("", D, "9"));
            String value1 = String.valueOf(RandomUtils.nextLong(0, max1));
            String value2 = D == 0 ? "" : "." + RandomUtils.nextLong(0, max2);
            return Float.parseFloat(value1 + value2);
        } else {
            return RandomUtils.nextFloat();
        }
    }

    private BigDecimal buildRandomBigDecimal(String columnType) {
        int M;
        int D;
        if (StringUtils.startsWithIgnoreCase(columnType, "decimal(")) {
            Pair<Integer, Integer> pair = parseLength(columnType);
            M = pair.getKey();
            D = pair.getValue();
        } else {
            M = 10;
            D = 0;
        }
        long max1 = Long.parseLong(StringUtils.leftPad("", M - D, "9"));
        long max2 = D == 0 ? 0L : Long.parseLong(StringUtils.leftPad("", D, "9"));
        String value1 = String.valueOf(RandomUtils.nextLong(0, max1));
        String value2 = D == 0 ? "" : "." + RandomUtils.nextLong(0, max2);
        return new BigDecimal(value1 + value2);
    }

    @SneakyThrows
    private Pair<Integer, Integer> randomRange() {
        int minId = getMinId();
        int maxId = getMaxId();
        return Pair.of(RandomUtils.nextInt(minId, maxId), maxId);
    }

    @SneakyThrows
    private Integer getMinId() {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeQuery("use " + dbName, connection);
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("select min(id) from " + tableName);
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new PolardbxException("get max id failed");
    }

    @SneakyThrows
    private Integer getMaxId() {
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeQuery("use " + dbName, connection);
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("select max(id) from " + tableName);
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new PolardbxException("get max id failed");
    }

    @SneakyThrows
    private Set<Integer> getIds(Integer min, Integer max, int limit) {
        Set<Integer> set = new HashSet<>();
        try (Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            JdbcUtil.executeQuery("use " + dbName, connection);
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(
                "select id from " + tableName + " where id >= " + min + " and id <= " + max + " limit " + limit);
            while (rs.next()) {
                set.add(rs.getInt(1));
            }
        }
        return set;
    }
}
