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

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

/**
 * case MYSQL_TYPE_BLOB:
 * case MYSQL_TYPE_MEDIUM_BLOB:
 * case MYSQL_TYPE_TINY_BLOB:
 * case MYSQL_TYPE_LONG_BLOB:
 */
public class BlobField extends Field {

    protected byte[] contents;

    public BlobField(CreateField createField) {
        super(createField);
        calculatePackLength();
    }

    public void calculatePackLength() {
        this.packageLength = calcPackLength(mysqlType, (int) fieldLength) - portable_sizeof_char_ptr;
    }

//    private static byte[] hexStr2Byte(String hex) {
//        ByteBuffer bf = ByteBuffer.allocate(hex.length() / 2);
//        for (int i = 0; i < hex.length(); i++) {
//            int hexStr = hex.charAt(i);
//            i++;
//            hexStr += hex.charAt(i);
//            byte b = (byte) Integer.parseInt(hexStr, 16);
//            bf.put(b);
//        }
//        return bf.array();
//    }

    @Override
    public byte[] encode() {
        if (contents == null) {
            if (this.data == null) {
                return EMPTY;
            }
            contents = this.data.getBytes(charset);
        }
        int len = contents.length;
        byte[] binary;
        /*
         * BLOB or TEXT datatype
         */
        switch (packageLength) {
        case 1: {
            /* TINYBLOB/TINYTEXT */
            binary = new byte[len + 1];
            binary[0] = (byte) len;
            System.arraycopy(contents, 0, binary, 1, len);
            return binary;
        }
        case 2: {
            /* BLOB/TEXT */
            binary = new byte[len + 2];
            binary[0] = (byte) (0xFF & len);
            binary[1] = (byte) (0xFF & (len >> 8));
            System.arraycopy(contents, 0, binary, 2, len);
            return binary;
        }
        case 3: {
            /* MEDIUMBLOB/MEDIUMTEXT */
            binary = new byte[len + 3];
            binary[0] = (byte) (0xFF & len);
            binary[1] = (byte) (0xFF & (len >> 8));
            binary[2] = (byte) (0xFF & (len >> 16));
            System.arraycopy(contents, 0, binary, 3, len);
            return binary;
        }
        case 4: {
            /* LONGBLOB/LONGTEXT */
            binary = new byte[len + 4];
            binary[0] = (byte) (0xFF & len);
            binary[1] = (byte) (0xFF & (len >> 8));
            binary[2] = (byte) (0xFF & (len >> 16));
            binary[3] = (byte) (0xFF & (len >> 24));
            System.arraycopy(contents, 0, binary, 4, len);
            return binary;
        }
        default:
            throw new IllegalArgumentException("!! Unknown BLOB packlen = " + packageLength);
        }

    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) packageLength};
    }
}
