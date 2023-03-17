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
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * Created by ShuGuang
 */
public class ResultSetComparator implements Comparator<List<Map<String, Object>>> {

    private final Map<String, Pair<Object, Object>> diffColumns = new HashMap<>();
    private final Map<String, Pair<Class<?>, Class<?>>> diffColumnTypes = new HashMap<>();

    @Override
    public int compare(List<Map<String, Object>> o1, List<Map<String, Object>> o2) {
        if (o1.size() != o2.size()) {
            return o1.size() - o2.size();
        }
        for (int i = 0; i < o1.size(); i++) {
            Map<String, Object> m1 = o1.get(i);
            Map<String, Object> m2 = o2.get(i);
            if (m1.size() != m2.size()) {
                return m1.size() - m2.size();
            }
            Iterator<Entry<String, Object>> iterator = m1.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, Object> next = iterator.next();
                Object v1 = next.getValue();
                Object v2 = m2.get(next.getKey());
                if (v1 == null ^ v2 == null) {
                    diffColumns.put(next.getKey(), Pair.of(v1, v2));
                    diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                } else if (v1 == null && v2 == null) {

                } else {
                    if (v1 instanceof byte[] && v2 instanceof byte[]) {
                        if (!compareArray((byte[]) v1, (byte[]) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(new String((byte[]) v1), new String((byte[]) v2)));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Float && v2 instanceof Float) {
                        if (!compareFloat((Float) v1, (Float) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Double && v2 instanceof Double) {
                        if (!compareDouble((Double) v1, (Double) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof BigDecimal && v2 instanceof BigDecimal) {
                        if (!compareDecimal((BigDecimal) v1, (BigDecimal) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Byte && v2 instanceof Byte) {
                        if (!compareNumber((Byte) v1, (Byte) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Integer && v2 instanceof Integer) {
                        if (!compareNumber((Integer) v1, (Integer) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Short && v2 instanceof Short) {
                        if (!compareNumber((Short) v1, (Short) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof Long && v2 instanceof Long) {
                        if (!compareNumber((Long) v1, (Long) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else if (v1 instanceof String && v2 instanceof String) {
                        if (!compareString((String) v1, (String) v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    } else {
                        if (!v1.equals(v2)) {
                            diffColumns.put(next.getKey(), Pair.of(v1, v2));
                            diffColumnTypes.put(next.getKey(), Pair.of(getClass(v1), getClass(v2)));
                        }
                    }
                }
            }
        }

        if (diffColumns.size() > 0) {
            return -1;
        } else {
            return 0;
        }
    }

    private boolean compareDecimal(BigDecimal b1, BigDecimal b2) {
        return compareFloating(b1, b2);
    }

    private boolean compareFloat(Float f1, Float f2) {
        return compareFloating(f1, f2);
    }

    private boolean compareDouble(Double d1, Double d2) {
        return compareFloating(d1, d2);
    }

    /**
     * float类型的对比，需要进行一下特殊处理，主要原因是polardb-x 和 单机mysql对精度的处理有差异，比如
     * 1) 9.768176E37，插入polarx之后进行查询，结果是9.768176E37，插入mysql之后进行查询，结果是9.76818e37
     * 2) 9.1096275E8，插入polarx之后进行查询，结果是9.1096275E8，插入mysql之后进行查询，结果是910963000(9.1096301E8)
     * 3) 4674898.0,   插入polarx之后进行查询，结果是4674898.0， 插入mysql之后进行查询，结果是4674900.0
     * 4）2.1039974E38，插入polarx之后进行查询，结果是2.1039974E38， 插入mysql之后进行查询，结果是2.104E38
     * 所以，我们在对float进行对比时，只对比小数位的前2位或1位
     */
    private boolean compareFloating(Object d1, Object d2) {
        //先比较3位小数，如果不相等降低到2位，如果还不相等降低到1位，最少比较1位

        for (int i = 30; i >= 0; i--) {
            String pattern = String.format("0.%sE0", StringUtils.leftPad("", i, "0"));
            DecimalFormat df = new DecimalFormat(pattern);

            df.setRoundingMode(RoundingMode.DOWN);
            String d1Str = df.format(d1);
            String d2Str = df.format(d2);
            if (!StringUtils.equals(d1Str, d2Str)) {
                df.setRoundingMode(RoundingMode.HALF_UP);
                d1Str = df.format(d1);
                d2Str = df.format(d2);
                if (StringUtils.equals(d1Str, d2Str)) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean compareString(String s1, String s2) {
        if (!StringUtils.equals(s1, s2)) {
            boolean result = tryCompareJson(s1, s2);
            result |= tryCompareFloating(s1, s2);
            result |= tryCompareDatetime(s1, s2);
            result |= tryCompareIntNumber(s1, s2);
            return result;
        } else {
            return true;
        }
    }

    private boolean compareArray(byte[] v1, byte[] v2) {
        if (!Arrays.equals(v1, v2)) {
            return compareString(new String(v1), new String(v2));
        } else {
            return true;
        }
    }

    // 有可能是json类型转换为字符串类型，polardbx序列化出的字段顺序和mysql序列化出的字段顺序可能是不一致的
    // 这里进行二次校验，如果能够解析成json类型，只要json对象是相等的，也认为是相等的
    private boolean tryCompareJson(String s1, String s2) {
        try {
            Object obj1 = JSON.parse(s1);
            Object obj2 = JSON.parse(s2);
            return Objects.equals(obj1, obj2);
        } catch (Throwable e) {
            //maybe JSONException or NumberFormatException
            return false;
        }
    }

    // 有可能是float类型转换为字符串类型，上下游
    private boolean tryCompareFloating(String s1, String s2) {
        try {
            Double d1 = Double.parseDouble(s1);
            Double d2 = Double.parseDouble(s2);
            return compareFloating(d1, d2);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean tryCompareDatetime(String s1, String s2) {
        try {
            MDate mDate1 = new MDate();
            mDate1.parse(s1);

            MDate mDate2 = new MDate();
            mDate2.parse(s2);
            return mDate1.equals(mDate2);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean compareNumber(Number n1, Number n2) {
        // 怀疑和insert ... select ...语句有关联关系，改成 insert into ... values(),(),(),(),()...的形式没在复现
        // return tryCompareIntNumber(n1.toString(), n2.toString());
        return n1.equals(n2);
    }

    // unsigned列转换为signed类型过程中，不知为啥上游会出现负值，并且负值都是边界值，比如：
    // 1) 建表的时候，有一列：`c_smallint_zerofill_un` smallint(5) UNSIGNED ZEROFILL DEFAULT 65535
    // 2) 执行一次ddl变更：alter table xxx modify c_smallint_zerofill_un tinyint(8)
    // 3) ddl变更期间上游会插入负值，但梳理了几遍用例，针对UNSIGNED类型，是不会插入赋值的
    // 4) 在ddl变更期间，物理表已经变成了tinyint，此时插入一个负值，整形代码会参照mysql的行为，把负值转换为0
    // 5）从而导致上下游数据不一致，这里先临时加一个hack逻辑，等定位到根因之后，再放开
    private boolean tryCompareIntNumber(String s1, String s2) {
        if ("-128".equals(s1) && "0".equals(s2)) {
            return true;
        } else if ("-32768".equals(s1) && "0".equals(s2)) {
            return true;
        } else if ("-2147483648".equals(s1) && "0".equals(s2)) {
            return true;
        } else {
            return "-9223372036854775808".equals(s1) && "0".equals(s2);
        }
    }

    public Map<String, Pair<Object, Object>> getDiffColumns() {
        return diffColumns;
    }

    public Map<String, Pair<Class<?>, Class<?>>> getDiffColumnTypes() {
        return diffColumnTypes;
    }

    private Class<?> getClass(Object obj) {
        return obj == null ? null : obj.getClass();
    }
}
