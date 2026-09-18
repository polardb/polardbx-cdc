/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * MYSQL_TYPE_JSON
 */
public class JsonField extends BlobField {

    private static final int SMALL_OFFSET_SIZE = 2;
    private static final int LARGE_OFFSET_SIZE = 4;

    private static final int KEY_ENTRY_SIZE_SMALL = 2 + SMALL_OFFSET_SIZE;
    private static final int KEY_ENTRY_SIZE_LARGE = 2 + LARGE_OFFSET_SIZE;

    private static final int VALUE_ENTRY_SIZE_SMALL = 1 + SMALL_OFFSET_SIZE;
    private static final int VALUE_ENTRY_SIZE_LARGE = 1 + LARGE_OFFSET_SIZE;

    public JsonField(CreateField createField) throws InvalidInputDataException {
        super(createField);

        // prepare and check
        check();

        if (!isNull()) {
            // parse and build
            String value = buildDataStr();
            Object jsonObject = JSON.parse(value);
            AutoExpandBuffer buffer = new AutoExpandBuffer(1024, 1024);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte) 0);
            serialJsonValue(buffer, 0, jsonObject);
            int pos = buffer.position();
            contents = new byte[pos];
            buffer.writeTo(contents);
            fieldLength = pos;
        }
        calculatePackLength();
    }

    private void check() throws InvalidInputDataException {
        if (!isNull()) {
            // 正常应该都可以parse过，但在执行modify column时，数据可能被截断，则会出现解析失败，如：
            // 将json类型调整为varchar类型，alter table t_1 modify column c_json varchar(10)
            // 部分物理表已经变更为varchar，此时进行插入，完整的json数据会被截断，此处拿到截断的数据，就会报错
            String value = buildDataStr();
            try {
                JSON.parse(value);
            } catch (JSONException e) {
                throw new InvalidInputDataException("invalid input data : " + value, e);
            }
        }
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_JSON;
    }

    /**
     * 直接简单用short的最大值来评估是否是大对象,大对象会对属性长度使用4个字节记录长度，比小对象多2个字节，不会影响数据正确性
     **/
    private boolean isLargeTest(long len) {
        return len >= Short.MAX_VALUE;
    }

    private void serialJsonValue(AutoExpandBuffer buffer, int typeOffset, Object o) {

        if (o instanceof BigInteger) {
            // fastjson对超出long范围的整数解析为BigInteger: 可容纳于long时降级为Long,
            // (Long.MAX_VALUE, UINT64_MAX]区间保留BigInteger由下方分支编码为UINT64(与MySQL一致),
            // 超出UINT64_MAX则走decimal编码
            BigInteger bi = (BigInteger) o;
            if (bi.bitLength() < 64) {
                o = bi.longValue();
            } else if (bi.signum() > 0 && bi.bitLength() == 64) {
                // (Long.MAX_VALUE, UINT64_MAX]区间，保留BigInteger，由下方分支编码为UINT64
            } else {
                o = new BigDecimal(bi);
            }
        }

        if (o instanceof Float) {
            o = ((Float) o).doubleValue();
        }

        if (o instanceof Long) {
            Long i = (Long) o;
            if (i < Short.MAX_VALUE) {
                o = i.shortValue();
            } else if (i < Integer.MAX_VALUE) {
                o = i.intValue();
            }
        }

        if (o instanceof Integer) {
            Integer i = (Integer) o;
            if (i < Short.MAX_VALUE) {
                o = i.shortValue();
            }
        }

        boolean isLarge = isLargeTest(Objects.toString(o).getBytes(charset).length);

        if (o instanceof JSONObject) {
            byte type;
            if (isLarge) {
                type = (byte) JsonConversion.JSONB_TYPE_LARGE_OBJECT;
            } else {
                type = (byte) JsonConversion.JSONB_TYPE_SMALL_OBJECT;
            }
            buffer.put(typeOffset, type);
            serialJsonObject(buffer, (JSONObject) o, isLarge);
        } else if (o instanceof JSONArray) {
            byte type;
            if (isLarge) {
                type = (byte) JsonConversion.JSONB_TYPE_LARGE_ARRAY;
            } else {
                type = (byte) JsonConversion.JSONB_TYPE_SMALL_ARRAY;
            }
            buffer.put(typeOffset, type);
            serialJsonArray(buffer, (JSONArray) o, isLarge);
        } else if (o instanceof String) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_STRING);
            String variableString = (String) o;
            byte[] stringData = variableString.getBytes(charset);
            int length = stringData.length;
            do {
                byte ch = (byte) (length & 0x7F);

                length >>= 7;
                if (length != 0) {
                    ch |= 0x80;
                }

                buffer.put(ch);
            } while (length != 0);
            buffer.put(stringData);
        } else if (o instanceof Long) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_INT64);
            buffer.putLong((Long) o);
        } else if (o instanceof Integer) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_INT32);
            buffer.putInt((Integer) o);
        } else if (o instanceof Short) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_INT16);
            buffer.putShort((Short) o);
        } else if (o instanceof BigInteger) {
            // 经前置归一化，此处必为(Long.MAX_VALUE, UINT64_MAX]区间，编码为UINT64，
            // longValue()取低64位即其无符号表示
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_UINT64);
            buffer.putLong(((BigInteger) o).longValue());
        } else if (o instanceof BigDecimal) {
            serialJsonDecimal(buffer, typeOffset, (BigDecimal) o);
        } else if (o instanceof Double) {
            buffer.putDouble((Double) o);
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_DOUBLE);
        } else if (o instanceof Boolean) {
            buffer.put((byte) (((Boolean) o) ? JsonConversion.JSONB_TRUE_LITERAL : JsonConversion.JSONB_FALSE_LITERAL));
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
        } else if (o == null) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.put((byte) JsonConversion.JSONB_NULL_LITERAL);
        } else {
            // 兜底防护：不能静默跳过，否则value entry的type字节保持占位0x00且不写任何数据，
            // 会生成内部offset错乱的损坏JSON（下游无法解析）
            throw new UnsupportedOperationException(
                "unsupported json value type: " + o.getClass().getName() + ", value: " + o);
        }
    }

    /**
     * 将BigDecimal（fastjson对JSON中带小数数值的默认解析类型）编码为MySQL JSONB的OPAQUE decimal格式:
     * JSONB_TYPE_OPAQUE + field_type(1字节, MYSQL_TYPE_NEWDECIMAL) + varint(数据长度) + precision(1字节)
     * + scale(1字节) + my_decimal二进制。
     * 该格式与解码侧JsonConversion的OPAQUE/MYSQL_TYPE_NEWDECIMAL分支对应。
     * <p>
     * 取舍说明：reformat链路经过"JSONB二进制-字符串-重编码"中转，源端JSONB数值类型信息已丢失，
     * DOUBLE与DECIMAL无法区分。此处选择decimal编码，保证数值无损（不受double 17位有效数字限制）；
     * 代价是文本写入的小数JSON_TYPE()会从DOUBLE变为DECIMAL。文本尾零不做保证：解码侧
     * LogBuffer.getDecimal在小数末段全为0时会丢失尾零（如10000.00解出10000.0），数值不受影响。
     * 超出MySQL decimal精度上限(65,30)时退化为JSONB_TYPE_DOUBLE编码。
     */
    private void serialJsonDecimal(AutoExpandBuffer buffer, int typeOffset, BigDecimal decimal) {
        if (decimal.scale() < 0) {
            // 归一化科学计数法（如1E+2），消除负scale
            decimal = decimal.setScale(0);
        }
        int scale = decimal.scale();
        int precision = Math.max(decimal.precision(), scale);
        if (precision > 65 || scale > 30) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_DOUBLE);
            buffer.putDouble(decimal.doubleValue());
            return;
        }

        buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_OPAQUE);
        Field decimalField = MakeFieldFactory.makeField(String.format("decimal(%s,%s)", precision, scale),
            decimal.toPlainString(), "utf8", false, false);
        byte[] decimalData = decimalField.encode();

        buffer.put((byte) MySQLType.MYSQL_TYPE_NEWDECIMAL.getType());
        int length = 2 + decimalData.length;
        do {
            byte ch = (byte) (length & 0x7F);

            length >>= 7;
            if (length != 0) {
                ch |= 0x80;
            }

            buffer.put(ch);
        } while (length != 0);
        buffer.put((byte) precision);
        buffer.put((byte) scale);
        buffer.put(decimalData);
    }

    private void serialJsonObject(AutoExpandBuffer buffer, JSONObject object, boolean large) {
        int elementCount = object.values().size();
        int startPosition = buffer.position();
        int sizePos;
        if (large) {
            buffer.putInt(elementCount);
            sizePos = buffer.position();
            buffer.putInt(0);
        } else {
            buffer.putShort((short) elementCount);
            sizePos = buffer.position();
            buffer.putShort((short) 0);
        }

        int KEY_ENTRY_SIZE = large ? KEY_ENTRY_SIZE_LARGE : KEY_ENTRY_SIZE_SMALL;
        int VALUE_ENTRY_SIZE = large ? VALUE_ENTRY_SIZE_LARGE : VALUE_ENTRY_SIZE_SMALL;

        int first_key_offset =
            buffer.position() + elementCount * (KEY_ENTRY_SIZE + VALUE_ENTRY_SIZE) - startPosition;
        // value entry OFFSET_SIZE + 2
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            int len = entry.getKey().getBytes(charset).length;
            if (large) {
                buffer.putInt(first_key_offset);
            } else {
                buffer.putShort((short) first_key_offset);
            }
            buffer.putShort((short) len);
            first_key_offset += len;
        }
        int mark = buffer.position();
        // value entry 1 + OFFSET_SIZE
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            buffer.put((byte) 0);
            if (large) {
                buffer.putInt(0);
            } else {
                buffer.putShort((short) 0);
            }

        }
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            buffer.put(entry.getKey().getBytes(charset));
        }
        int i = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            int typeOffset = mark + i++ * VALUE_ENTRY_SIZE;
            if (!attemptInlineValue(entry.getValue(), buffer, typeOffset)) {
                if (large) {
                    buffer.putInt(typeOffset + 1, buffer.position() - startPosition);
                } else {
                    buffer.putShort(typeOffset + 1, buffer.position() - startPosition);
                }
                serialJsonValue(buffer, typeOffset, entry.getValue());
            }
        }
        if (large) {
            buffer.putInt(sizePos, buffer.position() - startPosition);
        } else {
            buffer.putShort(sizePos, buffer.position() - startPosition);
        }
    }

    private static boolean attemptInlineValue(Object o, AutoExpandBuffer buffer,
                                              int typeOffset) {
        if (o instanceof Long) {
            long i = (Long) o;
            if (i < Short.MAX_VALUE) {
                o = (short) i;
            } else if (i < Integer.MAX_VALUE) {
                o = (int) i;
            }
        }

        if (o instanceof Integer) {
            Integer i = (Integer) o;
            if (i < Short.MAX_VALUE) {
                o = i.shortValue();
            }
        }
        if (o instanceof Short) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_INT16);
            buffer.putShort(typeOffset + 1, (Short) o);
        } else if (o instanceof Boolean) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.putShort(typeOffset + 1,
                (byte) (((Boolean) o) ? JsonConversion.JSONB_TRUE_LITERAL : JsonConversion.JSONB_FALSE_LITERAL));
        } else if (o == null) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.putShort(typeOffset + 1, (byte) JsonConversion.JSONB_NULL_LITERAL);
        } else {
            return false;
        }

        return true;
    }

    private void serialJsonArray(AutoExpandBuffer buffer, JSONArray array, boolean large) {
        int size = array.size();
        int startPosition = buffer.position();
        int sizePos;
        if (large) {
            buffer.putInt(size);
            sizePos = buffer.position();
            buffer.putInt(0);
        } else {
            buffer.putShort((short) size);
            sizePos = buffer.position();
            buffer.putShort((short) 0);
        }

        int mark = buffer.position();
        for (int i = 0; i < size; i++) {
            if (large) {
                buffer.putInt(0);
            } else {
                buffer.putShort((short) 0);
            }
            buffer.put((byte) 0);
        }

        int VALUE_ENTRY_SIZE = large ? VALUE_ENTRY_SIZE_LARGE : VALUE_ENTRY_SIZE_SMALL;

        for (int i = 0; i < size; i++) {
            Object o = array.get(i);
            int typeOffset = mark + i * VALUE_ENTRY_SIZE;
            if (!attemptInlineValue(o, buffer, typeOffset)) {
                if (large) {
                    buffer.putInt(typeOffset + 1, buffer.position() - startPosition);
                } else {
                    buffer.putShort(typeOffset + 1, (short) (buffer.position() - startPosition));
                }
                serialJsonValue(buffer, typeOffset, o);
            }
        }
        if (large) {
            buffer.putInt(sizePos, buffer.position() - startPosition);
        } else {
            buffer.putShort(sizePos, (short) (buffer.position() - startPosition));
        }
    }

}
