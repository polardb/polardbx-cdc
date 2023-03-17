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

import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.UpdateRowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
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
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BinlogTest {

    private static String filePath = System.getProperty("user.dir") + "/binlog/binlog.000001";
    private static AutoExpandBuffer output = new AutoExpandBuffer(1024, 1024);
    private static List<Computer> assertCallback = new ArrayList<>();

    static {
        File f = new File(filePath);
        if (f.exists()) {
            f.delete();
        }
        output.put(FileLogFetcher.BINLOG_MAGIC);
    }

    private LocalBinlogParser localBinlogParser = new LocalBinlogParser(filePath);

    @Test
    public void testDataFormat() throws Exception {
        generateFDE();
        generateQueryLog();
        generateRowData();
        FileUtils.writeByteArrayToFile(new File(filePath), output.toBytes(), 0, output.position(), true);
        testParseData();
    }

    private void generateFDE() throws Exception {
        FormatDescriptionEvent event = BinlogGenerateUtil.buildFormatDescriptionEvent(1, "5.6.29-TDDL-5.x");
        int len = event.write(output);
    }

    public void generateRowData() throws Exception {

        TableMapEventBuilder tme = new TableMapEventBuilder((int) (System.currentTimeMillis() / 1000),
            1,
            123,
            "test_abc_db",
            "test_abc_tb",
            "utf8");
        List<Field> fieldList = new ArrayList<>();

        fieldList.add(MakeFieldFactory.makeField("int unsigned", "4", "utf8", false, true));
        test(4, "int unsigned test error");
        fieldList.add(MakeFieldFactory.makeField("int(10) unsigned", "5", "utf8", false, true));
        test(5, "int(10) unsigned test error");
        fieldList.add(MakeFieldFactory.makeField("BIGINT( 20)", "6", "utf8", false, false));
        test(6L, "bigint( 20) unsigned test error");
        fieldList.add(MakeFieldFactory.makeField("MEDIUMINT", "999", "utf8", false, false));
        test(999, "MEDIUMINT test error");
        fieldList.add(MakeFieldFactory.makeField("BIGINT", "888", "utf8", false, false));
        test(888L, "BIGINT test error");
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(256)", "aa", "utf8", false, false));
        test("aa", "VARCHAR(256) test error");
        fieldList.add(MakeFieldFactory.makeField("VARCHAR(10)", "bb", "utf8", false, false));
        test("bb", "VARCHAR(10) test error");
        fieldList.add(MakeFieldFactory.makeField("CHAR(10)", "cc", "utf8", false, false));
        test("cc", "CHAR(10) test error");
        fieldList.add(MakeFieldFactory.makeField("tinyint(10)", "3", "utf8", false, false));
        test(3, "tinyint(10) test error");
        fieldList.add(MakeFieldFactory.makeField("tinyint", "4", "utf8", false, false));
        test(4, "tinyint test error");
        fieldList.add(MakeFieldFactory.makeField("dec(20,5)", "8.4", "utf8", false, false));
        test(new BigDecimal("8.40000"), "dec(20,5) test error");
        fieldList.add(MakeFieldFactory
            .makeField("decimal(20,10)", "3.14", "utf8", false, false));
        test(new BigDecimal("3.140000000"), "dec(20,10) test error");
        fieldList.add(MakeFieldFactory
            .makeField("decimal(30,10)", "31415926534521.3214521234", "utf8", false, false));
        test(new BigDecimal("31415926534521.3214521234"), "dec(20,10) test error");
        String now = DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss");
        fieldList.add(MakeFieldFactory
            .makeField("timestamp", now, "utf8",
                false, false));
        test(now, "timestamp test error");
        String now1 = DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss");
        fieldList.add(MakeFieldFactory
            .makeField("datetime(3)", now1, "utf8",
                false, false));
        test(now1, "datetime(3) test error");
        String now2 = DateFormatUtils.format(System.currentTimeMillis(), "yyyy-MM-dd");
        fieldList.add(MakeFieldFactory
            .makeField("date", now2, "utf8",
                false, false));
        test(now2, "date test error");
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
        rowData.setBiFieldList(fieldList);
        rowData.setBiNullBitMap(new BitMap(fieldList.size()));
        reb.addRowData(rowData);
        int newLen = reb.write(output);

    }

    public void generateQueryLog() throws Exception {
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

    public void testParseData() throws IOException, TableIdNotFoundException {
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
                            System.out.println(sb.toString());
                            break;
                        case WRITE_ROWS_EVENT:
                            sb.append("INSERT \t");
                            for (Serializable s : data) {
                                sb.append(s).append(",");
                            }
                            System.out.println(sb.toString());
                            for (int j = 0; j < data.length; j++) {
                                Computer computer = assertCallback.get(j);
                                Assert.assertEquals(computer.errorMsg, computer.a, data[j]);
                            }
                            break;
                        case DELETE_ROWS_EVENT:
                            break;
                        }
                        sb.append("\nrows end ---------");
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

    public void test(Serializable a, String msg) {
        assertCallback.add(new Computer(a, msg));
    }

    class Computer {
        public Serializable a;
        public String errorMsg;

        public Computer(Serializable a, String errorMsg) {
            this.a = a;
            this.errorMsg = errorMsg;
        }
    }

}
