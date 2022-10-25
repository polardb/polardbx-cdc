/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.format.field;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * MYSQL_TYPE_JSON
 */
public class JsonField extends BlobField {

    private static int SMALL_OFFSET_SIZE = 2;


    private static int KEY_ENTRY_SIZE_SMALL = 2 + SMALL_OFFSET_SIZE;


    private static int VALUE_ENTRY_SIZE_SMALL = 1 + SMALL_OFFSET_SIZE;

    public JsonField(CreateField createField) {
        super(createField);

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        JSONObject jsonObject = JSON.parseObject(this.data);
        buffer.put((byte) 0);
        serialJsonValue(buffer, 0, jsonObject);
        int pos = buffer.position();
        buffer.flip();
        contents = new byte[pos];
        buffer.get(contents);
        fieldLength = pos;
        calculatePackLength();
    }

    private void serialJsonValue(ByteBuffer buffer, int typeOffset, Object o) {
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
        } else if (o instanceof Double) {
            buffer.putDouble((Double) o);
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_DOUBLE);
            buffer.putShort(typeOffset + 1, (short) 8);
        } else if (o instanceof Boolean) {
            buffer.put((byte) (((Boolean) o) ? JsonConversion.JSONB_TRUE_LITERAL : JsonConversion.JSONB_FALSE_LITERAL));
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.putShort(typeOffset + 1, (short) 1);
        } else if (o == null) {
            buffer.put(typeOffset, (byte) JsonConversion.JSONB_TYPE_LITERAL);
            buffer.put((byte) JsonConversion.JSONB_NULL_LITERAL);
        }
    }

    private void serialJsonObject(ByteBuffer buffer, JSONObject object) {
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
            buffer.putShort(typeOffset + 1, (short) (buffer.position() - startPosition));
            serialJsonValue(buffer, typeOffset, entry.getValue());
        }
        buffer.putShort(sizePos, (byte) (buffer.position() - startPosition));
    }

    private void serialJsonArray(ByteBuffer buffer, JSONArray array) {
        int size = array.size();
        int startPosition = buffer.position();
        buffer.putShort((short) size);
        int sizePos = buffer.position();
        buffer.putShort((short) 0);

        int entrySize = VALUE_ENTRY_SIZE_SMALL;
        int entryPos = buffer.position();
        for (int i = 0; i < entrySize * size; i++) {
            buffer.put((byte) 0);
        }

        for (int i = 0; i < size; i++) {
            Object o = array.get(i);
            buffer.putShort(entryPos + 1, (short) (buffer.position() - startPosition));
            serialJsonValue(buffer, entryPos += entrySize, o);
        }
        buffer.putShort(sizePos, (short) (buffer.position() - startPosition));
    }

}
