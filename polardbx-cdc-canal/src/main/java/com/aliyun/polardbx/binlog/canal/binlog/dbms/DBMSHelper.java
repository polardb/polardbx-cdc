/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import javax.sql.rowset.serial.SerialBlob;
import java.io.Serializable;
import java.sql.Types;

/**
 * Defines a set of utilities of <code>DBMSEvent</code> handling.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class DBMSHelper {
    /**
     * Appends a message to a given <code>StringBuilder</code>, adds a newline
     * character at the end.
     *
     * @param builder StringBuilder object to add a message to.
     * @param msg String to print.
     */
    private static void println(StringBuilder builder, String msg) {
        builder.append(msg);
        builder.append('\n');
    }

    /**
     * Formats and prints DBMSEvent into a given <code>StringBuilder</code>.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param event DBMSEvent to print out.
     */
    public static void printDBMSEvent(StringBuilder builder, DBMSEvent event) {
        printDBMSEvent(builder, event, null);
    }

    /**
     * Formats and prints DBMSEvent into a given <code>StringBuilder</code>.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param event DBMSEvent to print out.
     * @param lastSchema Last printed schema name.
     * @return Last printed schema name.
     */
    public static String printDBMSEvent(StringBuilder builder, DBMSEvent event,
                                        String lastSchema) {
        if (event instanceof DBMSRowChange) {
            DBMSRowChange rowChange = (DBMSRowChange) event;
            lastSchema = printRowChange(builder, rowChange, lastSchema);
        } else if (event instanceof DBMSQueryLog) {
            DBMSQueryLog queryLog = (DBMSQueryLog) event;
            lastSchema = printQueryLog(builder, queryLog, lastSchema);
        } else {
            println(builder, "# " + event.getClass().getName()
                + ": not supported.");
        }
        return lastSchema;
    }

    /**
     * Prints DBMSQueryLog event.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param event DBMSQueryLog object to format and print.
     * @param lastSchema Last printed schema name.
     * @return Last printed schema name.
     */
    private static String printQueryLog(StringBuilder builder,
                                        DBMSQueryLog event, String lastSchema) {
        // Output schema name if needed.
        String schema = event.getSchema();
        lastSchema = printSchema(builder, schema, lastSchema);
        // Output actual DML/DDL statement.
        println(builder, "- SQL = " + event.getQuery());
        printOptions(builder, event);
        return lastSchema;
    }

    /**
     * Format and print schema name if it differs from the last printed schema
     * name.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param schema Schema name to print.
     * @param lastSchema Last printed schema name.
     * @return Schema name as given in the schema parameter.
     */
    private static String printSchema(StringBuilder builder, String schema,
                                      String lastSchema) {
        if (schema != null
            && (lastSchema == null || (lastSchema != null && !lastSchema.equals(schema)))) {
            println(builder, "- SCHEMA = " + schema);
            return schema;
        }
        return lastSchema;
    }

    /**
     * Format and print options.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param event DBMSQueryLog object to format and print.
     */
    private static void printOptions(StringBuilder builder, DBMSQueryLog event) {
        for (DBMSOption option : event.getOptions()) {
            println(builder, " - OPTION: " + option);
        }
    }

    /**
     * Prints RowChangeData event.
     *
     * @param builder StringBuilder object to append formatted contents to.
     * @param event RowChangeData object to format and print.
     * @param lastSchema Last printed schema name.
     * @return Last printed schema name.
     */
    private static String printRowChange(StringBuilder builder,
                                         DBMSRowChange event, String lastSchema) {
        // Output schema name if needed.
        String schema = event.getSchema();
        lastSchema = printSchema(builder, schema, lastSchema);
        println(builder, "- TABLE = " + event.getTable());
        println(builder, "- ACTION = " + event.getAction());

        // Output row change details.
        final int columnCount = event.getColumnSize();
        for (int rownum = 1; rownum <= event.getRowSize(); rownum++) {
            println(builder, " - ROW# = " + rownum);

            // Print column values.
            DBMSRowData data = event.getRowData(rownum);
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                DBMSColumn column = event.getColumn(columnIndex);
                Serializable value = data.getRowValue(columnIndex);
                printColumn(builder, column, value, "COL");
                builder.append('\n');
            }

            // Print change values.
            DBMSRowData dataChange = event.getChangeData(rownum);
            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                if (event.hasChangeColumn(columnIndex)) {
                    DBMSColumn column = event.getColumn(columnIndex);
                    Serializable value = dataChange.getRowValue(columnIndex);
                    printColumn(builder, column, value, "UPD");
                    builder.append('\n');
                }
            }
        }

        return lastSchema;
    }

    /**
     * Maximum length of characters to print out for a BLOB. If BLOB is larger,
     * it is truncated and "<...>" is added to the end. <br/>
     */
    private static final int maxBlobPrintLength = 1000;

    /**
     * Formats column and column value for printing.
     */
    private static void printColumn(StringBuilder builder, DBMSColumn column,
                                    Serializable value, String prefix) {
        builder.append("   " + prefix + "#");
        builder.append(column.getOrdinalIndex() + ": " + column.getName());
        builder.append("(" + getSqlTypeName(column.getSqlType()) + ") = ");
        if (value == null) {
            builder.append("NULL");
        } else if (value instanceof SerialBlob) {
            try {
                SerialBlob binary = (SerialBlob) value;
                builder.append(new String(
                    binary.getBytes(1, maxBlobPrintLength)));
                if (binary.length() > maxBlobPrintLength) {
                    builder.append("<...>");
                }
            } catch (Exception e) {
                builder.append(value);
            }
        } else {
            builder.append(value);
        }
    }

    public static String getSqlTypeName(int sqlType) {
        switch (sqlType) {
        case Types.BIT:
            return "BIT";
        case Types.TINYINT:
            return "TINYINT";
        case Types.SMALLINT:
            return "SMALLINT";
        case Types.INTEGER:
            return "INTEGER";
        case Types.BIGINT:
            return "BIGINT";
        case Types.FLOAT:
            return "FLOAT";
        case Types.REAL:
            return "REAL";
        case Types.DOUBLE:
            return "DOUBLE";
        case Types.NUMERIC:
            return "NUMERIC";
        case Types.DECIMAL:
            return "DECIMAL";
        case Types.CHAR:
            return "CHAR";
        case Types.VARCHAR:
            return "VARCHAR";
        case Types.LONGVARCHAR:
            return "LONGVARCHAR";
        case Types.DATE:
            return "DATE";
        case Types.TIME:
            return "TIME";
        case Types.TIMESTAMP:
            return "TIMESTAMP";
        case Types.BINARY:
            return "BINARY";
        case Types.VARBINARY:
            return "VARBINARY";
        case Types.LONGVARBINARY:
            return "LONGVARBINARY";
        case Types.NULL:
            return "NULL";
        case Types.OTHER:
            return "OTHER";
        case Types.JAVA_OBJECT:
            return "JAVA_OBJECT";
        case Types.DISTINCT:
            return "DISTINCT";
        case Types.STRUCT:
            return "STRUCT";
        case Types.ARRAY:
            return "ARRAY";
        case Types.BLOB:
            return "BLOB";
        case Types.CLOB:
            return "CLOB";
        case Types.REF:
            return "REF";
        case Types.DATALINK:
            return "DATALINK";
        case Types.BOOLEAN:
            return "BOOLEAN";
        case Types.ROWID:
            return "ROWID";
        case Types.NCHAR:
            return "NCHAR";
        case Types.NVARCHAR:
            return "NVARCHAR";
        case Types.LONGNVARCHAR:
            return "LONGNVARCHAR";
        case Types.NCLOB:
            return "NCLOB";
        case Types.SQLXML:
            return "SQLXML";
        }

        return Integer.toString(sqlType);
    }
}
