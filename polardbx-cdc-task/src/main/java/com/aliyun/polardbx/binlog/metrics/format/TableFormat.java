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

package com.aliyun.polardbx.binlog.metrics.format;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class TableFormat {

    private List<Column> columnList;
    private List<List<String>> rowsList = new ArrayList<>();
    private String title;

    public TableFormat(String title) {
        this.title = title;
    }

    public static void main(String[] args) {
        TableFormat tf = new TableFormat("test");
        tf.addColumn("tid", "rt", "event status", "pos", "delay");
        tf.addRow(26, 21, "complete", "master-bin.000067:262316#1610679074", 24958);
        tf.addRow(24, 0, "complete", "master-bin.000056:223844#1610679196", 24836);
        System.out.println(tf.print());
    }

    public void addColumn(String... col) {
        List<Column> columnList = new ArrayList<>();
        for (String c : col) {
            columnList.add(new Column(c));
        }
        for (int i = 0; i < columnList.size(); i++) {
            Column column = columnList.get(i);
            column.setIndex(i);
        }
        this.columnList = columnList;
    }

    public void addRow(List<String> row) {
        if (row.size() != columnList.size()) {
            throw new IllegalArgumentException(
                "row data size not equal column size : " + row.size() + "!=" + columnList.size());
        }
        rowsList.add(row);
    }

    public void addRow(Object... row) {
        List<String> rowList = new ArrayList<>();
        for (Object o : row) {
            rowList.add(String.valueOf(o));
        }
        this.addRow(rowList);
    }

    public String print() {

        for (Column column : columnList) {
            int len = column.getTitle().length();
            for (List<String> row : rowsList) {
                len = Math.max(len, row.get(column.getIndex()).length());
            }
            column.setColumnLen(len);
        }
        int totalLength = 1;
        for (Column column : columnList) {
            totalLength += column.getColumnLen() + 3;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n").
            append(title).
            append(":").
            append("\n").
            append(StringUtils.rightPad("", totalLength, "-")).
            append("\n");
        sb.append("|");
        for (Column column : columnList) {
            sb.append(" ")
                .append(StringUtils.rightPad(column.getTitle(), column.getColumnLen()))
                .append(" ")
                .append("|");
        }
        sb.append("\n");
        sb.append(StringUtils.rightPad("", totalLength, "-")).append("\n");
        for (List<String> row : rowsList) {
            sb.append("|");
            for (Column column : columnList) {
                sb.append(" ")
                    .append(StringUtils.rightPad(row.get(column.getIndex()), column.getColumnLen()))
                    .append(" ")
                    .append("|");
            }
            sb.append("\n");
        }
        sb.append(StringUtils.rightPad("", totalLength, "-")).append("\n");
        sb.append("\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return print();
    }
}
