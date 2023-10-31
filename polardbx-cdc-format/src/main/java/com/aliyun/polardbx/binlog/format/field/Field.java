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

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.CharsetMaxLenEnum;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Objects;

import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_BIT;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_BLOB;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_ENUM;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_GEOMETRY;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_JSON;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_LONGBLOB;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_MEDIUMBLOB;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_SET;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_TINYBLOB;
import static com.aliyun.polardbx.binlog.format.utils.MySQLType.MYSQL_TYPE_YEAR;

@Slf4j
public abstract class Field {

    public static final int portable_sizeof_char_ptr = 8;
    private static final int MAX_DATE_WIDTH = 10;             /* YYYY-MM-DD */
    private static final int MAX_TIME_WIDTH = 10;               /* -838:59:59 */
    private static final int MAX_TIME_FULL_WIDTH = 23;           /* -DDDDDD HH:MM:SS.###### */
    private static final int MAX_DATETIME_FULL_WIDTH = 29;       /* YYYY-MM-DD HH:MM:SS.###### AM */
    private static final int MAX_DATETIME_WIDTH = 19;      /* YYYY-MM-DD HH:MM:SS */
    private static final int MAX_DATETIME_COMPRESSED_WIDTH = 14; /* YYYYMMDDHHMMSS */
    private static final int DATE_INT_DIGITS = 8;     /* YYYYMMDD       */
    private static final int TIME_INT_DIGITS = 7;    /* hhhmmss        */
    private static final int DATETIME_INT_DIGITS = 14; /* YYYYMMDDhhmmss */
    // Max width for a VARCHAR column, in number of bytes
    static long MAX_VARCHAR_WIDTH = 65535;
    // Maximum sizes of the four BLOB types, in number of bytes
    static long MAX_TINY_BLOB_WIDTH = 255;
    static long MAX_SHORT_BLOB_WIDTH = 65535;
    static long MAX_MEDIUM_BLOB_WIDTH = 16777215;
    static long MAX_LONG_BLOB_WIDTH = 4294967295L;
    static int CONVERT_IF_BIGGER_TO_BLOB = 512; /* Used for CREATE ... SELECT */
    static int MAX_BIT_FIELD_LENGTH = 64; /* Max length in bits for bit fields */
    static int MAX_MBWIDTH = 3; /* Max multibyte sequence */
    static int MAX_FIELD_CHARLENGTH = 255;
    /* Max column width +1 */
    static int MAX_FIELD_WIDTH = (MAX_FIELD_CHARLENGTH * MAX_MBWIDTH + 1);
    static int MAX_FIELD_VARCHARLENGTH = 65535;
    static long UINT_MAX32 = 0xFFFFFFFFL;
    static long MAX_FIELD_BLOBLENGTH = UINT_MAX32;/* cf field_blob::get_length() */
    protected final Serializable data;
    /// The maximum display width of this column.
    ///
    /// The "display width" is the number of code points that is needed to print
    /// out the string represenation of a value. It can be given by the user
    /// both explicitly and implicitly. If a user creates a table with the columns
    /// "a VARCHAR(3), b INT(3)", both columns are given an explicit display width
    /// of 3 code points. But if a user creates a table with the columns
    /// "a INT, b TINYINT UNSIGNED", the first column has an implicit display
    /// width of 11 (-2147483648 is the longest value for a signed int) and the
    /// second column has an implicit display width of 3 (255 is the longest value
    /// for an unsigned tinyint).
    /// This is related to storage size for some types (VARCHAR, BLOB etc), but
    /// not for all types (an INT is four bytes regardless of the display width).
    ///
    /// A "code point" is bascially a numeric value. For instance, ASCII
    /// compromises of 128 code points (0x00 to 0x7F), while unicode contains way
    /// more. In most cases a code point represents a single graphical unit (aka
    /// grapheme), but not always. For instance, É may consists of two code points
    /// where one is the letter E and the other one is the quotation mark above
    /// the letter.
    protected int m_max_display_width_in_codepoints;
    protected MySQLType mysqlType;
    protected long fieldLength;
    protected int packageLength;
    protected boolean m_explicit_display_width;
    protected int mbmaxlen;
    protected String[] typeNames;
    protected int count;
    protected Charset charset;
    protected boolean nullable;
    protected String realType;
    protected byte[] EMPTY = new byte[0];

    public Field(CreateField createField) {
        if (createField == null) {
            this.data = null;
            return;
        }
        this.data = createField.getDefaultValue();
        this.mysqlType = MySQLType.valueOf(createField.getDataType());
        this.realType = createField.getRealType();
        this.m_max_display_width_in_codepoints = createField.getCodepoint();
        this.m_explicit_display_width = createField.isExplicitWidth();
        CharsetMaxLenEnum maxLenEnum = CharsetMaxLenEnum.find(createField.getMysqlCharset());
        if (maxLenEnum == null) {
            CharsetEncoder encoder = createField.getCharset().newEncoder();
            mbmaxlen = (int) encoder.maxBytesPerChar();
        } else {
            mbmaxlen = maxLenEnum.getMaxLen();
        }
        count = createField.getParameters() == null ? 0 : createField.getParameters().length;
        typeNames = new String[count];
        for (int i = 0; i < typeNames.length; i++) {
            typeNames[i] = createField.getParameters()[i];
        }
        this.charset = createField.getCharset();
        this.nullable = createField.isNullable();
        CreateField.SqlTypeConvert sqlTypeConvert = createField.getConvertType();
        if (createField.getConvertType() != null) {
            switch (sqlTypeConvert) {
            case BINARY:
            case VARBINARY:
                if (createField.getParameters() != null) {
                    this.fieldLength = Long.parseLong(createField.getParameters()[0]);
                }
                break;
            default:
                this.fieldLength = max_display_width_in_bytes(createField.getCharset(), mysqlType);
            }
        } else {
            this.fieldLength = max_display_width_in_bytes(createField.getCharset(), mysqlType);
        }

    }

    public long parseLong(String dataStr) {
        if (log.isDebugEnabled()) {
            log.debug("data str for parsing to long is " + dataStr);
        }

        dataStr = dataStr.toLowerCase();
        int sign = 0;
        if (dataStr.startsWith("-")) {
            sign = -1;
            dataStr = StringUtils.substring(dataStr, 1);
        }
        long v;
        if (StringUtils.startsWith(dataStr, "b'")) {
            dataStr = StringUtils.substringAfter(dataStr, "b'");
            dataStr = StringUtils.substringBefore(dataStr, "'");
            v = Long.parseUnsignedLong(dataStr, 2);
        } else if (StringUtils.startsWith(dataStr, "0x") || StringUtils.startsWith(dataStr, "0X")) {
            dataStr = StringUtils.substring(dataStr, 2);
            v = Long.parseUnsignedLong(dataStr, 16);
        } else if (StringUtils.startsWith(dataStr, "x'")) {
            dataStr = StringUtils.substringAfter(dataStr, "x'");
            dataStr = StringUtils.substringBefore(dataStr, "'");
            v = Long.parseUnsignedLong(dataStr, 16);
        } else if (StringUtils.startsWith(dataStr, "X'")) {
            dataStr = StringUtils.substringAfter(dataStr, "X'");
            dataStr = StringUtils.substringBefore(dataStr, "'");
            v = Long.parseUnsignedLong(dataStr, 16);
        } else if (StringUtils.equals(dataStr, "true")) {
            v = 1;
        } else if (StringUtils.equals(dataStr, "false")) {
            v = 0;
        } else {
            v = Long.parseLong(dataStr);
        }
        if (sign == -1) {
            return -v;
        }
        return v;
    }

    public boolean isOctOrHexOrBin(String dataStr) {
        if (StringUtils.isNotBlank(dataStr)) {
            dataStr = dataStr.trim();
            char first = dataStr.charAt(0);
            if (first == 'b' ||
                first == 'x' || first == 'X') {
                return true;
            }
            if (dataStr.length() > 2) {
                char second = dataStr.charAt(1);
                return (first == '0' && (second == 'x' || second == 'X'));
            }
        }
        return false;
    }

    public Serializable getValue() {
        return data;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isNull() {
        return data == null;
    }

    public final byte[] encode() {
        if (isNull()) {
            return EMPTY;
        } else {
            byte[] bytes = encodeInternal();
            if (Objects.isNull(bytes)) {
                throw new PolardbxException("encoded data value can`t be null!");
            }
            return bytes;
        }
    }

    public abstract byte[] encodeInternal();

    protected String buildDataStr() {
        if (data instanceof byte[]) {
            return new String((byte[]) data, charset);
        } else {
            return String.valueOf(data);
        }
    }

    protected byte[] toByte(long data, int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) ((data >> (i * 8)) & (0xFF));
        }
        return bytes;
    }

    protected byte[] toBEByte(long data, int size) {
        byte[] bytes = new byte[size];
        for (int i = size - 1; i >= 0; i--) {
            bytes[size - i - 1] = (byte) ((data >> (i * 8)) & (0xFF));
        }
        return bytes;
    }

    protected void toByte(byte[] bytes, long data, int size, int offset) {
        for (int i = 0; i < size; i++) {
            bytes[i + offset] = (byte) ((data >> (i * 8)) & (0xFF));
        }
    }

    protected void toBEByte(ByteBuffer bytes, long data, int size) {
        for (int i = size - 1; i >= 0; i--) {
            bytes.put((byte) ((data >> (i * 8)) & (0xFF)));
        }
    }

    public MySQLType getMysqlType() {
        return mysqlType;
    }

    public abstract byte[] doGetTableMeta();

    private int my_time_binary_length(int dec) {
        return 3 + (dec + 1) / 2;
    }

    private int my_timestamp_binary_length(int dec) {
        return 4 + (dec + 1) / 2;
    }

    private int my_datetime_binary_length(int dec) {
        return 5 + (dec + 1) / 2;
    }

    protected int calcPackLength(MySQLType type, int length) {
        switch (type) {
        case MYSQL_TYPE_VARSTRING:
        case MYSQL_TYPE_STRING:
        case MYSQL_TYPE_DECIMAL:
            return (length);
        case MYSQL_TYPE_VARCHAR:
            return (length + (length < 256 ? 1 : 2));
        case MYSQL_TYPE_BOOL:
        case MYSQL_TYPE_YEAR:
        case MYSQL_TYPE_TINY:
            return 1;
        case MYSQL_TYPE_SHORT:
            return 2;
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_NEWDATE:
            return 3;
        case MYSQL_TYPE_TIME:
            return 3;
        case MYSQL_TYPE_TIME2:
            return length > MAX_TIME_WIDTH ? my_time_binary_length(length - MAX_TIME_WIDTH - 1) : 3;
        case MYSQL_TYPE_TIMESTAMP:
            return 4;
        case MYSQL_TYPE_TIMESTAMP2:
            return length > MAX_DATETIME_WIDTH ? my_timestamp_binary_length(length - MAX_DATETIME_WIDTH - 1) : 4;
        case MYSQL_TYPE_DATE:
        case MYSQL_TYPE_LONG:
            return 4;
        case MYSQL_TYPE_FLOAT:
            return 4;
        case MYSQL_TYPE_DOUBLE:
            return 8;
        case MYSQL_TYPE_DATETIME:
            return 8;
        case MYSQL_TYPE_DATETIME2:
            return length > MAX_DATETIME_WIDTH ? my_datetime_binary_length(length - MAX_DATETIME_WIDTH - 1) : 5;
        case MYSQL_TYPE_LONGLONG:
            return 8; /* Don't crash if no longlong */
        case MYSQL_TYPE_NULL:
            return 0;
        case MYSQL_TYPE_TINYBLOB:
            return 1 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_BLOB:
            return 2 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_MEDIUMBLOB:
            return 3 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_LONGBLOB:
            return 4 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_GEOMETRY:
            return 4 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_JSON:
            return 4 + portable_sizeof_char_ptr;
        case MYSQL_TYPE_SET:
        case MYSQL_TYPE_ENUM:
        case MYSQL_TYPE_NEWDECIMAL:
            return 0;  // This shouldn't happen
        case MYSQL_TYPE_BIT:
            return length / 8;
        case MYSQL_TYPE_INVALID:
        case MYSQL_TYPE_TYPED_ARRAY:
            break;
        }
        // should not happen
        return 0;
    }

    long max_display_width_in_bytes(Charset charset, MySQLType sql_type) {
        // It might seem unnecessary to have special case for the various BLOB types
        // instead of just using the "else" clause for these types as well. However,
        // that might give us rounding errors for multi-byte character sets. One
        // example is JSON which has the character set utf8mb4_bin.
        // max_display_width_in_codepoints() will return 1073741823 (truncated from
        // 1073741823.75), and multiplying that by four again will give 4294967292
        // which is the wrong result.
        if (is_numeric_type(sql_type) || is_temporal_real_type(sql_type) || sql_type == MYSQL_TYPE_YEAR
            || sql_type == MYSQL_TYPE_BIT) {
            // Numeric types, temporal types, YEAR or BIT are never multi-byte.
            return max_display_width_in_codepoints(sql_type);
        } else if (sql_type == MYSQL_TYPE_TINYBLOB) {
            return MAX_TINY_BLOB_WIDTH;
        } else if (sql_type == MYSQL_TYPE_BLOB && !explicit_display_width()) {
            // For BLOB and TEXT, the user can give a display width (BLOB(25), TEXT(25))
            // where the expected behavior is that the server will find the smallest
            // possible BLOB/TEXT type that will fit the given display width. If the
            // user has given an explicit display width, return that instead of the
            // max BLOB size.
            return MAX_SHORT_BLOB_WIDTH;
        } else if (sql_type == MYSQL_TYPE_MEDIUMBLOB) {
            return MAX_MEDIUM_BLOB_WIDTH;
        } else if (sql_type == MYSQL_TYPE_LONGBLOB || sql_type == MYSQL_TYPE_JSON || sql_type == MYSQL_TYPE_GEOMETRY) {
            return MAX_LONG_BLOB_WIDTH;
        } else {
            // If the user has given a display width to the TEXT type where the display
            // width is 2^32-1, the below computation will exceed
            // MAX_LONG_BLOB_WIDTH if the character set is multi-byte. So we must
            // ensure that we never return a value greater than
            // MAX_LONG_BLOB_WIDTH.

            long display_width = max_display_width_in_codepoints(sql_type) * mbmaxlen;
            return Math.min(display_width, (MAX_LONG_BLOB_WIDTH));
        }
    }

    int max_display_width_in_codepoints(MySQLType sql_type) {
        if (sql_type == MYSQL_TYPE_ENUM || sql_type == MYSQL_TYPE_SET) {

            int max_display_width_in_codepoints = 0;
            for (int i = 0; i < count; i++) {
                int num_characters = typeNames[i].length();

                if (sql_type == MYSQL_TYPE_ENUM) {
                    // ENUM uses the longest value.
                    max_display_width_in_codepoints = Math.max(max_display_width_in_codepoints, num_characters);
                } else if (sql_type == MYSQL_TYPE_SET) {
                    // SET uses the total length of all values, plus a comma between each
                    // value.
                    max_display_width_in_codepoints += num_characters;
                    if (i > 0) {
                        max_display_width_in_codepoints++;
                    }
                }
            }

            return Math.min(max_display_width_in_codepoints, MAX_FIELD_WIDTH - 1);
        } else if (sql_type == MYSQL_TYPE_TINYBLOB) {
            return (int) (MAX_TINY_BLOB_WIDTH / mbmaxlen);
        } else if (sql_type == MYSQL_TYPE_BLOB && !explicit_display_width()) {
            // For BLOB and TEXT, the user can give a display width explicitly in CREATE
            // TABLE (BLOB(25), TEXT(25)) where the expected behavior is that the server
            // will find the smallest possible BLOB/TEXT type that will fit the given
            // display width. If the user has given an explicit display width, return
            // that instead of the max BLOB size.
            return (int) (MAX_SHORT_BLOB_WIDTH / mbmaxlen);
        } else if (sql_type == MYSQL_TYPE_MEDIUMBLOB) {
            return (int) (MAX_MEDIUM_BLOB_WIDTH / mbmaxlen);
        } else if (sql_type == MYSQL_TYPE_LONGBLOB || sql_type == MYSQL_TYPE_JSON || sql_type == MYSQL_TYPE_GEOMETRY) {
            return (int) (MAX_LONG_BLOB_WIDTH / mbmaxlen);
        } else {
            return m_max_display_width_in_codepoints;
        }
    }

    /**
     * Tests if field type is an integer
     *
     * @param type Field type, as returned by field->type()
     */
    boolean is_integer_type(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_TINY:
        case MYSQL_TYPE_SHORT:
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_LONG:
        case MYSQL_TYPE_LONGLONG:
        case MYSQL_TYPE_YEAR:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field type is a numeric type
     *
     * @param type Field type, as returned by field->type()
     */
    boolean is_numeric_type(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_TINY:
        case MYSQL_TYPE_SHORT:
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_LONG:
        case MYSQL_TYPE_LONGLONG:
        case MYSQL_TYPE_YEAR:
        case MYSQL_TYPE_FLOAT:
        case MYSQL_TYPE_DOUBLE:
        case MYSQL_TYPE_DECIMAL:
        case MYSQL_TYPE_NEWDECIMAL:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field type is a string type
     *
     * @param type Field type, as returned by field->type()
     */
    boolean is_string_type(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_VARCHAR:
        case MYSQL_TYPE_VARSTRING:
        case MYSQL_TYPE_STRING:
        case MYSQL_TYPE_TINYBLOB:
        case MYSQL_TYPE_MEDIUMBLOB:
        case MYSQL_TYPE_LONGBLOB:
        case MYSQL_TYPE_BLOB:
        case MYSQL_TYPE_ENUM:
        case MYSQL_TYPE_SET:
        case MYSQL_TYPE_JSON:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field type is temporal, i.e. represents
     * DATE, TIME, DATETIME or TIMESTAMP types in SQL.
     *
     * @param type Field type, as returned by field->type().
     */
    boolean is_temporal_type(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_TIME:
        case MYSQL_TYPE_DATETIME:
        case MYSQL_TYPE_TIMESTAMP:
        case MYSQL_TYPE_DATE:
        case MYSQL_TYPE_NEWDATE:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field real type is temporal, i.e. represents
     * all existing implementations of
     * DATE, TIME, DATETIME or TIMESTAMP types in SQL.
     *
     * @param type Field real type, as returned by field->real_type()
     */
    boolean is_temporal_real_type(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_TIME2:
        case MYSQL_TYPE_TIMESTAMP2:
        case MYSQL_TYPE_DATETIME2:
            return true;
        default:
            return is_temporal_type(type);
        }
    }

    /**
     * Tests if field type is temporal and has time part,
     * i.e. represents TIME, DATETIME or TIMESTAMP types in SQL.
     *
     * @param type Field type, as returned by field->type().
     */
    boolean is_temporal_type_with_time(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_TIME:
        case MYSQL_TYPE_DATETIME:
        case MYSQL_TYPE_TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field type is temporal and has date part,
     * i.e. represents DATE, DATETIME or TIMESTAMP types in SQL.
     *
     * @param type Field type, as returned by field->type().
     */
    boolean is_temporal_type_with_date(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_DATE:
        case MYSQL_TYPE_DATETIME:
        case MYSQL_TYPE_TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    /**
     * Tests if field type is temporal and has date and time parts,
     * i.e. represents DATETIME or TIMESTAMP types in SQL.
     *
     * @param type Field type, as returned by field->type().
     */
    boolean is_temporal_type_with_date_and_time(MySQLType type) {
        switch (type) {
        case MYSQL_TYPE_DATETIME:
        case MYSQL_TYPE_TIMESTAMP:
            return true;
        default:
            return false;
        }
    }

    /// @retval true if the maximum column length was given explicitly by the
    ///         user.
    /// @retval false if the user didn't specify any maximum length.
    boolean explicit_display_width() {
        return m_explicit_display_width;
    }

    Serializable decode(byte[] value, int meta) {
        LogBuffer logBuffer = new LogBuffer(value, 0, value.length);
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(logBuffer, 0, charset.name());
        return rowsLogBuffer.fetchValue(mysqlType.getType(), meta, false, false);
    }

}
