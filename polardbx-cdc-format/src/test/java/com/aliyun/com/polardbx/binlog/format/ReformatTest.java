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
package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LocalBinlogParser;
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
import com.aliyun.polardbx.binlog.format.field.BlobField;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;
import com.aliyun.polardbx.binlog.format.utils.BinlogGenerateUtil;
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.CollationCharset;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ReformatTest {
    private static final String filePath = System.getProperty("user.home") + "/binlog/binlog.000001";
    private static final AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);

    static {
        File f = new File(filePath);
        if (f.exists()) {
            f.delete();
        }
        output.put(FileLogFetcher.BINLOG_MAGIC);
    }

    private final LocalBinlogParser localBinlogParser = new LocalBinlogParser(filePath);

    @BeforeClass
    public static void beforeClass() {
        DynamicApplicationConfig.setConfigDataProvider(key -> "");
    }

    public void generateFDE() throws Exception {
        FormatDescriptionEvent event = BinlogGenerateUtil.buildFormatDescriptionEvent(1, "5.6.29-TDDL-5.x");
        event.write(output);
    }

    protected byte[] toByte(long data, int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) ((data >> (i * 8)) & (0xFF));
        }
        return bytes;
    }

    @Test
    public void generateData() throws Exception {
        generateFDE();
        generateQueryLog();

        TableMapEventBuilder tme = new TableMapEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            123,
            "test_abc_db",
            "test_abc_tb",
            "utf8");
        List<Field> fieldList = new ArrayList<>();

        fieldList.add(MakeFieldFactory.makeField("BIGINT(20) unsigned", "18446744073709551615", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("mediumint(24) unsigned", "16777215", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("binary(200)",
            "JnZqWzexLzdYKIhJrhOfSAWzkSfiERRINcESCMqsKljBDyBeaMXuZRQSUIJMoRgxOlTtyOZJVEDOjWvPjKOeJzPlwvhQbfMqFKHSMXoSIBuTdonqaDIsrxxWJRDWlRhjvAyaXDUNEDOpkvjRLJGJikhfJkWPRXpOpCBALBixEFUQKPRSexqhpHPTDOyXYfZeQcvWJeFKA",
            "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(10)", "bb", "utf8", false, false));
        fieldList.add(MakeFieldFactory
            .makeField("JSON", "{\"test\":[100,200,300]}", "utf8", false, false));
        fieldList.add(MakeFieldFactory
            .makeField("JSON", "{\"id\": 1,\"name\": \"muscleape\", \"scores\":[100,200,300]}", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("JSON", null, "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("DECIMAL(18, 2)", "1.8", "utf8", false, false));
        fieldList
            .add(MakeFieldFactory.makeField("DECIMAL(20, 10)", "9999999999999999.999999999", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("DECIMAL(10, 3)", "16777215", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("DATETIME(3)", "2022-06-06 10:22:33.555", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("DATE", "-838:59:59.666000", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("TIMESTAMP(3)", "00:00:45.184000", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("TIME(6)", "-838:59:59.666", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("tinytext", "我是中国人", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("mediumtext", "我是中国人", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("text", "我是中国人", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("YEAR", "2022", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("enum('red','green','yellow')", "red", "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("set(1,2,3)", "2", "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("geometry", null, "utf8", true, false));
        fieldList.add(MakeFieldFactory
            .makeField("geometry", "0x00000000010100000000000000000049400000000000805140", "utf8", true, false));
        fieldList.add(MakeFieldFactory.makeField("bit", null, "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("decimal", "-1613793319", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("decimal", "9999999999", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("decimal", "-5839673.5", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("decimal(10,3)", "1.787E7", "utf8", false, false));
        fieldList.add(MakeFieldFactory.makeField("boolean", "true", "utf8", false, false));

        BlobField field = (BlobField) MakeFieldFactory.makeField("tinytext", "我是中国人", "utf8", false, false);
        field.setContents("你好，我是中国人".getBytes("utf8"));
        fieldList.add(field);
        tme.setFieldList(fieldList);
        int len = tme.write(output);
        RowEventBuilder reb = new RowEventBuilder(tme.getTableId(),
            fieldList.size(),
            BinlogEventType.WRITE_ROWS_EVENT,
            (int) (System.currentTimeMillis() / 1000),
            1);
        reb.setColumnCount(fieldList.size());
        reb.setColumnsBitMap(new BitMap(fieldList.size(), true));
        RowData rowData = new RowData();
        List<Field> biFieldList = new ArrayList<>();
        BitMap nullBitmap = new BitMap(fieldList.size());
        for (int i = 0; i < fieldList.size(); i++) {
            Field f = fieldList.get(i);
            nullBitmap.set(i, f.isNull());
            if (!f.isNull()) {
                biFieldList.add(f);
            }
        }

        rowData.setBiFieldList(biFieldList);

        rowData.setBiNullBitMap(nullBitmap);
        reb.addRowData(rowData);
        int newLen = reb.write(output);

        FileUtils.writeByteArrayToFile(new File(filePath), output.toBytes(), 0, output.position(), true);
        System.out.println("write to file path :" + filePath);
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
    }

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

}
