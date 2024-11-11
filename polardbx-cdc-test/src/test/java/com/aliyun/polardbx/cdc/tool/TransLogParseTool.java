/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.tool;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * created by ziyang.lb
 * 筛选出出现提交延迟的事务
 **/
public class TransLogParseTool {
    static String filePath = "";
    static long delayThreshold = 5000;

    public static void main(String[] args) throws IOException, ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
        File file = new File(filePath);
        FileReader fileReader = new FileReader(file);
        BufferedReader br = new BufferedReader(fileReader);
        Map<String, Pair<Date, Date>> map = new HashMap<>();
        String str;
        while ((str = br.readLine()) != null) {
            String timeStr = StringUtils.substringBefore(str, " - [");
            Date date = dateFormat.parse(timeStr);

            String trace = StringUtils.substringAfter(str, "[TDDL] [");
            trace = StringUtils.substringBefore(trace, "] [TSO]");

            if (str.contains("Prepared")) {
                map.put(trace, new MutablePair<>(date, new Date()));
            } else if (str.contains("Committed")) {
                Pair<Date, Date> pair = map.get(trace);
                pair.setValue(date);
                if (pair.getValue().getTime() - pair.getKey().getTime() < delayThreshold) {
                    map.remove(trace);
                }
            }
        }

        System.out.println(JSONObject.toJSONString(map.entrySet().stream().map(
            e -> Pair.of(
                e.getKey(),
                Pair.of(e.getValue().getKey().toString(), e.getValue().getValue().toString())))
            .collect(Collectors.toList()), true));
    }
}
