/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.tool;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * created by ziyang.lb
 **/
public class SQLLogParseTool {
    private static final String LINE_SUFFIX = "tddl version: 5.4.9-16300361";

    /**
     * 1. 日志文件解读：
     * <a href="https://aliyuque.antfin.com/coronadb/kb/nifd46">...</a>
     * <p>
     * 2. slow.log & physical_slow.log解读：
     * <a href="https://aliyuque.antfin.com/drds_doc/uwdcqb/ypzmtw#tddl-physical_slow.log">...</a>
     */
    public static void main(String[] args) throws IOException {
        parseSlowLog("");
        parsePhysicalSlowLog("");
        parseLongPendingTrans();
    }

    private static void parseSlowLog(String fileName) throws IOException {
        File file = new File("/Users/lubiao/Downloads/1204/" + fileName);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            String originStr = s;
            if (StringUtils.endsWith(s, LINE_SUFFIX)) {
                try {
                    // logTime
                    String logTime = StringUtils.substringBefore(s, "- [").trim();

                    // traceId
                    String traceId = StringUtils.substringAfterLast(s, "#");
                    traceId = StringUtils.substringBefore(traceId, ",");

                    // tranId
                    String tranId = StringUtils.substringBefore(traceId, "-");

                    // executeTime
                    s = StringUtils.substringBeforeLast(s, "#");
                    s = StringUtils.substringBeforeLast(s, "#");
                    String executeTime = StringUtils.substringAfterLast(s, "#");

                    if (Long.parseLong(executeTime) > 2000) {
                        System.out.println(logTime + "        " + executeTime + "     " + traceId);
                    }
                } catch (Throwable t) {
                    System.err.println(originStr);
                }
            }
        }
    }

    private static void parsePhysicalSlowLog(String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            String originStr = s;
            if (StringUtils.endsWith(s, LINE_SUFFIX)) {
                try {
                    String logTime = StringUtils.substringBefore(s, "- [").trim();
                    String traceId = StringUtils.substringAfterLast(s, "#");

                    String[] array = StringUtils.split(s, "#");
                    String total_time = array[array.length - 1 - 5];
                    String sql_execute_time = array[array.length - 1 - 4];
                    String get_connection_time = array[array.length - 1 - 3];

                    if (logTime.compareTo("2023-12-04 15:30:00") > 0) {
                        System.out.println(logTime + "        "
                            + total_time + "     "
                            + sql_execute_time + "       "
                            + get_connection_time + "     "
                            + traceId);
                    }
                } catch (Throwable t) {
                    System.err.println(originStr);
                }
            }
        }
    }

    private static void parseLongPendingTrans() throws IOException {
        HashMap<String, List<String>> map = new HashMap<>();

        Function<String, Boolean> filter = null;
        parseEachSql("/Users/lubiao/Downloads/1204/sql-2023-12-04-21.log", map, filter);
        parseEachSql("/Users/lubiao/Downloads/1204/sql-2023-12-04-22.log", map, filter);
        parseEachSql("/Users/lubiao/Downloads/1204/sql-2023-12-04-23.log", map, filter);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        map.forEach((key, value) -> {
            if (value.size() >= 2) {
                String first = value.get(0);
                String end = value.get(value.size() - 1);

                try {
                    Date firstTime = sdf.parse(first);
                    Date endTime = sdf.parse(end);
                    if (endTime.getTime() - firstTime.getTime() > 5 * 60 * 1000) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(key).append("   ");
                        sb.append(value.get(0)).append("     ");
                        sb.append(value.get(value.size() - 1)).append("     ");
                        sb.append((endTime.getTime() - firstTime.getTime()) / 1000);
                        System.out.println(sb);
                    }
                } catch (ParseException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private static void parseEachSql(String fileName, Map<String, List<String>> map, Function<String, Boolean> filter)
        throws IOException {
        File file = new File(fileName);
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        String s;
        while ((s = bufferedReader.readLine()) != null) {
            if (StringUtils.endsWith(s, LINE_SUFFIX)) {
                try {
                    // log time
                    String logTime = StringUtils.substringBefore(s, "- [").trim();

                    // traceId
                    String traceId = StringUtils.substringAfterLast(s, "#");
                    traceId = StringUtils.substringBefore(traceId, ",");

                    // tranId
                    String tranId = StringUtils.substringBefore(traceId, "-");

                    // filter and add
                    if (filter == null || filter.apply(s)) {
                        List<String> list = map.computeIfAbsent(tranId, k -> Lists.newArrayList());
                        list.add(logTime);
                    }
                } catch (Throwable t) {
                    System.err.println(s);
                    throw t;
                }
            }
        }
    }
}
