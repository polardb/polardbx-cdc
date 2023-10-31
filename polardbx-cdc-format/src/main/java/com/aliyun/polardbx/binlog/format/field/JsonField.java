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
package com.aliyun.polardbx.binlog.format.field;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.nio.ByteOrder;
import java.util.Map;

/**
 * MYSQL_TYPE_JSON
 */
public class JsonField extends BlobField {

    private static final int SMALL_OFFSET_SIZE = 2;

    private static final int KEY_ENTRY_SIZE_SMALL = 2 + SMALL_OFFSET_SIZE;

    private static final int VALUE_ENTRY_SIZE_SMALL = 1 + SMALL_OFFSET_SIZE;

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

    private void serialJsonValue(AutoExpandBuffer buffer, int typeOffset, Object o) {

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

        if (o instanceof JSONObject) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_SMALL_OBJECT);
            serialJsonObject(buffer, (JSONObject) o);
        } else if (o instanceof JSONArray) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_SMALL_ARRAY);
            serialJsonArray(buffer, (JSONArray) o);
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
        } else if (o instanceof Double) {
            buffer.putDouble((Double) o);
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_DOUBLE);
        } else if (o instanceof Boolean) {
            buffer.put((byte) (((Boolean) o) ? JsonConversion.JSONB_TRUE_LITERAL : JsonConversion.JSONB_FALSE_LITERAL));
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
        } else if (o == null) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.put((byte) JsonConversion.JSONB_NULL_LITERAL);
        }
    }

    private void serialJsonObject(AutoExpandBuffer buffer, JSONObject object) {
        int size = object.values().size();
        int startPosition = buffer.position();
        buffer.putShort((short) size);
        int sizePos = buffer.position();
        buffer.putShort((short) 0);

        int first_key_offset =
            buffer.position() + size * (KEY_ENTRY_SIZE_SMALL + VALUE_ENTRY_SIZE_SMALL) - startPosition;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            int len = entry.getKey().getBytes(charset).length;
            buffer.putShort((short) first_key_offset);
            buffer.putShort((short) len);
            first_key_offset += len;
        }
        int mark = buffer.position();
        // value entry
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            buffer.put((byte) 0);
            buffer.putShort((short) 0);
        }
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            buffer.put(entry.getKey().getBytes(charset));
        }
        int i = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            int typeOffset = mark + i++ * VALUE_ENTRY_SIZE_SMALL;
            if (!attemptInlineValue(entry.getValue(), buffer, typeOffset)) {
                buffer.putShort(typeOffset + 1, (short) (buffer.position() - startPosition));
                serialJsonValue(buffer, typeOffset, entry.getValue());
            }
        }
        buffer.putShort(sizePos, (byte) (buffer.position() - startPosition));
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

    private void serialJsonArray(AutoExpandBuffer buffer, JSONArray array) {
        int size = array.size();
        int startPosition = buffer.position();
        buffer.putShort((short) size);
        int sizePos = buffer.position();
        buffer.putShort((short) 0);

        int mark = buffer.position();
        for (int i = 0; i < size; i++) {
            buffer.put((byte) 0);
            buffer.putShort((short) 0);
        }

        for (int i = 0; i < size; i++) {
            Object o = array.get(i);
            int typeOffset = mark + i * VALUE_ENTRY_SIZE_SMALL;
            if (!attemptInlineValue(o, buffer, typeOffset)) {
                buffer.putShort(typeOffset + 1, (short) (buffer.position() - startPosition));
                serialJsonValue(buffer, typeOffset, o);
            }
        }
        buffer.putShort(sizePos, (short) (buffer.position() - startPosition));
    }

}
