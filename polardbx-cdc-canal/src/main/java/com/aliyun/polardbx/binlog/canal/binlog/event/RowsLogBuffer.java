/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion.Json_Value;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.BitSet;

/**
 * Extracting JDBC type & value information from packed rows-buffer.
 *
 * @author Changyuan.lh
 * @version 1.0
 * @see mysql-5.1.60/sql/log_event.cc - Rows_log_event::print_verbose_one_row
 */
public final class RowsLogBuffer {

    public static final long DATETIMEF_INT_OFS = 0x8000000000L;
    public static final long TIMEF_INT_OFS = 0x800000L;
    public static final long TIMEF_OFS = 0x800000000000L;
    protected static final Logger logger = LoggerFactory.getLogger(RowsLogBuffer.class);
    private final LogBuffer buffer;
    private final int columnLen;
    private final String charsetName;
    // private Calendar cal;

    private final BitSet nullBits;
    private int nullBitIndex;

    private boolean fNull;
    private int javaType;
    private int length;
    private Serializable value;

    private static char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    public RowsLogBuffer(LogBuffer buffer, final int columnLen, String charsetName) {
        this.buffer = buffer;
        this.columnLen = columnLen;
        this.charsetName = charsetName;
        this.nullBits = new BitSet(columnLen);
    }

    /**
     * Maps the given MySQL type to the correct JDBC type.
     */
    static int mysqlToJavaType(int type, final int meta, boolean isBinary) {
        int javaType;

        if (type == LogEvent.MYSQL_TYPE_STRING) {
            if (meta >= 256) {
                int byte0 = meta >> 8;
                if ((byte0 & 0x30) != 0x30) {
                    /* a long CHAR() field: see #37426 */
                    type = byte0 | 0x30;
                } else {
                    switch (byte0) {
                    case LogEvent.MYSQL_TYPE_SET:
                    case LogEvent.MYSQL_TYPE_ENUM:
                    case LogEvent.MYSQL_TYPE_STRING:
                        type = byte0;
                    }
                }
            }
        }

        switch (type) {
        case LogEvent.MYSQL_TYPE_LONG:
            javaType = Types.INTEGER;
            break;

        case LogEvent.MYSQL_TYPE_TINY:
            javaType = Types.TINYINT;
            break;

        case LogEvent.MYSQL_TYPE_SHORT:
            javaType = Types.SMALLINT;
            break;

        case LogEvent.MYSQL_TYPE_INT24:
            javaType = Types.INTEGER;
            break;

        case LogEvent.MYSQL_TYPE_LONGLONG:
            javaType = Types.BIGINT;
            break;

        case LogEvent.MYSQL_TYPE_DECIMAL:
            javaType = Types.DECIMAL;
            break;

        case LogEvent.MYSQL_TYPE_NEWDECIMAL:
            javaType = Types.DECIMAL;
            break;

        case LogEvent.MYSQL_TYPE_FLOAT:
            javaType = Types.REAL; // Types.FLOAT;
            break;

        case LogEvent.MYSQL_TYPE_DOUBLE:
            javaType = Types.DOUBLE;
            break;

        case LogEvent.MYSQL_TYPE_BIT:
            javaType = Types.BIT;
            break;

        case LogEvent.MYSQL_TYPE_TIMESTAMP:
        case LogEvent.MYSQL_TYPE_DATETIME:
        case LogEvent.MYSQL_TYPE_TIMESTAMP2:
        case LogEvent.MYSQL_TYPE_DATETIME2:
            javaType = Types.TIMESTAMP;
            break;

        case LogEvent.MYSQL_TYPE_TIME:
        case LogEvent.MYSQL_TYPE_TIME2:
            javaType = Types.TIME;
            break;

        case LogEvent.MYSQL_TYPE_NEWDATE:
        case LogEvent.MYSQL_TYPE_DATE:
            javaType = Types.DATE;
            break;

        case LogEvent.MYSQL_TYPE_YEAR:
            javaType = Types.VARCHAR;
            break;

        case LogEvent.MYSQL_TYPE_ENUM:
            javaType = Types.INTEGER;
            break;

        case LogEvent.MYSQL_TYPE_SET:
            javaType = Types.BINARY;
            break;

        case LogEvent.MYSQL_TYPE_TINY_BLOB:
        case LogEvent.MYSQL_TYPE_MEDIUM_BLOB:
        case LogEvent.MYSQL_TYPE_LONG_BLOB:
        case LogEvent.MYSQL_TYPE_BLOB:
            if (meta == 1) {
                javaType = Types.VARBINARY;
            } else {
                javaType = Types.LONGVARBINARY;
            }
            break;

        case LogEvent.MYSQL_TYPE_VARCHAR:
        case LogEvent.MYSQL_TYPE_VAR_STRING:
            if (isBinary) {
                // varbinary在binlog中为var_string类型
                javaType = Types.VARBINARY;
            } else {
                javaType = Types.VARCHAR;
            }
            break;

        case LogEvent.MYSQL_TYPE_STRING:
            if (isBinary) {
                // binary在binlog中为string类型
                javaType = Types.BINARY;
            } else {
                javaType = Types.CHAR;
            }
            break;

        case LogEvent.MYSQL_TYPE_GEOMETRY:
            javaType = Types.BINARY;
            break;

        // case LogEvent.MYSQL_TYPE_BINARY:
        // javaType = Types.BINARY;
        // break;
        //
        // case LogEvent.MYSQL_TYPE_VARBINARY:
        // javaType = Types.VARBINARY;
        // break;

        default:
            javaType = Types.OTHER;
        }

        return javaType;
    }

    /**
     * Extracting next row from packed buffer.
     *
     * @see mysql-5.1.60/sql/log_event.cc - Rows_log_event::print_verbose_one_row
     */
    public final boolean nextOneRow(BitSet columns) {
        final boolean hasOneRow = buffer.hasRemaining();

        if (hasOneRow) {
            int column = 0;

            for (int i = 0; i < columnLen; i++) {
                if (columns.get(i)) {
                    column++;
                }
            }

            nullBitIndex = 0;
            nullBits.clear();
            buffer.fillBitmap(nullBits, column);
        }
        return hasOneRow;
    }

    /**
     * Extracting next field value from packed buffer.
     *
     * @see mysql-5.1.60/sql/log_event.cc - Rows_log_event::print_verbose_one_row
     */
    public final Serializable nextValue(final int type, final int meta) {
        return nextValue(type, meta, false);
    }

    /**
     * Extracting next field value from packed buffer.
     *
     * @see mysql-5.1.60/sql/log_event.cc - Rows_log_event::print_verbose_one_row
     */
    public final Serializable nextValue(final int type, final int meta, boolean isBinary) {
        fNull = nullBits.get(nullBitIndex++);

        if (fNull) {
            value = null;
            javaType = mysqlToJavaType(type, meta, isBinary);
            length = 0;
            return null;
        } else {
            // Extracting field value from packed buffer.
            return fetchValue(type, meta, isBinary);
        }
    }

    /**
     * 只提供给columnar使用
     */
    public final Serializable nextBinaryValueForColumnar(final int type, final int meta) {
        fNull = nullBits.get(nullBitIndex++);

        if (fNull) {
            value = null;
            javaType = mysqlToJavaType(type, meta, true);
            length = 0;
            return null;
        } else {
            // Extracting field value from packed buffer.
            value = fetchBinaryValue(type, meta, true);
            javaType = mysqlToJavaType(type, meta, true);
            return value;
        }
    }

    public final Serializable nextValue(final int type, final int meta, boolean isBinary, String newCharsetName) {
        fNull = nullBits.get(nullBitIndex++);
        if (fNull) {
            value = null;
            javaType = mysqlToJavaType(type, meta, isBinary);
            length = 0;
            return null;
        } else {
            // Extracting field value from packed buffer.
            return fetchValue(type, meta, isBinary, buffer, newCharsetName, false);
        }
    }

    public final Serializable fetchValue(int type, final int meta, boolean isBinary, boolean unsigned) {
        return fetchValue(type, meta, isBinary, buffer, charsetName, unsigned);
    }

    public final Serializable fetchValue(int type, final int meta, boolean isBinary) {
        return fetchValue(type, meta, isBinary, buffer, charsetName, false);
    }

    public final Serializable fetchValue(int type, final int meta, boolean isBinary, byte[] orginalData,
                                         String charsetName) {
        return fetchValue(type, meta, isBinary, new LogBuffer(orginalData, 0, orginalData.length), charsetName, false);
    }

    /**
     * Extracting next field value from packed buffer.
     *
     * @see mysql-5.1.60/sql/log_event.cc - log_event_print_value
     */
    public final Serializable fetchValue(int type, final int meta, boolean isBinary, LogBuffer buffer,
                                         String charsetName,
                                         boolean unsigned) {
        int len = 0;

        if (type == LogEvent.MYSQL_TYPE_STRING) {
            if (meta >= 256) {
                int byte0 = meta >> 8;
                int byte1 = meta & 0xff;
                if ((byte0 & 0x30) != 0x30) {
                    /* a long CHAR() field: see #37426 */
                    len = byte1 | (((byte0 & 0x30) ^ 0x30) << 4);
                    type = byte0 | 0x30;
                } else {
                    switch (byte0) {
                    case LogEvent.MYSQL_TYPE_SET:
                    case LogEvent.MYSQL_TYPE_ENUM:
                    case LogEvent.MYSQL_TYPE_STRING:
                    case LogEvent.MYSQL_TYPE_VAR_STRING:
                        type = byte0;
                        len = byte1;
                        break;
                    default:
                        throw new IllegalArgumentException(String
                            .format("!! Don't know how to handle column type=%d meta=%d (%04X)", type, meta, meta));
                    }
                }
            } else {
                len = meta;
            }
        }

        switch (type) {
        case LogEvent.MYSQL_TYPE_LONG: {
            // XXX: How to check signed / unsigned?
            // value = unsigned ? Long.valueOf(buffer.getUint32()) :
            // Integer.valueOf(buffer.getInt32());
            value = unsigned ? Long.valueOf(buffer.getUint32()) :
                Integer.valueOf(buffer.getInt32());
            javaType = Types.INTEGER;
            length = 4;
            break;
        }
        case LogEvent.MYSQL_TYPE_TINY: {
            // XXX: How to check signed / unsigned?
            value = Integer.valueOf(unsigned ? buffer.getUint8() :
                buffer.getInt8());
//            value = Integer.valueOf(buffer.getInt8());
            javaType = Types.TINYINT; // java.sql.Types.INTEGER;
            length = 1;
            break;
        }
        case LogEvent.MYSQL_TYPE_SHORT: {
            // XXX: How to check signed / unsigned?
            value = Integer.valueOf(unsigned ? buffer.getUint16() :
                buffer.getInt16());
//            value = Integer.valueOf((short) buffer.getInt16());
            javaType = Types.SMALLINT; // java.sql.Types.INTEGER;
            length = 2;
            break;
        }
        case LogEvent.MYSQL_TYPE_INT24: {
            // XXX: How to check signed / unsigned?
            value = Integer.valueOf(unsigned ? buffer.getUint24() :
                buffer.getInt24());
//            value = Integer.valueOf(buffer.getInt24());
            javaType = Types.INTEGER;
            length = 3;
            break;
        }
        case LogEvent.MYSQL_TYPE_LONGLONG: {
            // XXX: How to check signed / unsigned?
            value = unsigned ? buffer.getUlong64() :
                Long.valueOf(buffer.getLong64());
//            value = Long.valueOf(buffer.getLong64());
            javaType = Types.BIGINT; // Types.INTEGER;
            length = 8;
            break;
        }
        case LogEvent.MYSQL_TYPE_DECIMAL: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_DECIMAL : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
            javaType = Types.DECIMAL;
            value = null; /* unknown format */
            length = 0;
            break;
        }
        case LogEvent.MYSQL_TYPE_NEWDECIMAL: {
            final int precision = meta >> 8;
            final int decimals = meta & 0xff;
            value = buffer.getDecimal(precision, decimals);
            javaType = Types.DECIMAL;
            length = precision;
            break;
        }
        case LogEvent.MYSQL_TYPE_FLOAT: {
            value = Float.valueOf(buffer.getFloat32());
            javaType = Types.REAL; // Types.FLOAT;
            length = 4;
            break;
        }
        case LogEvent.MYSQL_TYPE_DOUBLE: {
            value = Double.valueOf(buffer.getDouble64());
            javaType = Types.DOUBLE;
            length = 8;
            break;
        }
        case LogEvent.MYSQL_TYPE_BIT: {
            /* Meta-data: bit_len, bytes_in_rec, 2 bytes */
            final int nbits = ((meta >> 8) * 8) + (meta & 0xff);
            len = (nbits + 7) / 8;
            byte[] bits = new byte[len];
            buffer.fillBytes(bits, 0, len);
            value = bits;
            // if (nbits > 1) {
            // // 转化为unsign long
            // switch (len) {
            // case 1:
            // value = buffer.getInt8();
            // break;
            // case 2:
            // value = buffer.getBeUint16();
            // break;
            // case 3:
            // value = buffer.getBeUint24();
            // break;
            // case 4:
            // value = buffer.getBeUint32();
            // break;
            // case 5:
            // value = buffer.getBeUlong40();
            // break;
            // case 6:
            // value = buffer.getBeUlong48();
            // break;
            // case 7:
            // value = buffer.getBeUlong56();
            // break;
            // case 8:
            // value = buffer.getBeUlong64();
            // break;
            // default:
            // throw new IllegalArgumentException("!! Unknown Bit len = " +
            // len);
            // }
            // } else {
            // final int bit = buffer.getInt8();
            // // value = (bit != 0) ? Boolean.TRUE : Boolean.FALSE;
            // value = bit;
            // }
            javaType = Types.BIT;
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_TIMESTAMP: {
            // MYSQL DataTypes: TIMESTAMP
            // range is '1970-01-01 00:00:01' UTC to '2038-01-19 03:14:07'
            // UTC
            // A TIMESTAMP cannot represent the value '1970-01-01 00:00:00'
            // because that is equivalent to 0 seconds from the epoch and
            // the value 0 is reserved for representing '0000-00-00
            // 00:00:00', the “zero” TIMESTAMP value.
            final long i32 = buffer.getUint32();
            if (i32 == 0) {
                value = "0000-00-00 00:00:00";
            } else {
                String v = new Timestamp(i32 * 1000).toString();
                value = v.substring(0, v.length() - 2);
            }
            javaType = Types.TIMESTAMP;
            length = 4;
            break;
        }
        case LogEvent.MYSQL_TYPE_TIMESTAMP2: {
            final long tv_sec = buffer.getBeUint32(); // big-endian
            int tv_usec = 0;
            switch (meta) {
            case 0:
                tv_usec = 0;
                break;
            case 1:
            case 2:
                tv_usec = buffer.getInt8() * 10000;
                break;
            case 3:
            case 4:
                tv_usec = buffer.getBeInt16() * 100;
                break;
            case 5:
            case 6:
                tv_usec = buffer.getBeInt24();
                break;
            default:
                tv_usec = 0;
                break;
            }

            String second = null;
            if (tv_sec == 0) {
                second = "0000-00-00 00:00:00";
            } else {
                Timestamp time = new Timestamp(tv_sec * 1000);
                second = time.toString();
                second = second.substring(0, second.length() - 2);// 去掉毫秒精度.0
            }

            if (meta >= 1) {
                String microSecond = usecondsToStr(tv_usec, meta);
                microSecond = microSecond.substring(0, meta);
                value = second + '.' + microSecond;
            } else {
                value = second;
            }

            javaType = Types.TIMESTAMP;
            length = 4 + (meta + 1) / 2;
            break;
        }
        case LogEvent.MYSQL_TYPE_DATETIME: {
            // MYSQL DataTypes: DATETIME
            // range is '0000-01-01 00:00:00' to '9999-12-31 23:59:59'
            final long i64 = buffer.getLong64(); /* YYYYMMDDhhmmss */
            if (i64 == 0) {
                value = "0000-00-00 00:00:00";
            } else {
                final int d = (int) (i64 / 1000000);
                final int t = (int) (i64 % 1000000);
                // if (cal == null) cal = Calendar.getInstance();
                // cal.clear();
                /* month is 0-based, 0 for january. */
                // cal.set(d / 10000, (d % 10000) / 100 - 1, d % 100, t /
                // 10000, (t % 10000) / 100, t % 100);
                // value = new Timestamp(cal.getTimeInMillis());
                // value = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                // d / 10000,
                // (d % 10000) / 100,
                // d % 100,
                // t / 10000,
                // (t % 10000) / 100,
                // t % 100);

                StringBuilder builder = new StringBuilder();
                appendNumber4(builder, d / 10000);
                builder.append('-');
                appendNumber2(builder, (d % 10000) / 100);
                builder.append('-');
                appendNumber2(builder, d % 100);
                builder.append(' ');
                appendNumber2(builder, t / 10000);
                builder.append(':');
                appendNumber2(builder, (t % 10000) / 100);
                builder.append(':');
                appendNumber2(builder, t % 100);
                value = builder.toString();
            }
            javaType = Types.TIMESTAMP;
            length = 8;
            break;
        }
        case LogEvent.MYSQL_TYPE_DATETIME2: {
            /*
             * DATETIME and DATE low-level memory and disk representation routines 1 bit sign (used when on disk) 17
             * bits year*13+month (year 0-9999, month 0-12) 5 bits day (0-31) 5 bits hour (0-23) 6 bits minute
             * (0-59) 6 bits second (0-59) 24 bits microseconds (0-999999) Total: 64 bits = 8 bytes
             * SYYYYYYY.YYYYYYYY .YYdddddh.hhhhmmmm.mmssssss.ffffffff.ffffffff.ffffffff
             */
            long intpart = buffer.getBeUlong40() - DATETIMEF_INT_OFS; // big-endian
            int frac = 0;
            switch (meta) {
            case 0:
                frac = 0;
                break;
            case 1:
            case 2:
                frac = buffer.getInt8() * 10000;
                break;
            case 3:
            case 4:
                frac = buffer.getBeInt16() * 100;
                break;
            case 5:
            case 6:
                frac = buffer.getBeInt24();
                break;
            default:
                frac = 0;
                break;
            }

            String second = null;
            if (intpart == 0) {
                second = "0000-00-00 00:00:00";
            } else {
                // 构造TimeStamp只处理到秒
                long ymd = intpart >> 17;
                long ym = ymd >> 5;
                long hms = intpart % (1 << 17);

                // if (cal == null) cal = Calendar.getInstance();
                // cal.clear();
                // cal.set((int) (ym / 13), (int) (ym % 13) - 1, (int) (ymd
                // % (1 << 5)), (int) (hms >> 12),
                // (int) ((hms >> 6) % (1 << 6)), (int) (hms % (1 << 6)));
                // value = new Timestamp(cal.getTimeInMillis());
                // second = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                // (int) (ym / 13),
                // (int) (ym % 13),
                // (int) (ymd % (1 << 5)),
                // (int) (hms >> 12),
                // (int) ((hms >> 6) % (1 << 6)),
                // (int) (hms % (1 << 6)));

                StringBuilder builder = new StringBuilder(26);
                appendNumber4(builder, (int) (ym / 13));
                builder.append('-');
                appendNumber2(builder, (int) (ym % 13));
                builder.append('-');
                appendNumber2(builder, (int) (ymd % (1 << 5)));
                builder.append(' ');
                appendNumber2(builder, (int) (hms >> 12));
                builder.append(':');
                appendNumber2(builder, (int) ((hms >> 6) % (1 << 6)));
                builder.append(':');
                appendNumber2(builder, (int) (hms % (1 << 6)));
                second = builder.toString();
            }

            if (meta >= 1) {
                String microSecond = usecondsToStr(frac, meta);
                microSecond = microSecond.substring(0, meta);
                value = second + '.' + microSecond;
            } else {
                value = second;
            }

            javaType = Types.TIMESTAMP;
            length = 5 + (meta + 1) / 2;
            break;
        }
        case LogEvent.MYSQL_TYPE_TIME: {
            // MYSQL DataTypes: TIME
            // The range is '-838:59:59' to '838:59:59'
            // final int i32 = buffer.getUint24();
            final int i32 = buffer.getInt24();
            final int u32 = Math.abs(i32);
            if (i32 == 0) {
                value = "00:00:00";
            } else {
                // if (cal == null) cal = Calendar.getInstance();
                // cal.clear();
                // cal.set(70, 0, 1, i32 / 10000, (i32 % 10000) / 100, i32 %
                // 100);
                // value = new Time(cal.getTimeInMillis());
                // value = String.format("%s%02d:%02d:%02d",
                // (i32 >= 0) ? "" : "-",
                // u32 / 10000,
                // (u32 % 10000) / 100,
                // u32 % 100);

                StringBuilder builder = new StringBuilder(17);
                if (i32 < 0) {
                    builder.append('-');
                }

                int d = u32 / 10000;
                if (d > 100) {
                    builder.append(d);
                } else {
                    appendNumber2(builder, d);
                }
                builder.append(':');
                appendNumber2(builder, (u32 % 10000) / 100);
                builder.append(':');
                appendNumber2(builder, u32 % 100);
                value = builder.toString();
            }
            javaType = Types.TIME;
            length = 3;
            break;
        }
        case LogEvent.MYSQL_TYPE_TIME2: {
            /*
             * TIME low-level memory and disk representation routines In-memory format: 1 bit sign (Used for sign,
             * when on disk) 1 bit unused (Reserved for wider hour range, e.g. for intervals) 10 bit hour (0-836) 6
             * bit minute (0-59) 6 bit second (0-59) 24 bits microseconds (0-999999) Total: 48 bits = 6 bytes
             * Suhhhhhh.hhhhmmmm.mmssssss.ffffffff.ffffffff.ffffffff
             */
            long intpart = 0;
            int frac = 0;
            long ltime = 0;
            switch (meta) {
            case 0:
                intpart = buffer.getBeUint24() - TIMEF_INT_OFS; // big-endian
                ltime = intpart << 24;
                break;
            case 1:
            case 2:
                intpart = buffer.getBeUint24() - TIMEF_INT_OFS;
                frac = buffer.getUint8();
                if (intpart < 0 && frac > 0) {
                    intpart++;
                    frac -= 0x100; /* -(0x100 - frac) */
                    // fraclong = frac * 10000;
                }
                frac = frac * 10000;
                ltime = intpart << 24;
                break;
            case 3:
            case 4:
                intpart = buffer.getBeUint24() - TIMEF_INT_OFS;
                frac = buffer.getBeUint16();
                if (intpart < 0 && frac > 0) {
                    /*
                     * Fix reverse fractional part order: "0x10000 - frac". See comments for FSP=1 and FSP=2
                     * above.
                     */
                    intpart++; /* Shift to the next integer value */
                    frac -= 0x10000; /* -(0x10000-frac) */
                    // fraclong = frac * 100;
                }
                frac = frac * 100;
                ltime = intpart << 24;
                break;
            case 5:
            case 6:
                intpart = buffer.getBeUlong48() - TIMEF_OFS;
                ltime = intpart;
                frac = (int) (intpart % (1L << 24));
                break;
            default:
                intpart = buffer.getBeUint24() - TIMEF_INT_OFS;
                ltime = intpart << 24;
                break;
            }

            String second = null;
            if (intpart == 0) {
                second = "00:00:00";
            } else {
                // 目前只记录秒，不处理us frac
                // if (cal == null) cal = Calendar.getInstance();
                // cal.clear();
                // cal.set(70, 0, 1, (int) ((intpart >> 12) % (1 << 10)),
                // (int) ((intpart >> 6) % (1 << 6)),
                // (int) (intpart % (1 << 6)));
                // value = new Time(cal.getTimeInMillis());
                long ultime = Math.abs(ltime);
                intpart = ultime >> 24;

                // second = String.format("%s%02d:%02d:%02d",
                // ltime >= 0 ? "" : "-",
                // (int) ((intpart >> 12) % (1 << 10)),
                // (int) ((intpart >> 6) % (1 << 6)),
                // (int) (intpart % (1 << 6)));

                StringBuilder builder = new StringBuilder(12);
                if (ltime < 0) {
                    builder.append('-');
                }

                int d = (int) ((intpart >> 12) % (1 << 10));
                if (d >= 100) {
                    builder.append(d);
                } else {
                    appendNumber2(builder, d);
                }
                builder.append(':');
                appendNumber2(builder, (int) ((intpart >> 6) % (1 << 6)));
                builder.append(':');
                appendNumber2(builder, (int) (intpart % (1 << 6)));
                second = builder.toString();
            }

            if (meta >= 1) {
                String microSecond = usecondsToStr(Math.abs(frac), meta);
                microSecond = microSecond.substring(0, meta);
                value = second + '.' + microSecond;
            } else {
                value = second;
            }

            javaType = Types.TIME;
            length = 3 + (meta + 1) / 2;
            break;
        }
        case LogEvent.MYSQL_TYPE_NEWDATE: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_NEWDATE : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
            javaType = Types.DATE;
            value = null; /* unknown format */
            length = 0;
            break;
        }
        case LogEvent.MYSQL_TYPE_DATE: {
            // MYSQL DataTypes:
            // range: 0000-00-00 ~ 9999-12-31
            final int i32 = buffer.getUint24();
            if (i32 == 0) {
                value = "0000-00-00";
            } else {
                // if (cal == null) cal = Calendar.getInstance();
                // cal.clear();
                /* month is 0-based, 0 for january. */
                // cal.set((i32 / (16 * 32)), (i32 / 32 % 16) - 1, (i32 %
                // 32));
                // value = new java.sql.Date(cal.getTimeInMillis());
                // value = String.format("%04d-%02d-%02d", i32 / (16 * 32), i32 / 32 % 16, i32 % 32);

                StringBuilder builder = new StringBuilder(12);
                appendNumber4(builder, i32 / (16 * 32));
                builder.append('-');
                appendNumber2(builder, i32 / 32 % 16);
                builder.append('-');
                appendNumber2(builder, i32 % 32);
                value = builder.toString();
            }
            javaType = Types.DATE;
            length = 3;
            break;
        }
        case LogEvent.MYSQL_TYPE_YEAR: {
            // MYSQL DataTypes: YEAR[(2|4)]
            // In four-digit format, values display as 1901 to 2155, and
            // 0000.
            // In two-digit format, values display as 70 to 69, representing
            // years from 1970 to 2069.

            final int i32 = buffer.getUint8();
            // If connection property 'YearIsDateType' has
            // set, value is java.sql.Date.
            /*
             * if (cal == null) cal = Calendar.getInstance(); cal.clear(); cal.set(Calendar.YEAR, i32 + 1900); value
             * = new java.sql.Date(cal.getTimeInMillis());
             */
            // The else, value is java.lang.Short.
            if (i32 == 0) {
                value = "0000";
            } else {
                value = String.valueOf((short) (i32 + 1900));
            }
            // It might seem more correct to create a java.sql.Types.DATE
            // value
            // for this date, but it is much simpler to pass the value as an
            // integer. The MySQL JDBC specification states that one can
            // pass a java int between 1901 and 2055. Creating a DATE value
            // causes truncation errors with certain SQL_MODES
            // (e.g."STRICT_TRANS_TABLES").
            javaType = Types.VARCHAR; // Types.INTEGER;
            length = 1;
            break;
        }
        case LogEvent.MYSQL_TYPE_ENUM: {
            final int int32;
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            switch (len) {
            case 1:
                int32 = buffer.getUint8();
                break;
            case 2:
                int32 = buffer.getUint16();
                break;
            default:
                throw new IllegalArgumentException("!! Unknown ENUM packlen = " + len);
            }
            // logger.warn("MYSQL_TYPE_ENUM : This enumeration value is "
            // + "only used internally and cannot exist in a binlog!");
            value = Integer.valueOf(int32);
            javaType = Types.INTEGER;
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_SET: {
            final int nbits = (meta & 0xFF) * 8;
            len = (nbits + 7) / 8;
            if (nbits > 1) {
                // byte[] bits = new byte[len];
                // buffer.fillBytes(bits, 0, len);
                // 转化为unsign long
                switch (len) {
                case 1:
                    value = buffer.getUint8();
                    break;
                case 2:
                    value = buffer.getUint16();
                    break;
                case 3:
                    value = buffer.getUint24();
                    break;
                case 4:
                    value = buffer.getUint32();
                    break;
                case 5:
                    value = buffer.getUlong40();
                    break;
                case 6:
                    value = buffer.getUlong48();
                    break;
                case 7:
                    value = buffer.getUlong56();
                    break;
                case 8:
                    value = buffer.getUlong64();
                    break;
                default:
                    throw new IllegalArgumentException("!! Unknown Set len = " + len);
                }
            } else {
                final int bit = buffer.getInt8();
                // value = (bit != 0) ? Boolean.TRUE : Boolean.FALSE;
                value = bit;
            }

            javaType = Types.BIT;
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_TINY_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_TINY_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_MEDIUM_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_MEDIUM_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_LONG_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_LONG_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_BLOB: {
            /*
             * BLOB or TEXT datatype
             */
            switch (meta) {
            case 1: {
                /* TINYBLOB/TINYTEXT */
                final int len8 = buffer.getUint8();
                byte[] binary = new byte[len8];
                buffer.fillBytes(binary, 0, len8);
                value = binary;
                javaType = Types.VARBINARY;
                length = len8;
                break;
            }
            case 2: {
                /* BLOB/TEXT */
                final int len16 = buffer.getUint16();
                byte[] binary = new byte[len16];
                buffer.fillBytes(binary, 0, len16);
                value = binary;
                javaType = Types.LONGVARBINARY;
                length = len16;
                break;
            }
            case 3: {
                /* MEDIUMBLOB/MEDIUMTEXT */
                final int len24 = buffer.getUint24();
                byte[] binary = new byte[len24];
                buffer.fillBytes(binary, 0, len24);
                value = binary;
                javaType = Types.LONGVARBINARY;
                length = len24;
                break;
            }
            case 4: {
                /* LONGBLOB/LONGTEXT */
                final int len32 = (int) buffer.getUint32();
                byte[] binary = new byte[len32];
                buffer.fillBytes(binary, 0, len32);
                value = binary;
                javaType = Types.LONGVARBINARY;
                length = len32;
                break;
            }
            default:
                throw new IllegalArgumentException("!! Unknown BLOB packlen = " + meta);
            }
            break;
        }
        case LogEvent.MYSQL_TYPE_VARCHAR: {
            /*
             * Except for the data length calculation, MYSQL_TYPE_VARCHAR, MYSQL_TYPE_VAR_STRING and
             * MYSQL_TYPE_STRING are handled the same way.
             */
            len = meta;
            if (len < 256) {
                len = buffer.getUint8();
            } else {
                len = buffer.getUint16();
            }

            if (isBinary) {
                // fixed issue #66 ,binary类型在binlog中为var_string
                /* fill binary */
                byte[] binary = new byte[len];
                buffer.fillBytes(binary, 0, len);

                javaType = Types.VARBINARY;
                value = binary;
            } else {
                value = buffer.getFullString(len, charsetName);
                javaType = Types.VARCHAR;
            }

            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_STRING:
        case LogEvent.MYSQL_TYPE_VAR_STRING: {
            if (len < 256) {
                len = buffer.getUint8();
            } else {
                len = buffer.getUint16();
            }

            if (isBinary) {
                /* fill binary */
                byte[] binary = new byte[len];
                buffer.fillBytes(binary, 0, len);

                javaType = Types.BINARY;
                value = binary;
            } else {
                value = buffer.getFullString(len, charsetName);
                javaType = Types.CHAR; // Types.VARCHAR;
            }
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_JSON: {
            switch (meta) {
            case 1: {
                len = buffer.getUint8();
                break;
            }
            case 2: {
                len = buffer.getUint16();
                break;
            }
            case 3: {
                len = buffer.getUint24();
                break;
            }
            case 4: {
                len = (int) buffer.getUint32();
                break;
            }
            default:
                throw new IllegalArgumentException("!! Unknown JSON packlen = " + meta);
            }
            // len = buffer.getUint16();
            // buffer.forward(meta - 4);
            if (0 == len) {
                // fixed issue #1 by lava, json column of zero length has no value, value parsing should be skipped
                value = "";
            } else {
                int position = buffer.position();
                Json_Value jsonValue = JsonConversion.parse_value(buffer.getUint8(), buffer, len - 1, charsetName);
                StringBuilder builder = new StringBuilder();
                jsonValue.toJsonString(builder, charsetName);
                value = builder.toString();
                value = JSON.parseObject(String.valueOf(value), String.class);
                buffer.position(position + len);
                // byte[] binary = new byte[len];
                // buffer.fillBytes(binary, 0, len);
                // value = binary;
            }
            javaType = Types.VARCHAR;
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_GEOMETRY: {
            /*
             * MYSQL_TYPE_GEOMETRY: copy from BLOB or TEXT
             */
            switch (meta) {
            case 1:
                len = buffer.getUint8();
                break;
            case 2:
                len = buffer.getUint16();
                break;
            case 3:
                len = buffer.getUint24();
                break;
            case 4:
                len = (int) buffer.getUint32();
                break;
            default:
                throw new IllegalArgumentException("!! Unknown MYSQL_TYPE_GEOMETRY packlen = " + meta);
            }
            /* fill binary */
            byte[] binary = new byte[len];
            buffer.fillBytes(binary, 0, len);
            javaType = Types.BINARY;
            value = binary;
            length = len;
            break;
        }
        case LogEvent.MYSQL_TYPE_BOOL:
        case LogEvent.MYSQL_TYPE_INVALID:
        default:
            logger.error(String.format("!! Don't know how to handle column type=%d meta=%d (%04X)",
                type,
                meta,
                meta));
            javaType = Types.OTHER;
            value = null;
            length = 0;
        }

        return value;
    }

    public final byte[] fetchBinaryValue(int type, final int meta, boolean forColumnar) {
        int len = 0;

        if (type == LogEvent.MYSQL_TYPE_STRING) {
            if (meta >= 256) {
                int byte0 = meta >> 8;
                int byte1 = meta & 0xff;
                if ((byte0 & 0x30) != 0x30) {
                    /* a long CHAR() field: see #37426 */
                    len = byte1 | (((byte0 & 0x30) ^ 0x30) << 4);
                    type = byte0 | 0x30;
                } else {
                    switch (byte0) {
                    case LogEvent.MYSQL_TYPE_SET:
                    case LogEvent.MYSQL_TYPE_ENUM:
                    case LogEvent.MYSQL_TYPE_STRING:
                    case LogEvent.MYSQL_TYPE_VAR_STRING:
                        type = byte0;
                        len = byte1;
                        break;
                    default:
                        throw new IllegalArgumentException(String.format(
                            "!! Don't know how to handle column type=%d meta=%d (%04X)",
                            type,
                            meta,
                            meta));
                    }
                }
            } else {
                len = meta;
            }
        }

        switch (type) {
        case LogEvent.MYSQL_TYPE_LONG: {
            // XXX: How to check signed / unsigned?
            // value = unsigned ? Long.valueOf(buffer.getUint32()) :
            // Integer.valueOf(buffer.getInt32());
            return buffer.getData(4);
        }
        case LogEvent.MYSQL_TYPE_TINY: {
            // XXX: How to check signed / unsigned?
            // value = Integer.valueOf(unsigned ? buffer.getUint8() :
            // buffer.getInt8());
            return buffer.getData(1);
        }
        case LogEvent.MYSQL_TYPE_SHORT: {
            // XXX: How to check signed / unsigned?
            // value = Integer.valueOf(unsigned ? buffer.getUint16() :
            // buffer.getInt16());
            return buffer.getData(2);
        }
        case LogEvent.MYSQL_TYPE_INT24: {
            // XXX: How to check signed / unsigned?
            // value = Integer.valueOf(unsigned ? buffer.getUint24() :
            // buffer.getInt24());
            return buffer.getData(3);
        }
        case LogEvent.MYSQL_TYPE_LONGLONG: {
            // XXX: How to check signed / unsigned?
            // value = unsigned ? buffer.getUlong64()) :
            // Long.valueOf(buffer.getLong64());
            return buffer.getData(8);
        }
        case LogEvent.MYSQL_TYPE_DECIMAL: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_DECIMAL : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
            javaType = Types.DECIMAL;
            value = null; /* unknown format */
            length = 0;
            break;
        }
        case LogEvent.MYSQL_TYPE_NEWDECIMAL: {
            final int precision = meta >> 8;
            final int frac = meta & 0xff;

            final int intg = precision - frac;
            final int intg0 = intg / LogBuffer.DIG_PER_INT32;
            final int frac0 = frac / LogBuffer.DIG_PER_INT32;
            final int intg0x = intg - intg0 * LogBuffer.DIG_PER_INT32;
            final int frac0x = frac - frac0 * LogBuffer.DIG_PER_INT32;

            final int binSize =
                intg0 * LogBuffer.SIZE_OF_INT32 + LogBuffer.dig2bytes[intg0x] + frac0 * LogBuffer.SIZE_OF_INT32
                    + LogBuffer.dig2bytes[frac0x];

            return buffer.getData(binSize);
        }
        case LogEvent.MYSQL_TYPE_FLOAT: {
            return buffer.getData(4);
        }
        case LogEvent.MYSQL_TYPE_DOUBLE: {
            return buffer.getData(8);
        }
        case LogEvent.MYSQL_TYPE_BIT: {
            /* Meta-data: bit_len, bytes_in_rec, 2 bytes */
            final int nbits = ((meta >> 8) * 8) + (meta & 0xff);
            len = (nbits + 7) / 8;
            return buffer.getData(len);
        }
        case LogEvent.MYSQL_TYPE_TIMESTAMP: {
            // MYSQL DataTypes: TIMESTAMP
            // range is '1970-01-01 00:00:01' UTC to '2038-01-19 03:14:07'
            // UTC
            // A TIMESTAMP cannot represent the value '1970-01-01 00:00:00'
            // because that is equivalent to 0 seconds from the epoch and
            // the value 0 is reserved for representing '0000-00-00
            // 00:00:00', the “zero” TIMESTAMP value.
            return buffer.getData(4);
        }
        case LogEvent.MYSQL_TYPE_TIMESTAMP2: {
            length = 4 + (meta + 1) / 2;
            return buffer.getData(length);
        }
        case LogEvent.MYSQL_TYPE_DATETIME: {
            // MYSQL DataTypes: DATETIME
            // range is '0000-01-01 00:00:00' to '9999-12-31 23:59:59'
            return buffer.getData(8);
        }
        case LogEvent.MYSQL_TYPE_DATETIME2: {
            /*
             * DATETIME and DATE low-level memory and disk representation routines 1 bit sign (used when on disk) 17
             * bits year*13+month (year 0-9999, month 0-12) 5 bits day (0-31) 5 bits hour (0-23) 6 bits minute
             * (0-59) 6 bits second (0-59) 24 bits microseconds (0-999999) Total: 64 bits = 8 bytes
             * SYYYYYYY.YYYYYYYY .YYdddddh.hhhhmmmm.mmssssss.ffffffff.ffffffff.ffffffff
             */
            length = 5 + (meta + 1) / 2;
            return buffer.getData(length);
        }
        case LogEvent.MYSQL_TYPE_TIME: {
            // MYSQL DataTypes: TIME
            // The range is '-838:59:59' to '838:59:59'
            // final int i32 = buffer.getUint24();
            return buffer.getData(3);
        }
        case LogEvent.MYSQL_TYPE_TIME2: {
            /*
             * TIME low-level memory and disk representation routines In-memory format: 1 bit sign (Used for sign,
             * when on disk) 1 bit unused (Reserved for wider hour range, e.g. for intervals) 10 bit hour (0-836) 6
             * bit minute (0-59) 6 bit second (0-59) 24 bits microseconds (0-999999) Total: 48 bits = 6 bytes
             * Suhhhhhh.hhhhmmmm.mmssssss.ffffffff.ffffffff.ffffffff
             */
            length = 3 + (meta + 1) / 2;
            return buffer.getData(length);
        }
        case LogEvent.MYSQL_TYPE_NEWDATE: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_NEWDATE : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
            javaType = Types.DATE;
            value = null; /* unknown format */
            length = 0;
            break;
        }
        case LogEvent.MYSQL_TYPE_DATE: {
            // MYSQL DataTypes:
            // range: 0000-00-00 ~ 9999-12-31
            return buffer.getData(3);
        }
        case LogEvent.MYSQL_TYPE_YEAR: {
            // MYSQL DataTypes: YEAR[(2|4)]
            // In four-digit format, values display as 1901 to 2155, and
            // 0000.
            // In two-digit format, values display as 70 to 69, representing
            // years from 1970 to 2069.

            return buffer.getData(1);
        }
        case LogEvent.MYSQL_TYPE_ENUM: {
            length = len;
            return buffer.getData(length);
        }
        case LogEvent.MYSQL_TYPE_SET: {
            length = len;
            return buffer.getData(length);
        }
        case LogEvent.MYSQL_TYPE_TINY_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_TINY_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_MEDIUM_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_MEDIUM_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_LONG_BLOB: {
            /*
             * log_event.h : This enumeration value is only used internally and cannot exist in a binlog.
             */
            logger.warn("MYSQL_TYPE_LONG_BLOB : This enumeration value is "
                + "only used internally and cannot exist in a binlog!");
        }
        case LogEvent.MYSQL_TYPE_BLOB: {
            /*
             * BLOB or TEXT datatype
             */
            switch (meta) {
            case 1: {
                /* TINYBLOB/TINYTEXT */
                final int len8 = buffer.getUint8();
                if (forColumnar) {
                    byte[] binary = new byte[len8];
                    buffer.fillBytes(binary, 0, len8);
                    return binary;
                } else {
                    byte[] binary = new byte[len8 + 1];
                    binary[0] = (byte) len8;
                    buffer.fillBytes(binary, 1, len8);
                    return binary;
                }
            }
            case 2: {
                /* BLOB/TEXT */
                final int len16 = buffer.getUint16();
                if (forColumnar) {
                    byte[] binary = new byte[len16];
                    buffer.fillBytes(binary, 0, len16);
                    return binary;
                } else {
                    byte[] binary = new byte[len16 + 2];
                    binary[0] = (byte) (len16 & 0xFF);
                    binary[1] = (byte) ((len16 >> 8) & 0xFF);
                    buffer.fillBytes(binary, 2, len16);
                    return binary;
                }

            }
            case 3: {
                /* MEDIUMBLOB/MEDIUMTEXT */
                final int len24 = buffer.getUint24();
                if (forColumnar) {
                    byte[] binary = new byte[len24];
                    buffer.fillBytes(binary, 0, len24);
                    return binary;
                } else {
                    byte[] binary = new byte[len24 + 3];
                    binary[0] = (byte) (len24 & 0xFF);
                    binary[1] = (byte) ((len24 >> 8) & 0xFF);
                    binary[2] = (byte) ((len24 >> 16) & 0xFF);
                    buffer.fillBytes(binary, 3, len24);
                    return binary;
                }

            }
            case 4: {
                /* LONGBLOB/LONGTEXT */
                final int len32 = (int) buffer.getUint32();
                if (forColumnar) {
                    byte[] binary = new byte[len32];
                    buffer.fillBytes(binary, 0, len32);
                    return binary;
                } else {
                    byte[] binary = new byte[len32 + 4];
                    binary[0] = (byte) (len32 & 0xFF);
                    binary[1] = (byte) ((len32 >> 8) & 0xFF);
                    binary[2] = (byte) ((len32 >> 16) & 0xFF);
                    binary[3] = (byte) ((len32 >> 24) & 0xFF);
                    buffer.fillBytes(binary, 4, len32);
                    return binary;
                }

            }
            default:
                throw new IllegalArgumentException("!! Unknown BLOB packlen = " + meta);
            }
        }
        case LogEvent.MYSQL_TYPE_VARCHAR: {
            /*
             * Except for the data length calculation, MYSQL_TYPE_VARCHAR, MYSQL_TYPE_VAR_STRING and
             * MYSQL_TYPE_STRING are handled the same way.
             */
            len = meta;
            byte[] binary;
            int start = 0;
            if (len < 256) {
                len = buffer.getUint8();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 1];
                    binary[0] = (byte) len;
                    start = 1;
                }

            } else {
                len = buffer.getUint16();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 2];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    start = 2;
                }

            }

            // fixed issue #66 ,binary类型在binlog中为var_string
            /* fill binary */
            buffer.fillBytes(binary, start, len);
            return binary;
        }
        case LogEvent.MYSQL_TYPE_VAR_STRING:
        case LogEvent.MYSQL_TYPE_STRING: {
            byte[] binary;
            int start = 0;
            if (len < 256) {
                len = buffer.getUint8();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 1];
                    binary[0] = (byte) (len & 0xFF);
                    start = 1;
                }

            } else {
                len = buffer.getUint16();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 2];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    start = 2;
                }

            }

            /* fill binary */
            buffer.fillBytes(binary, start, len);

            return binary;
        }
        case LogEvent.MYSQL_TYPE_JSON: {
            byte[] binary;
            int start = 0;
            switch (meta) {
            case 1: {
                len = buffer.getUint8();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 1];
                    binary[0] = (byte) (len & 0xFF);
                    start = 1;
                }
                break;
            }
            case 2: {
                len = buffer.getUint16();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 2];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    start = 2;
                }
                break;
            }
            case 3: {
                len = buffer.getUint24();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 3];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    binary[2] = (byte) (len >> 16 & 0xFF);
                    start = 3;
                }
                break;
            }
            case 4: {
                len = (int) buffer.getUint32();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 4];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    binary[2] = (byte) (len >> 16 & 0xFF);
                    binary[3] = (byte) (len >> 24 & 0xFF);
                    start = 4;
                }
                break;
            }
            default:
                throw new IllegalArgumentException("!! Unknown JSON packlen = " + meta);
            }
            // len = buffer.getUint16();
            // buffer.forward(meta - 4);
            if (0 == len) {
                // fixed issue #1 by lava, json column of zero length has no value, value parsing should be skipped
                value = "";
                binary = new byte[0];
            } else {
                buffer.fillBytes(binary, start, len);
            }
            return binary;
        }
        case LogEvent.MYSQL_TYPE_GEOMETRY: {
            /*
             * MYSQL_TYPE_GEOMETRY: copy from BLOB or TEXT
             */
            byte[] binary;
            int start = 0;
            switch (meta) {
            case 1:
                len = buffer.getUint8();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 1];
                    binary[0] = (byte) (len & 0xFF);
                    start = 1;
                }
                break;
            case 2:
                len = buffer.getUint16();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 2];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    start = 2;
                }
                break;
            case 3:
                len = buffer.getUint24();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 3];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    binary[2] = (byte) (len >> 16 & 0xFF);
                    start = 3;
                }
                break;
            case 4:
                len = (int) buffer.getUint32();
                if (forColumnar) {
                    binary = new byte[len];
                } else {
                    binary = new byte[len + 4];
                    binary[0] = (byte) (len & 0xFF);
                    binary[1] = (byte) (len >> 8 & 0xFF);
                    binary[2] = (byte) (len >> 16 & 0xFF);
                    binary[3] = (byte) (len >> 24 & 0xFF);
                    start = 4;
                }
                break;
            default:
                throw new IllegalArgumentException("!! Unknown MYSQL_TYPE_GEOMETRY packlen = " + meta);
            }
            /* fill binary */
            buffer.fillBytes(binary, start, len);
            return binary;
        }
        default:
            logger.error(String.format("!! Don't know how to handle column type=%d meta=%d (%04X)",
                type,
                meta,
                meta));
            javaType = Types.OTHER;
            value = null;
            length = 0;
        }

        return new byte[0];
    }

    public final boolean isNull() {
        return fNull;
    }

    public final int getJavaType() {
        return javaType;
    }

    public final Serializable getValue() {
        return value;
    }

    public final int getLength() {
        return length;
    }

    private String usecondsToStr(int frac, int meta) {
        String sec = String.valueOf(frac);
        if (meta > 6) {
            throw new IllegalArgumentException("unknow useconds meta : " + meta);
        }

        if (sec.length() < 6) {
            StringBuilder result = new StringBuilder(6);
            int len = 6 - sec.length();
            for (; len > 0; len--) {
                result.append('0');
            }
            result.append(sec);
            sec = result.toString();
        }

        return sec.substring(0, meta);
    }

    public BitSet getNullBits() {
        return nullBits;
    }

    public static void appendNumber4(StringBuilder builder, int d) {
        if (d >= 1000) {
            builder.append(digits[d / 1000])
                .append(digits[(d / 100) % 10])
                .append(digits[(d / 10) % 10])
                .append(digits[d % 10]);
        } else {
            builder.append('0');
            appendNumber3(builder, d);
        }
    }

    public static void appendNumber3(StringBuilder builder, int d) {
        if (d >= 100) {
            builder.append(digits[d / 100]).append(digits[(d / 10) % 10]).append(digits[d % 10]);
        } else {
            builder.append('0');
            appendNumber2(builder, d);
        }
    }

    public static void appendNumber2(StringBuilder builder, int d) {
        if (d >= 10) {
            builder.append(digits[(d / 10) % 10]).append(digits[d % 10]);
        } else {
            builder.append('0').append(digits[d]);
        }
    }

}
