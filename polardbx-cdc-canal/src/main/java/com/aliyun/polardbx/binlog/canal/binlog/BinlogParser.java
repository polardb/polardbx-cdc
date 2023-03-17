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
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowData;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent.ColumnInfo;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta.FieldMeta;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Types;
import java.util.BitSet;
import java.util.List;

/**
 * @author chengjin.lyf on 2020/7/27 8:15 下午
 * @since 1.0.25
 */
public class BinlogParser {

    public static final String ISO_8859_1 = "ISO-8859-1";
    public static final String UTF_8 = "UTF-8";
    public static final int TINYINT_MAX_VALUE = 256;
    public static final int SMALLINT_MAX_VALUE = 65536;
    public static final int MEDIUMINT_MAX_VALUE = 16777216;
    public static final long INTEGER_MAX_VALUE = 4294967296L;
    public static final BigInteger BIGINT_MAX_VALUE = new BigInteger("18446744073709551616");
    public static final int version = 1;
    public static final String BEGIN = "BEGIN";
    public static final String COMMIT = "COMMIT";

    private TableMeta tableMeta;

    private RowsLogEvent rowsLogEvent;

    private RowsLogBuffer rowsLogBuffer;

    private DBMSAction action = null;

    private String charset;

    private DefaultRowChange rowChange;

    public void parse(TableMeta tableMeta, RowsLogEvent rowsLogEvent,
                      String charset) throws UnsupportedEncodingException {

        this.tableMeta = tableMeta;
        this.rowsLogEvent = rowsLogEvent;
        this.rowsLogBuffer = rowsLogEvent.getRowsBuf(charset);
        this.charset = charset;

        int type = rowsLogEvent.getHeader().getType();
        if (LogEvent.WRITE_ROWS_EVENT_V1 == type || LogEvent.WRITE_ROWS_EVENT == type) {
            action = DBMSAction.INSERT;
        } else if (LogEvent.UPDATE_ROWS_EVENT_V1 == type || LogEvent.UPDATE_ROWS_EVENT == type) {
            action = DBMSAction.UPDATE;
        } else if (LogEvent.DELETE_ROWS_EVENT_V1 == type || LogEvent.DELETE_ROWS_EVENT == type) {
            action = DBMSAction.DELETE;
        } else {
            throw new CanalParseException("unsupport event type :" + rowsLogEvent.getHeader().getType());
        }

        BitSet columns = rowsLogEvent.getColumns();
        BitSet changeColumns = rowsLogEvent.getChangeColumns(); // 非变更的列,而是指存在binlog记录的列,mysql full image模式会提供所有列

        int columnSize = rowsLogEvent.getTable().getColumnCnt();
        ColumnInfo[] columnInfo = rowsLogEvent.getTable().getColumnInfo();

        TableMapLogEvent table = rowsLogEvent.getTable();

        // 构造列信息
        List<DBMSColumn> dbmsColumns = Lists.newArrayList();
        List<FieldMeta> fieldMetas = tableMeta.getFields();
        // 兼容一下canal的逻辑,认为DDL新增列都加在末尾,如果表结构的列比binlog的要多
        int size = fieldMetas.size();
        if (columnSize < size) {
            size = columnSize;
        }
        for (int i = 0; i < size; i++) {
            FieldMeta fieldMeta = fieldMetas.get(i);
            // 先临时加一个sqlType=0的值
            DefaultColumn column = new DefaultColumn(fieldMeta.getColumnName(),
                i,
                Types.OTHER,
                fieldMeta.isUnsigned(),
                fieldMeta.isNullable(),
                fieldMeta.isKey(),
                fieldMeta.isUnique());
            dbmsColumns.add(column);
        }

        rowChange = new DefaultRowChange(action,
            table.getDbName(),
            table.getTableName(),
            new DefaultColumnSet(dbmsColumns));
        BitSet actualChangeColumns = new BitSet(columnSize); // 需要处理到update类型时，基于数据内容进行判定
        rowChange.setChangeColumnsBitSet(actualChangeColumns);
        boolean tableError = false;
        while (rowsLogBuffer.nextOneRow(columns)) {
            // 处理row记录
            if (DBMSAction.INSERT == action) {
                // insert的记录放在before字段中
                tableError |= parseOneRow(rowChange,
                    rowsLogEvent,
                    rowsLogBuffer,
                    columns,
                    false,
                    tableMeta,
                    actualChangeColumns);
            } else if (DBMSAction.DELETE == action) {
                // delete的记录放在before字段中
                tableError |= parseOneRow(rowChange,
                    rowsLogEvent,
                    rowsLogBuffer,
                    columns,
                    false,
                    tableMeta,
                    actualChangeColumns);
            } else {
                // update需要处理before/after
                tableError |= parseOneRow(rowChange,
                    rowsLogEvent,
                    rowsLogBuffer,
                    columns,
                    false,
                    tableMeta,
                    actualChangeColumns);
                if (!rowsLogBuffer.nextOneRow(changeColumns)) {
                    break;
                }

                tableError |= parseOneRow(rowChange,
                    rowsLogEvent,
                    rowsLogBuffer,
                    changeColumns,
                    true,
                    tableMeta,
                    actualChangeColumns);
            }
        }

    }

    private boolean parseOneRow(DefaultRowChange rowChange, RowsLogEvent event, RowsLogBuffer buffer, BitSet cols,
                                boolean isAfter, TableMeta tableMeta, BitSet actualChangeColumns)
        throws UnsupportedEncodingException {
        int columnCnt = event.getTable().getColumnCnt();
        ColumnInfo[] columnInfo = event.getTable().getColumnInfo();

        boolean tableError = false;

        DefaultRowData rowData = new DefaultRowData(columnCnt);
        for (int i = 0; i < columnCnt; i++) {
            ColumnInfo info = columnInfo[i];
            // mysql 5.6开始支持nolob/mininal类型,并不一定记录所有的列,需要进行判断
            if (!cols.get(i)) {
                continue;
            }

            // if (existRDSNoPrimaryKey && i == columnCnt - 1 && info.type ==
            // LogEvent.MYSQL_TYPE_LONGLONG) {
            // // 不解析最后一列
            // buffer.nextValue(info.type, info.meta, false);
            // continue;
            // }

            FieldMeta fieldMeta = null;
            if (tableMeta != null && !tableError) {
                // 处理file meta
                fieldMeta = tableMeta.getFields().get(i);
            }
            // fixed issue
            // https://github.com/alibaba/canal/issues/66，特殊处理binary/varbinary，不能做编码处理
            boolean isBinary = false;
            if (fieldMeta != null) {
                if (StringUtils.containsIgnoreCase(fieldMeta.getColumnType(), "VARBINARY")) {
                    isBinary = true;
                } else if (StringUtils.containsIgnoreCase(fieldMeta.getColumnType(), "BINARY")) {
                    isBinary = true;
                }
            }
            try {
                buffer.nextValue(info.type, info.meta, isBinary);
            } catch (Exception e) {
                throw new PolardbxException("fetch value occur error ! " + fieldMeta.getColumnName() + " !", e);
            }
            // if (existRDSNoPrimaryKey && i == columnCnt - 1 && info.type ==
            // LogEvent.MYSQL_TYPE_LONGLONG) {
            // // 不解析最后一列
            // continue;
            // }

            int javaType = buffer.getJavaType();
            Serializable dataValue = null;
            if (buffer.isNull()) {
                dataValue = null;
            } else {
                final Serializable value = buffer.getValue();
                // 处理各种类型
                switch (javaType) {
                case Types.INTEGER:
                case Types.TINYINT:
                case Types.SMALLINT:
                case Types.BIGINT:
                    // 处理unsigned类型
                    Number number = (Number) value;
                    if (fieldMeta != null && fieldMeta.isUnsigned() && number.longValue() < 0) {
                        switch (buffer.getLength()) {
                        case 1: /* MYSQL_TYPE_TINY */
                            dataValue = String.valueOf(Integer.valueOf(TINYINT_MAX_VALUE + number.intValue()));
                            javaType = Types.SMALLINT; // 往上加一个量级
                            break;

                        case 2: /* MYSQL_TYPE_SHORT */
                            dataValue = String.valueOf(Integer.valueOf(SMALLINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 3: /* MYSQL_TYPE_INT24 */
                            dataValue = String
                                .valueOf(Integer.valueOf(MEDIUMINT_MAX_VALUE + number.intValue()));
                            javaType = Types.INTEGER; // 往上加一个量级
                            break;

                        case 4: /* MYSQL_TYPE_LONG */
                            dataValue = String.valueOf(Long.valueOf(INTEGER_MAX_VALUE + number.longValue()));
                            javaType = Types.BIGINT; // 往上加一个量级
                            break;

                        case 8: /* MYSQL_TYPE_LONGLONG */
                            dataValue = BIGINT_MAX_VALUE.add(BigInteger.valueOf(number.longValue())).toString();
                            javaType = Types.DECIMAL; // 往上加一个量级，避免执行出错
                            break;
                        }
                    } else {
                        // 对象为number类型，直接valueof即可
                        dataValue = String.valueOf(value);
                    }
                    break;
                case Types.REAL: // float
                case Types.DOUBLE: // double
                    // 对象为number类型，直接valueof即可
                    dataValue = String.valueOf(value);
                    break;
                case Types.BIT:// bit
                    // 对象为byte[]类型,不能直接转为字符串,入库的时候会有问题
                    dataValue = value;
                    break;
                case Types.DECIMAL:
                    dataValue = ((BigDecimal) value).toPlainString();
                    break;
                case Types.TIMESTAMP:
                    // 修复时间边界值
                    // String v = value.toString();
                    // v = v.substring(0, v.length() - 2);
                    // columnBuilder.setValue(v);
                    // break;
                case Types.TIME:
                case Types.DATE:
                    // 需要处理year
                    dataValue = value.toString();
                    break;
                case Types.BINARY:
                case Types.VARBINARY:
                case Types.LONGVARBINARY:
                    // fixed text encoding
                    // https://github.com/AlibabaTech/canal/issues/18
                    // mysql binlog中blob/text都处理为blob类型，需要反查table
                    // meta，按编码解析text
                    if (fieldMeta != null && isText(fieldMeta.getColumnType())) {
                        dataValue = new String((byte[]) value, charset);
                        javaType = Types.CLOB;
                    } else {
                        // byte数组，直接使用iso-8859-1保留对应编码，浪费内存
                        dataValue = (byte[]) value;
                        javaType = Types.BLOB;
                    }
                    break;
                case Types.CHAR:
                case Types.VARCHAR:
                    dataValue = value.toString();
                    break;
                default:
                    dataValue = value.toString();
                }

            }

            // 下标从1开始
            rowData.setRowValue(i + 1, dataValue);
            // 处理一下sqlType
            DBMSColumn dbmsColumn = rowChange.getColumns().get(i);
            if (dbmsColumn instanceof DefaultColumn && ((DefaultColumn) dbmsColumn).getSqlType() == Types.OTHER) {
                ((DefaultColumn) dbmsColumn).setSqlType(javaType);
            }
        }

        if (isAfter) {
            rowChange.addChangeData(rowData);
            // 处理一下变更列
            DBMSRowData beforeRowData = rowChange.getRowData(rowChange.getRowSize());
            buildChangeColumns(beforeRowData, rowData, columnCnt, actualChangeColumns);
        } else {
            rowChange.addRowData(rowData);
        }

        return tableError;
    }

    private boolean isText(String columnType) {
        return "LONGTEXT".equalsIgnoreCase(columnType) || "MEDIUMTEXT".equalsIgnoreCase(columnType)
            || "TEXT".equalsIgnoreCase(columnType) || "TINYTEXT".equalsIgnoreCase(columnType);
    }

    private void buildChangeColumns(DBMSRowData beforeRowData, DBMSRowData afterRowData, int size,
                                    BitSet changeColumns) {
        for (int i = 1; i <= size; i++) {
            Serializable before = beforeRowData.getRowValue(i);
            Serializable after = afterRowData.getRowValue(i);

            boolean check = isUpdate(before, after);
            if (check) {
                changeColumns.set(i - 1, true);
            }
        }

    }

    private boolean isUpdate(Serializable before, Serializable after) {
        if (before == null && after == null) {
            return false;
        } else if (before != null && after != null && before.equals(after)) {
            return false;
        }

        // 比如nolob/minial模式下,可能找不到before记录,认为是有变化
        return true;
    }

    public Serializable getField(String columnName) {
        return rowChange.getRowValue(1, columnName);
    }
}
