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

package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.format.QueryEventBuilder;
import com.aliyun.polardbx.binlog.format.RowData;
import com.aliyun.polardbx.binlog.format.RowEventBuilder;
import com.aliyun.polardbx.binlog.format.TableMapEventBuilder;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class BinlogTest {

    //    private static String filePath = "/Users/yanfenglin/Documents/binlog/binlog.000017";
    private static String filePath = "/Users/yanfenglin/Documents/polardbx-binlog/dumper/binlog/binlog.000001";
    //    private static String filePath = "/Users/yanfenglin/Documents/binlog/binlog.000001";
    private static AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);
    //    static {
    //        File f = new File(filePath);
    //        if (f.exists()) {
    //            f.delete();
    //        }
    //        for (int i = 0; i < 4; i++) {
    //            output[i] = FileLogFetcher.BINLOG_MAGIC[i];
    //        }
    //    }

    private LocalBinlogParser localBinlogParser = new LocalBinlogParser(filePath);

    @Test
    public void generateFDE() throws Exception {
        FormatDescriptionEvent event = BinlogGenerateUtil.buildFormatDescriptionEvent(1, "5.6.29-TDDL-5.x");
        int len = event.write(output);
        System.out.println(len);
        FileUtils.writeByteArrayToFile(new File(filePath), output.toBytes(), 0, len, false);
    }

    @Test
    public void generateTME() throws Exception {

        generateFDE();

        TableMapEventBuilder tme = new TableMapEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            123,
            "test_abc_db",
            "test_abc_tb");
        List<Field> fieldList = new ArrayList<>();

        fieldList.add(MakeFieldFactory.makeField("BIGINT(20)", "1", "utf8mb4", false));
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(20)", "abcdefg", "utf8mb4", false));
        tme.setFieldList(fieldList);
        int len = tme.write(output);
        RowEventBuilder reb = new RowEventBuilder((int) tme.getTableId(),
            2,
            BinlogEventType.WRITE_ROWS_EVENT,
            (int) (System.currentTimeMillis() / 1000),
            1);
        reb.setColumnCount(2);
        reb.setColumnsBitMap(new BitMap(2, true));
        RowData rowData = new RowData();
        rowData.setBiFieldList(fieldList);
        rowData.setBiNullBitMap(new BitMap(2));
        reb.addRowData(rowData);
        int newLen = reb.write(output);

        FileUtils.writeByteArrayToFile(new File(filePath), output.toBytes(), 0, newLen + len, true);
        test();
    }

    @Test
    public void generateQueryLog() throws Exception {
        generateFDE();
        QueryEventBuilder ddlBuilder = new QueryEventBuilder("",
            "create database test_ddl",
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            true,
            (int) (System.currentTimeMillis() / 1000),
            1);
        int len = ddlBuilder.write(output);
        QueryEventBuilder queryEventBuilder = new QueryEventBuilder("test_db",
            "/*DRDS /127.0.0.1/11cffbe3f6c00000/ */CREATE TABLE `test_aaab_1Ot8` (\n"
                + "\tid bigint(20) PRIMARY KEY AUTO_INCREMENT,\n" + "\tname varchar(20)\n" + ")",
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            CollationCharset.utf8mb4Charset.getId(),
            true,
            (int) (System.currentTimeMillis() / 1000),
            1);
        len += queryEventBuilder.write(output);
        FileUtils.writeByteArrayToFile(new File(filePath), output.toBytes(), 0, len, true);
        test();
    }

    @Test
    public void test() throws IOException, TableIdNotFoundException {
        BinlogPosition position = new BinlogPosition("master-bin.000067", 124912750L, -1, -1);
        localBinlogParser.dump(new SinkFunction() {

            @Override
            public boolean sink(LogEvent event, LogPosition logPosition)
                throws CanalParseException, TableIdNotFoundException {
                if (event instanceof RowsLogEvent) {
                    RowsLogEvent re = (RowsLogEvent) event;
                    RowsLogBuffer buffer = ((RowsLogEvent) event).getRowsBuf("utf8");
                    TableMapLogEvent tme = re.getTable();
                    if (tme.getTableName().equalsIgnoreCase("t_enum")) {
                        System.out.println("enum");
                    }
                    while (buffer.nextOneRow(re.getColumns())) {
                        int i = 0;
                        Serializable[] data = new Serializable[tme.getColumnCnt()];
                        Serializable[] changeData = new Serializable[tme.getColumnCnt()];
                        for (TableMapLogEvent.ColumnInfo columnInfo : tme.getColumnInfo()) {
                            Serializable value = buffer.nextValue(columnInfo.type, columnInfo.meta);
                            data[i] = value;
                            i++;
                        }
                        i = 0;
                        if (re instanceof UpdateRowsLogEvent) {
                            if (buffer.nextOneRow(re.getChangeColumns())) {
                                int idx = 0;
                                for (TableMapLogEvent.ColumnInfo columnInfo : tme.getColumnInfo()) {
                                    idx++;
                                    if (re.getChangeColumns().get(idx - 1)) {
                                        Serializable value = buffer.nextValue(columnInfo.type, columnInfo.meta);
                                        changeData[i] = value;
                                        i++;
                                    }
                                }
                            }
                        }
                        StringBuffer sb = new StringBuffer();
                        BinlogEventType eventType = BinlogEventType.valueOf(re.getHeader().getType());
                        String tableName = re.getTable().getTableName();
                        sb.append("rows begin " + event.getHeader().getLogPos() + " ------------- " + tableName)
                            .append("\n");
                        switch (eventType) {
                        case UPDATE_ROWS_EVENT:
                            sb.append("UPDATE \t");
                            for (Serializable s : data) {
                                sb.append(s).append(",");
                            }

                            sb.append("\n\t");
                            for (Serializable s : changeData) {
                                sb.append(s).append(",");
                            }
                            break;
                        case WRITE_ROWS_EVENT:
                            sb.append("INSERT \t");
                            for (Serializable s : data) {
                                sb.append(s).append(",");
                            }
                            break;
                        case DELETE_ROWS_EVENT:
                            break;
                        }
                        sb.append("\nrows end ---------");
                        System.out.println(sb.toString());
                    }

                }
                if (event instanceof QueryLogEvent) {
                    QueryLogEvent queryLogEvent = (QueryLogEvent) event;
                    StringBuilder sb = new StringBuilder();
                    sb.append("query event : [")
                        .append(queryLogEvent.getDbName())
                        .append(queryLogEvent.getQuery())
                        .append("] ")
                        .append(queryLogEvent.getHeader().getLogPos());
                    System.out.println(sb.toString());
                }
                if (event instanceof TableMapLogEvent) {
                    TableMapLogEvent table = (TableMapLogEvent) event;
                    StringBuilder sb = new StringBuilder();
                    sb.append("table map : [");
                    for (TableMapLogEvent.ColumnInfo info : table.getColumnInfo()) {
                        sb.append(info.type).append(":").append(info.meta).append(", ");
                    }
                    sb.append("] ").append(table.getHeader().getLogPos());

                    System.out.println(sb.toString());
                }
                return true;
            }

        });
    }

    @Test
    public void testEncode() {
        Charset charset = Charset.defaultCharset();
        System.out.println(charset.newEncoder().maxBytesPerChar());
    }
}
