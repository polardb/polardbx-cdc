/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * case MYSQL_TYPE_BLOB:
 * case MYSQL_TYPE_MEDIUM_BLOB:
 * case MYSQL_TYPE_TINY_BLOB:
 * case MYSQL_TYPE_LONG_BLOB:
 */
public class BlobField extends Field {

    private static final Logger logger = LoggerFactory.getLogger(BlobField.class);

    private static final int MAX_1_B = (1 << 8) - 1;
    private static final int MAX_2_B = (1 << 16) - 1;
    private static final int MAX_3_B = (1 << 24) - 1;
    protected byte[] contents;

    public BlobField(CreateField createField) {
        super(createField);
        calculatePackLength();
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_BLOB;
    }

    public void calculatePackLength() {
        this.packageLength = calcPackLength(mysqlType, (int) fieldLength) - portable_sizeof_char_ptr;
    }

    public void setContents(byte[] contents) {
        this.contents = contents;
    }

    @Override
    public boolean isNull() {
        return super.isNull() && contents == null;
    }

    @Override
    public byte[] encodeInternal() {
        if (contents == null) {
            if (this.data instanceof byte[]) {
                contents = (byte[]) this.data;
            } else {
                if (StringUtils.endsWith(this.realType, "BLOB")) {
                    BigInteger bigInteger = null;
                    String dataStr = String.valueOf(this.data);
                    try {
                        if (StringUtils.startsWith(dataStr, "b'")) {
                            dataStr = StringUtils.substringAfter(dataStr, "b'");
                            dataStr = StringUtils.substringBefore(dataStr, "'");
                            bigInteger = new BigInteger(dataStr, 2);
                        } else if (StringUtils.startsWith(dataStr, "0x") || StringUtils.startsWith(dataStr, "0X")) {
                            dataStr = StringUtils.substring(dataStr, 2);
                            bigInteger = new BigInteger(dataStr, 16);
                        } else if (StringUtils.startsWith(dataStr, "x'")) {
                            dataStr = StringUtils.substringAfter(dataStr, "x'");
                            dataStr = StringUtils.substringBefore(dataStr, "'");
                            bigInteger = new BigInteger(dataStr, 16);
                        } else if (StringUtils.startsWith(dataStr, "X'")) {
                            dataStr = StringUtils.substringAfter(dataStr, "X'");
                            dataStr = StringUtils.substringBefore(dataStr, "'");
                            bigInteger = new BigInteger(dataStr, 16);
                        }
                    } catch (Exception e) {
                        logger.error(
                            "try convert str 2 blob failed! str is " + this.data + " , real type is " + this.realType,
                            e);
                    }
                    if (bigInteger != null) {
                        contents = bigInteger.toByteArray();
                    }
                }
                if (StringUtils.contains(this.realType, "TEXT") || contents == null) {
                    contents = String.valueOf(this.data).getBytes(charset);
                }
            }
        }

        int len = contents.length;
        byte[] binary;
        /*
         * BLOB or TEXT datatype
         */
        switch (packageLength) {
        case 1: {
            /* TINYBLOB/TINYTEXT */
            if (len > MAX_1_B) {
                len = MAX_1_B;
            }
            binary = new byte[len + 1];
            binary[0] = (byte) len;
            System.arraycopy(contents, 0, binary, 1, len);
            return binary;
        }
        case 2: {
            /* BLOB/TEXT */
            if (len > MAX_2_B) {
                len = MAX_2_B;
            }
            binary = new byte[len + 2];
            binary[0] = (byte) (0xFF & len);
            binary[1] = (byte) (0xFF & (len >> 8));
            System.arraycopy(contents, 0, binary, 2, len);
            return binary;
        }
        case 3: {
            /* MEDIUMBLOB/MEDIUMTEXT */
            if (len > MAX_3_B) {
                len = MAX_3_B;
            }
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
