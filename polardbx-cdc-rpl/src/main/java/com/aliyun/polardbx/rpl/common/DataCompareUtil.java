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
package com.aliyun.polardbx.rpl.common;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.Date;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math.util.MathUtils;

/**
 * @author 燧木
 */
public class DataCompareUtil {
    /**
     * 比较源值与目标值是否相同
     *
     * @return boolean
     */
    public static boolean compareValue(Object sourceValue, int sourceColumnType, Object targetValue,
                                       int targetColumnType) {
        if (sourceValue == null && targetValue == null) {
            return true;
        }

        if (sourceValue != null && targetValue != null) {
            if (sourceValue.equals(targetValue)) {
                return true;
            }
            // custom define
            if (sourceValue instanceof java.util.Date && targetValue instanceof java.util.Date) {
                boolean result = dateValueEquals((java.util.Date) sourceValue, (java.util.Date) targetValue);
                if (!result) {
                    String v1 = getDateString(sourceValue);
                    String v2 = getDateString(targetValue);
                    // 2012-02-02 02:02:02 与 2012-02-02 肯定是一种包含关系
                    if ((v1.contains(v2) || v2.contains(v1))) {
                        return true;
                    } else {
                        // 考虑精度差一秒,四舍五入
                        if (isDateRoundOneSencond(v1, v2)) {
                            return true;
                        }
                        return false;
                    }
                } else {
                    return true;
                }
            } else if (isDateType(sourceColumnType) && isDateType(targetColumnType)) {
                // 2012-02-02 02:02:02 与 2012-02-02 肯定是一种包含关系
                String v1 = getDateString(sourceValue);
                String v2 = getDateString(targetValue);
                if ((v1.contains(v2) || v2.contains(v1))) {
                    return true;
                } else {
                    // 考虑精度差一秒,四舍五入
                    if (isDateRoundOneSencond(v1, v2)) {
                        return true;
                    }

                    return false;
                }
            } else if (Number.class.isAssignableFrom(sourceValue.getClass())
                && Number.class.isAssignableFrom(targetValue.getClass())) {
                String v1 = getNumberString(sourceValue);
                String v2 = getNumberString(targetValue);
                boolean result = v1.equals(v2);
                if (!result) {
                    // 如果出现浮点数,使用浮点计数法再对比一次
                    if (isRealType(sourceColumnType) || isRealType(targetColumnType)) {
                        v1 = getRealString(sourceValue);
                        v2 = getRealString(targetValue);
                        result = v1.equals(v2);
                        if (!result) {
                            return compareBigDecimalStr(v1, v2);
                        }
                    }
                }

                return result;
            } else if (sourceValue.getClass().isArray() && targetValue.getClass().isArray()) {
                boolean result = true;
                if (Array.getLength(sourceValue) == Array.getLength(targetValue)) {
                    int length = Array.getLength(sourceValue);
                    for (int i = 0; i < length; i++) {
                        result &= ObjectUtils.equals(Array.get(sourceValue, i), Array.get(targetValue, i));
                    }

                    if (result) {
                        return true;
                    }
                }

                return false;
            } else if (sourceColumnType == Types.BOOLEAN || sourceColumnType == Types.BIT
                || targetColumnType == Types.BOOLEAN || targetColumnType == Types.BIT) {

                String boolStr, theOtherStr;
                if (sourceColumnType == Types.BOOLEAN || sourceColumnType == Types.BIT) {
                    boolStr = sourceValue.toString();
                    theOtherStr = targetValue.toString();
                } else {
                    boolStr = targetValue.toString();
                    theOtherStr = sourceValue.toString();
                }

                if (isTrueValue(boolStr) && isTrueValue(theOtherStr)) {
                    return true;
                }
                if (isFalseValue(boolStr) && isFalseValue(theOtherStr)) {
                    return true;
                }

                return false;
            }

            // 通用比较
            boolean result = sourceValue.toString().equals(targetValue.toString());
            if (result) {
                return true;
            }

            if (isNumberType(sourceColumnType) && isNumberType(targetColumnType)) {
                String v1 = getNumberString(sourceValue);
                String v2 = getNumberString(targetValue);
                return v1.equals(v2);
            }

            // 特殊情况都匹配不上
            return false;
        } else {
            // 可能一个为空,另一个不为空的情况
            return false;
        }
    }

    private static boolean isTrueValue(String value) {
        return ("true".equalsIgnoreCase(value) || "1".equals(value));
    }

    private static boolean isFalseValue(String value) {
        return ("false".equalsIgnoreCase(value) || "0".equals(value));
    }

    private static String getNumberString(Object sourceValue) {
        String v1;
        if (sourceValue instanceof BigDecimal) {
            v1 = ((BigDecimal) sourceValue).toPlainString();
        } else if (sourceValue instanceof Float) {
            v1 = new BigDecimal(sourceValue.toString()).toPlainString();
        } else if (sourceValue instanceof Double) {
            v1 = new BigDecimal((double) sourceValue).toPlainString();
        } else {
            v1 = sourceValue.toString();
        }

        if (StringUtils.indexOf(v1, ".") != -1) {
            v1 = StringUtils.stripEnd(v1, "0");// 如果0是末尾，则删除之
            v1 = StringUtils.stripEnd(v1, ".");// 如果.是末尾，则删除之
        }
        return v1;
    }

    private static String getRealString(Object sourceValue) {
        String v1;
        if (sourceValue instanceof BigDecimal) {
            v1 = String.valueOf(((BigDecimal) sourceValue).doubleValue());
        } else {
            v1 = String.valueOf(new BigDecimal(sourceValue.toString()).doubleValue());
        }

        if (StringUtils.indexOf(v1, ".") != -1) {
            v1 = StringUtils.stripEnd(v1, "0");// 如果0是末尾，则删除之
            v1 = StringUtils.stripEnd(v1, ".");// 如果.是末尾，则删除之
        }
        return v1;
    }

    private static String getDateString(Object date) {
        // 处理一下2017-01-01 22:00:00.00
        String str = date.toString();
        if (StringUtils.indexOf(str, ".") != -1) {
            str = StringUtils.stripEnd(str, "0");// 如果0是末尾，则删除之
            str = StringUtils.stripEnd(str, ".");// 如果.是末尾，则删除之
        }
        return str;
    }

    private static boolean dateValueEquals(Date source, Date target) {
        return source.getTime() == target.getTime();
    }

    private static boolean isDateType(int type) {
        switch (type) {
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    private static boolean isNumberType(int type) {
        switch (type) {
        case Types.TINYINT:
        case Types.SMALLINT:
        case Types.INTEGER:
        case Types.BIGINT:
        case Types.NUMERIC:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.REAL:
            return true;
        default:
            return false;
        }
    }

    private static boolean isRealType(int type) {
        switch (type) {
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.REAL:
        case 100:
        case 101:
            return true;
        default:
            return false;
        }
    }

    private static boolean compareBigDecimalStr(String source, String target) {
        if (StringUtils.equals(source, target)) {
            return true;
        }
        if (source == null || target == null) {
            return false;
        }
        // 精度
        int srcPrecision = 0;
        int tgtPrecision = 0;
        String sourceVal = source;
        String targetVal = target;
        // 截取E的前部分
        if (source.contains(".") && source.contains("E")) {
            srcPrecision = StringUtils.substringBetween(source, ".", "E").length();
            sourceVal = StringUtils.substring(source, 0, source.indexOf("E"));
        }
        if (target.contains(".") && target.contains("E")) {
            tgtPrecision = StringUtils.substringBetween(target, ".", "E").length();
            targetVal = StringUtils.substring(target, 0, target.indexOf("E"));
        }
        if (StringUtils.equals(sourceVal, targetVal)) {
            return true;
        }
        // 根据源数据精度进行折算
        if (srcPrecision != 0 && tgtPrecision >= srcPrecision) {
            targetVal = String.valueOf(MathUtils.round(Double.valueOf(targetVal), srcPrecision));
            return sourceVal.equals(targetVal);
        }
        return StringUtils.equals(source, target);
    }

    private static boolean isDateRoundOneSencond(String source, String target) {
        Date v1 = DateParseUtil.str_to_time(source);
        Date v2 = DateParseUtil.str_to_time(target);
        //异常情况 返回false
        if (v1 == null || v2 == null) {
            return false;
        }
        long vs1 = v1.getTime() / 1000;
        long vs2 = v2.getTime() / 1000;
        return Math.abs(vs1 - vs2) == 1;
    }
}
